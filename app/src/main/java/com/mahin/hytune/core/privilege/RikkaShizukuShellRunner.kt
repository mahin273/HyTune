package com.mahin.hytune.core.privilege

import android.content.pm.PackageManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

class RikkaShizukuShellRunner(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ShizukuShellRunner {

    override fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    override suspend fun runCommand(command: String): CommandResult = withContext(ioDispatcher) {
        try {
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply {
                isAccessible = true
            }

            val process = newProcessMethod.invoke(
                null,
                arrayOf("sh", "-c", command),
                null,
                null
            ) as java.lang.Process

            val stdout = process.inputStream.bufferedReader().useLines { it.toList() }
            val stderr = process.errorStream.bufferedReader().useLines { it.toList() }
            val exitCode = process.waitFor()
            CommandResult(
                exitCode = exitCode,
                stdout = stdout,
                stderr = stderr
            )
        } catch (e: Throwable) {
            CommandResult.failure(exitCode = 1, errorLines = listOf(e.message ?: "Shizuku command execution failed"))
        }
    }
}
