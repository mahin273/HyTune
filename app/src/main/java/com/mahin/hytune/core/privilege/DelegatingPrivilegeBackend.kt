package com.mahin.hytune.core.privilege

class DelegatingPrivilegeBackend(
    private val privilegeManager: PrivilegeManager
) : PrivilegeBackend {

    private val delegate: PrivilegeBackend
        get() = privilegeManager.activeBackend.value

    override val tier: PrivilegeTier
        get() = delegate.tier

    override fun hasCapability(capability: Capability): Boolean {
        return delegate.hasCapability(capability)
    }

    override suspend fun exec(command: String): CommandResult {
        return delegate.exec(command)
    }

    override suspend fun readFile(path: String): Result<String> {
        return delegate.readFile(path)
    }

    override suspend fun writeFile(path: String, content: String): Result<Unit> {
        return delegate.writeFile(path, content)
    }

    override suspend fun exists(path: String): Boolean {
        return delegate.exists(path)
    }

    override suspend fun listFiles(path: String): Result<List<String>> {
        return delegate.listFiles(path)
    }
}
