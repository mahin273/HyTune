package com.mahin.hytune.core.privilege

interface PrivilegeBackend {
    val tier: PrivilegeTier

    fun hasCapability(capability: Capability): Boolean

    suspend fun exec(command: String): CommandResult

    suspend fun readFile(path: String): Result<String>

    suspend fun writeFile(path: String, content: String): Result<Unit>

    suspend fun exists(path: String): Boolean

    suspend fun listFiles(path: String): Result<List<String>>
}
