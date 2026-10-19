package com.mahin.hytune.domain.sysfs.engine

import com.mahin.hytune.core.privilege.PrivilegeBackend
import com.mahin.hytune.domain.sysfs.model.SysfsNode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SysfsBrowserEngine @Inject constructor(
    private val privilegeBackend: PrivilegeBackend,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    companion object {
        const val MAX_READ_BYTES = 65536
        private val ALLOWED_ROOT_PREFIXES = listOf("/sys", "/proc")
        private val FORBIDDEN_WRITE_SUBSTRINGS = listOf(
            "/dev/block",
            "/dev/kmsg",
            "/dev/null",
            "/proc/kmsg",
            "/proc/sysrq-trigger"
        )
    }

    fun isPathAllowed(path: String): Boolean {
        val normalized = normalizePath(path)
        return ALLOWED_ROOT_PREFIXES.any { prefix ->
            normalized == prefix || normalized.startsWith("$prefix/")
        }
    }

    private fun normalizePath(path: String): String {
        return path.trim().replace(Regex("/+"), "/")
    }

    suspend fun listDirectory(dirPath: String): Result<List<SysfsNode>> = withContext(ioDispatcher) {
        val normalized = normalizePath(dirPath)
        if (!isPathAllowed(normalized)) {
            return@withContext Result.failure(
                IllegalArgumentException("Browsing is restricted to /sys and /proc directories")
            )
        }

        val cmd = "ls -la \"$normalized\""
        val result = privilegeBackend.exec(cmd)
        if (!result.isSuccess) {
            return@withContext Result.failure(
                IOException(result.errorOutput.ifEmpty { "Failed to list directory: $normalized" })
            )
        }

        val nodes = mutableListOf<SysfsNode>()
        val lines = result.output.lines()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("total")) continue

            val parts = trimmed.split(Regex("\\s+"))
            if (parts.size < 9) continue

            val permissions = parts[0]
            val name = parts.subList(8, parts.size).joinToString(" ")

            if (name == "." || name == "..") continue

            val cleanName = if (name.contains(" -> ")) name.substringBefore(" -> ") else name
            val isDir = permissions.startsWith("d")
            val isWritable = permissions.contains("w")
            val isReadable = permissions.contains("r")
            val nodePath = if (normalized == "/") "/$cleanName" else "$normalized/$cleanName"

            nodes.add(
                SysfsNode(
                    path = nodePath,
                    name = cleanName,
                    isDirectory = isDir,
                    isReadable = isReadable,
                    isWritable = isWritable
                )
            )
        }

        // Sort: directories first, then alphabetical
        val sorted = nodes.sortedWith(
            compareBy<SysfsNode> { !it.isDirectory }.thenBy { it.name.lowercase() }
        )
        Result.success(sorted)
    }

    suspend fun readNode(path: String, maxBytes: Int = MAX_READ_BYTES): Result<String> = withContext(ioDispatcher) {
        val normalized = normalizePath(path)
        if (!isPathAllowed(normalized)) {
            return@withContext Result.failure(
                IllegalArgumentException("Access is restricted to /sys and /proc paths")
            )
        }

        val cmd = "head -c $maxBytes \"$normalized\""
        val result = privilegeBackend.exec(cmd)
        if (result.isSuccess) {
            Result.success(result.output.trim())
        } else {
            val catFallback = privilegeBackend.exec("cat \"$normalized\"")
            if (catFallback.isSuccess) {
                Result.success(catFallback.output.trim())
            } else {
                Result.failure(
                    IOException(result.errorOutput.ifEmpty { "Failed to read sysfs node: $normalized" })
                )
            }
        }
    }

    suspend fun writeNode(path: String, value: String): Result<Unit> = withContext(ioDispatcher) {
        val normalized = normalizePath(path)
        if (!isPathAllowed(normalized)) {
            return@withContext Result.failure(
                IllegalArgumentException("Modification is restricted to /sys and /proc paths")
            )
        }

        if (FORBIDDEN_WRITE_SUBSTRINGS.any { normalized.contains(it) }) {
            return@withContext Result.failure(
                SecurityException("Writing to protected critical kernel path is forbidden: $normalized")
            )
        }

        // Escape single quotes in value to prevent shell injection
        val escapedValue = value.replace("'", "'\\''")
        val cmd = "echo -n '$escapedValue' > \"$normalized\""

        val result = privilegeBackend.exec(cmd)
        if (result.isSuccess) {
            Result.success(Unit)
        } else {
            Result.failure(
                IOException(result.errorOutput.ifEmpty { "Failed to write value into $normalized" })
            )
        }
    }
}
