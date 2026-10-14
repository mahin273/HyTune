package com.mahin.hytune.core.privilege

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LibsuShellRunner(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : RootShellRunner {

    init {
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setTimeout(10)
        )
    }

    override fun isRootAvailable(): Boolean {
        return Shell.isAppGrantedRoot() == true || Shell.getShell().isRoot
    }

    override suspend fun runCommand(command: String): CommandResult = withContext(ioDispatcher) {
        val result = Shell.cmd(command).exec()
        CommandResult(
            exitCode = result.code,
            stdout = result.out,
            stderr = result.err
        )
    }
}
