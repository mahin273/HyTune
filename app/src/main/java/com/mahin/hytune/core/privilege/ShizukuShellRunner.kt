package com.mahin.hytune.core.privilege

interface ShizukuShellRunner {
    fun isShizukuAvailable(): Boolean

    suspend fun runCommand(command: String): CommandResult
}
