package com.mahin.hytune.core.privilege

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class RootPrivilegeBackendTest {

    private lateinit var mockRunner: MockRootShellRunner
    private lateinit var backend: RootPrivilegeBackend

    private class MockRootShellRunner : RootShellRunner {
        var rootAvailable: Boolean = true
        val executedCommands = mutableListOf<String>()
        val commandResponses = mutableMapOf<String, CommandResult>()
        var defaultResponse = CommandResult.success()

        override fun isRootAvailable(): Boolean = rootAvailable

        override suspend fun runCommand(command: String): CommandResult {
            executedCommands.add(command)
            return commandResponses[command] ?: defaultResponse
        }
    }

    @Before
    fun setUp() {
        mockRunner = MockRootShellRunner()
        backend = RootPrivilegeBackend(shellRunner = mockRunner)
    }

    @Test
    fun tierAndCapabilities_areRootAndAlwaysGranted() {
        assertEquals(PrivilegeTier.ROOT, backend.tier)
        assertTrue(backend.hasCapability(Capability.SYSFS_READ))
        assertTrue(backend.hasCapability(Capability.SYSFS_WRITE))
        assertTrue(backend.hasCapability(Capability.APP_FREEZE))
        assertTrue(backend.hasCapability(Capability.DUMPSYS))
        assertTrue(backend.hasCapability(Capability.PROCESS_CONTROL))
        assertTrue(backend.hasCapability(Capability.SETTINGS_WRITE))
    }

    @Test
    fun readFile_success_executesCatWithEscapedPath() = runTest {
        val path = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq"
        val expectedCommand = "cat '$path'"
        mockRunner.commandResponses[expectedCommand] = CommandResult.success(listOf("1804800"))

        val result = backend.readFile(path)

        assertTrue(result.isSuccess)
        assertEquals("1804800", result.getOrNull())
        assertEquals(listOf(expectedCommand), mockRunner.executedCommands)
    }

    @Test
    fun readFile_failure_returnsIOException() = runTest {
        val path = "/sys/devices/system/cpu/cpu99/cpufreq/scaling_cur_freq"
        val expectedCommand = "cat '$path'"
        mockRunner.commandResponses[expectedCommand] = CommandResult.failure(1, listOf("No such file or directory"))

        val result = backend.readFile(path)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals("No such file or directory", result.exceptionOrNull()?.message)
    }

    @Test
    fun writeFile_success_executesPrintfWithEscapedArguments() = runTest {
        val path = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor"
        val content = "schedutil"
        val expectedCommand = "printf '%s' '$content' > '$path'"
        mockRunner.commandResponses[expectedCommand] = CommandResult.success()

        val result = backend.writeFile(path, content)

        assertTrue(result.isSuccess)
        assertEquals(listOf(expectedCommand), mockRunner.executedCommands)
    }

    @Test
    fun writeFile_failure_returnsIOException() = runTest {
        val path = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor"
        val content = "invalid_governor"
        val expectedCommand = "printf '%s' '$content' > '$path'"
        mockRunner.commandResponses[expectedCommand] = CommandResult.failure(1, listOf("Invalid argument"))

        val result = backend.writeFile(path, content)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals("Invalid argument", result.exceptionOrNull()?.message)
    }

    @Test
    fun writeFile_escapesSingleQuotesSafely() = runTest {
        val path = "/sys/devices/custom/test'node"
        val content = "it's 'fast'"
        val expectedPathEscaped = "'/sys/devices/custom/test'\\''node'"
        val expectedContentEscaped = "'it'\\''s '\\''fast'\\'''"
        val expectedCommand = "printf '%s' $expectedContentEscaped > $expectedPathEscaped"
        mockRunner.commandResponses[expectedCommand] = CommandResult.success()

        val result = backend.writeFile(path, content)

        assertTrue(result.isSuccess)
        assertEquals(listOf(expectedCommand), mockRunner.executedCommands)
    }

    @Test
    fun exists_returnsTrueWhenTestCommandSucceeds() = runTest {
        val path = "/sys/class/kgsl/kgsl-3d0"
        val expectedCommand = "[ -e '$path' ]"
        mockRunner.commandResponses[expectedCommand] = CommandResult.success()

        val exists = backend.exists(path)

        assertTrue(exists)
        assertEquals(listOf(expectedCommand), mockRunner.executedCommands)
    }

    @Test
    fun exists_returnsFalseWhenTestCommandFails() = runTest {
        val path = "/sys/class/kgsl/nonexistent"
        val expectedCommand = "[ -e '$path' ]"
        mockRunner.commandResponses[expectedCommand] = CommandResult.failure(1)

        val exists = backend.exists(path)

        assertFalse(exists)
        assertEquals(listOf(expectedCommand), mockRunner.executedCommands)
    }

    @Test
    fun listFiles_success_returnsParsedLines() = runTest {
        val path = "/sys/devices/system/cpu"
        val expectedCommand = "ls -1A '$path'"
        mockRunner.commandResponses[expectedCommand] = CommandResult.success(listOf("cpu0", "cpu1", "possible", ""))

        val result = backend.listFiles(path)

        assertTrue(result.isSuccess)
        assertEquals(listOf("cpu0", "cpu1", "possible"), result.getOrNull())
        assertEquals(listOf(expectedCommand), mockRunner.executedCommands)
    }

    @Test
    fun listFiles_failure_returnsIOException() = runTest {
        val path = "/sys/devices/missing_dir"
        val expectedCommand = "ls -1A '$path'"
        mockRunner.commandResponses[expectedCommand] = CommandResult.failure(1, listOf("No such file or directory"))

        val result = backend.listFiles(path)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
    }

    @Test
    fun exec_delegatesDirectlyToShellRunner() = runTest {
        val command = "dumpsys surfaceflinger --latency"
        mockRunner.commandResponses[command] = CommandResult.success(listOf("16666666", "100 200 300"))

        val result = backend.exec(command)

        assertTrue(result.isSuccess)
        assertEquals(listOf("16666666", "100 200 300"), result.stdout)
        assertEquals(listOf(command), mockRunner.executedCommands)
    }
}
