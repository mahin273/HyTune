package com.mahin.hytune.core.privilege

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class UnprivilegedBackend(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : PrivilegeBackend {

    override val tier: PrivilegeTier = PrivilegeTier.NONE

    override fun hasCapability(capability: Capability): Boolean {
        return capability == Capability.SYSFS_READ
    }

    override suspend fun exec(command: String): CommandResult = withContext(ioDispatcher) {
        try {
            val process = ProcessBuilder("sh", "-c", command).start()
            val stdout = process.inputStream.bufferedReader().useLines { it.toList() }
            val stderr = process.errorStream.bufferedReader().useLines { it.toList() }
            val exitCode = process.waitFor()
            CommandResult(exitCode = exitCode, stdout = stdout, stderr = stderr)
        } catch (e: Throwable) {
            CommandResult.failure(exitCode = 1, errorLines = listOf(e.message ?: "Command execution failed"))
        }
    }

    override suspend fun readFile(path: String): Result<String> = withContext(ioDispatcher) {
        try {
            val file = File(path)
            if (!file.exists()) {
                Result.failure(NoSuchFileException(file))
            } else {
                Result.success(file.readText())
            }
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    override suspend fun writeFile(path: String, content: String): Result<Unit> {
        return Result.failure(SecurityException("SYSFS_WRITE is not available without ROOT privilege tier"))
    }

    override suspend fun exists(path: String): Boolean = withContext(ioDispatcher) {
        File(path).exists()
    }

    override suspend fun listFiles(path: String): Result<List<String>> = withContext(ioDispatcher) {
        val file = File(path)
        val list = file.list()
        if (list != null) {
            Result.success(list.toList().sorted())
        } else {
            Result.failure(IOException("Cannot list directory or directory not found: $path"))
        }
    }
}
