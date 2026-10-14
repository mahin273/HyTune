package com.mahin.hytune.core.privilege

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class ShizukuPrivilegeBackendTest {

    private lateinit var mockRunner: MockShizukuShellRunner
    private lateinit var backend: ShizukuPrivilegeBackend

    private class MockShizukuShellRunner : ShizukuShellRunner {
        var shizukuAvailable: Boolean = true
        val executedCommands = mutableListOf<String>()
        val commandResponses = mutableMapOf<String, CommandResult>()
        var defaultResponse = CommandResult.success()

        override fun isShizukuAvailable(): Boolean = shizukuAvailable

        override suspend fun runCommand(command: String): CommandResult {
            executedCommands.add(command)
            return commandResponses[command] ?: defaultResponse
        }
    }

    @Before
    fun setUp() {
        mockRunner = MockShizukuShellRunner()
        backend = ShizukuPrivilegeBackend(shellRunner = mockRunner)
    }

    @Test
    fun tierAndCapabilities_areShizukuAndSelective() {
        assertEquals(PrivilegeTier.SHIZUKU, backend.tier)
        assertTrue(backend.hasCapability(Capability.SYSFS_READ))
        assertTrue(backend.hasCapability(Capability.APP_FREEZE))
        assertTrue(backend.hasCapability(Capability.DUMPSYS))
        assertTrue(backend.hasCapability(Capability.SETTINGS_WRITE))

        assertFalse(backend.hasCapability(Capability.SYSFS_WRITE))
        assertFalse(backend.hasCapability(Capability.PROCESS_CONTROL))
    }

    @Test
    fun writeFile_alwaysFailsWithSecurityException() = runTest {
        val result = backend.writeFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor", "performance")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
        assertTrue(mockRunner.executedCommands.isEmpty())
    }

    @Test
    fun readFile_success_executesCatCommand() = runTest {
        val path = "/proc/version"
        val expectedCmd = "cat '$path'"
        mockRunner.commandResponses[expectedCmd] = CommandResult.success(listOf("Linux version 5.10.0-android"))

        val result = backend.readFile(path)

        assertTrue(result.isSuccess)
        assertEquals("Linux version 5.10.0-android", result.getOrNull())
        assertEquals(listOf(expectedCmd), mockRunner.executedCommands)
    }

    @Test
    fun exec_appFreezeCommand_delegatesToRunner() = runTest {
        val cmd = "pm disable-user --user 0 com.bloatware.app"
        mockRunner.commandResponses[cmd] = CommandResult.success(listOf("Package com.bloatware.app new state: disabled-user"))

        val result = backend.exec(cmd)

        assertTrue(result.isSuccess)
        assertEquals(listOf("Package com.bloatware.app new state: disabled-user"), result.stdout)
        assertEquals(listOf(cmd), mockRunner.executedCommands)
    }

    @Test
    fun exists_successAndFailure() = runTest {
        val path = "/system/bin/app_process"
        val cmd = "[ -e '$path' ]"
        mockRunner.commandResponses[cmd] = CommandResult.success()

        assertTrue(backend.exists(path))

        val missingPath = "/system/bin/missing_binary"
        val missingCmd = "[ -e '$missingPath' ]"
        mockRunner.commandResponses[missingCmd] = CommandResult.failure(1)

        assertFalse(backend.exists(missingPath))
    }

    @Test
    fun listFiles_returnsFilteredOutput() = runTest {
        val path = "/system/bin"
        val cmd = "ls -1A '$path'"
        mockRunner.commandResponses[cmd] = CommandResult.success(listOf("sh", "pm", "am", ""))

        val result = backend.listFiles(path)

        assertTrue(result.isSuccess)
        assertEquals(listOf("sh", "pm", "am"), result.getOrNull())
    }
}
