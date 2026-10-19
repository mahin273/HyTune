package com.mahin.hytune.core.privilege

interface RootShellRunner {
    fun isRootAvailable(): Boolean

    suspend fun runCommand(command: String): CommandResult

    suspend fun requestRoot(): Boolean = isRootAvailable()
}
