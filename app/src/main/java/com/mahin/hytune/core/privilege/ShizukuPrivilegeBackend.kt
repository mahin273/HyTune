package com.mahin.hytune.core.privilege

import java.io.IOException

class ShizukuPrivilegeBackend(
    private val shellRunner: ShizukuShellRunner = RikkaShizukuShellRunner()
) : PrivilegeBackend {

    override val tier: PrivilegeTier = PrivilegeTier.SHIZUKU

    private val supportedCapabilities = setOf(
        Capability.SYSFS_READ,
        Capability.APP_FREEZE,
        Capability.DUMPSYS,
        Capability.SETTINGS_WRITE
    )

    override fun hasCapability(capability: Capability): Boolean {
        return supportedCapabilities.contains(capability)
    }

    override suspend fun exec(command: String): CommandResult {
        return shellRunner.runCommand(command)
    }

    override suspend fun readFile(path: String): Result<String> {
        val cmd = "cat ${escapeShellArg(path)}"
        val result = shellRunner.runCommand(cmd)
        return if (result.isSuccess) {
            Result.success(result.stdout.joinToString("\n"))
        } else {
            val errorMsg = if (result.stderr.isNotEmpty()) result.errorOutput else "Failed to read file: $path"
            Result.failure(IOException(errorMsg))
        }
    }

    override suspend fun writeFile(path: String, content: String): Result<Unit> {
        return Result.failure(SecurityException("SYSFS_WRITE capability requires ROOT privilege tier"))
    }

    override suspend fun exists(path: String): Boolean {
        val cmd = "[ -e ${escapeShellArg(path)} ]"
        val result = shellRunner.runCommand(cmd)
        return result.isSuccess
    }

    override suspend fun listFiles(path: String): Result<List<String>> {
        val cmd = "ls -1A ${escapeShellArg(path)}"
        val result = shellRunner.runCommand(cmd)
        return if (result.isSuccess) {
            val lines = result.stdout.filter { it.isNotBlank() }
            Result.success(lines)
        } else {
            val errorMsg = if (result.stderr.isNotEmpty()) result.errorOutput else "Failed to list directory: $path"
            Result.failure(IOException(errorMsg))
        }
    }

    private fun escapeShellArg(arg: String): String {
        return "'" + arg.replace("'", "'\\''") + "'"
    }
}
