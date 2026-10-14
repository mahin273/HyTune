package com.mahin.hytune.core.privilege

import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class FakePrivilegeBackend(
    initialTier: PrivilegeTier = PrivilegeTier.ROOT,
    initialCapabilities: Set<Capability> = Capability.entries.toSet()
) : PrivilegeBackend {

    override var tier: PrivilegeTier = initialTier

    private val capabilities: MutableSet<Capability> = initialCapabilities.toMutableSet()
    private val virtualFilesystem = ConcurrentHashMap<String, String>()
    private val readOnlyPaths = ConcurrentHashMap.newKeySet<String>()
    private val commandHistory = CopyOnWriteArrayList<String>()
    private val customCommandHandlers = ConcurrentHashMap<String, (String) -> CommandResult>()

    var failNextReadWith: Throwable? = null
    var failNextWriteWith: Throwable? = null

    override fun hasCapability(capability: Capability): Boolean {
        return capabilities.contains(capability)
    }

    fun setCapabilities(vararg caps: Capability) {
        capabilities.clear()
        capabilities.addAll(caps)
    }

    fun addCapability(capability: Capability) {
        capabilities.add(capability)
    }

    fun removeCapability(capability: Capability) {
        capabilities.remove(capability)
    }

    fun setFile(path: String, content: String) {
        val normalized = normalizePath(path)
        virtualFilesystem[normalized] = content
    }

    fun getFile(path: String): String? {
        val normalized = normalizePath(path)
        return virtualFilesystem[normalized]
    }

    fun setReadOnly(path: String, readOnly: Boolean = true) {
        val normalized = normalizePath(path)
        if (readOnly) {
            readOnlyPaths.add(normalized)
        } else {
            readOnlyPaths.remove(normalized)
        }
    }

    fun registerCommandHandler(prefix: String, handler: (String) -> CommandResult) {
        customCommandHandlers[prefix] = handler
    }

    fun getExecutedCommands(): List<String> {
        return commandHistory.toList()
    }

    fun clearHistory() {
        commandHistory.clear()
    }

    fun clearFilesystem() {
        virtualFilesystem.clear()
        readOnlyPaths.clear()
    }

    override suspend fun readFile(path: String): Result<String> {
        if (!hasCapability(Capability.SYSFS_READ)) {
            return Result.failure(SecurityException("Missing capability SYSFS_READ"))
        }

        failNextReadWith?.let { error ->
            failNextReadWith = null
            return Result.failure(error)
        }

        val normalized = normalizePath(path)
        val content = virtualFilesystem[normalized]
            ?: return Result.failure(NoSuchFileException(file = java.io.File(path)))

        return Result.success(content)
    }

    override suspend fun writeFile(path: String, content: String): Result<Unit> {
        if (!hasCapability(Capability.SYSFS_WRITE)) {
            return Result.failure(SecurityException("Missing capability SYSFS_WRITE"))
        }

        failNextWriteWith?.let { error ->
            failNextWriteWith = null
            return Result.failure(error)
        }

        val normalized = normalizePath(path)
        if (readOnlyPaths.contains(normalized)) {
            return Result.failure(IOException("Path is read-only: $path"))
        }

        virtualFilesystem[normalized] = content
        return Result.success(Unit)
    }

    override suspend fun exists(path: String): Boolean {
        val normalized = normalizePath(path)
        if (virtualFilesystem.containsKey(normalized)) {
            return true
        }
        val prefix = "$normalized/"
        return virtualFilesystem.keys.any { it.startsWith(prefix) }
    }

    override suspend fun listFiles(path: String): Result<List<String>> {
        if (!hasCapability(Capability.SYSFS_READ)) {
            return Result.failure(SecurityException("Missing capability SYSFS_READ"))
        }

        val normalized = normalizePath(path)
        val prefix = if (normalized.endsWith("/")) normalized else "$normalized/"

        val entries = mutableSetOf<String>()
        for (key in virtualFilesystem.keys) {
            if (key.startsWith(prefix) && key != prefix) {
                val remainder = key.substring(prefix.length)
                val directChild = remainder.substringBefore('/')
                if (directChild.isNotEmpty()) {
                    entries.add(directChild)
                }
            }
        }

        return Result.success(entries.sorted())
    }

    override suspend fun exec(command: String): CommandResult {
        commandHistory.add(command)

        for ((prefix, handler) in customCommandHandlers) {
            if (command.startsWith(prefix)) {
                return handler(command)
            }
        }

        val trimmed = command.trim()
        if (trimmed == "which su") {
            return if (tier == PrivilegeTier.ROOT) {
                CommandResult.success(listOf("/system/bin/su"))
            } else {
                CommandResult.failure(exitCode = 1, errorLines = listOf("su not found"))
            }
        }

        if (trimmed.startsWith("cat ")) {
            val targetPath = trimmed.substringAfter("cat ").trim()
            val readResult = readFile(targetPath)
            return if (readResult.isSuccess) {
                val content = readResult.getOrThrow()
                CommandResult.success(content.lines())
            } else {
                CommandResult.failure(exitCode = 1, errorLines = listOf(readResult.exceptionOrNull()?.message ?: "Read error"))
            }
        }

        if (trimmed.startsWith("echo ") && trimmed.contains(">")) {
            val parts = trimmed.substringAfter("echo ").split(">", limit = 2)
            if (parts.size == 2) {
                val rawValue = parts[0].trim().removeSurrounding("\"", "\"").removeSurrounding("'", "'")
                val targetPath = parts[1].trim()
                val writeResult = writeFile(targetPath, rawValue)
                return if (writeResult.isSuccess) {
                    CommandResult.success()
                } else {
                    CommandResult.failure(exitCode = 1, errorLines = listOf(writeResult.exceptionOrNull()?.message ?: "Write error"))
                }
            }
        }

        return CommandResult.success()
    }

    private fun normalizePath(path: String): String {
        val parts = path.split("/").filter { it.isNotEmpty() && it != "." }
        val stack = mutableListOf<String>()
        for (part in parts) {
            if (part == "..") {
                if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
            } else {
                stack.add(part)
            }
        }
        return "/" + stack.joinToString("/")
    }
}
