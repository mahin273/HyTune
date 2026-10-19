package com.mahin.hytune.domain.script.engine

import com.mahin.hytune.core.privilege.CommandResult
import com.mahin.hytune.core.privilege.FakePrivilegeBackend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScriptExecutionEngineTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeBackend: FakePrivilegeBackend
    private lateinit var engine: ScriptExecutionEngine

    @Before
    fun setUp() {
        fakeBackend = FakePrivilegeBackend()
        engine = ScriptExecutionEngine(
            privilegeBackend = fakeBackend,
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun executeScript_normalCommand_succeedsAndRecordsMetrics() = runTest(testDispatcher) {
        fakeBackend.registerCommandHandler("echo 'hello world'") {
            CommandResult.success(listOf("hello world"))
        }

        val result = engine.executeScript("echo 'hello world'")
        assertTrue(result.isSuccess)

        val data = result.getOrThrow()
        assertEquals(0, data.exitCode)
        assertEquals("hello world", data.output)
        assertEquals("", data.errorOutput)
        assertFalse(data.isTimedOut)
        assertTrue(data.isSuccess)
    }

    @Test
    fun executeScript_dangerousCommands_blockedWithSecurityException() = runTest(testDispatcher) {
        val dangerous = listOf(
            "rm -rf /",
            "rm -rf /data",
            "rm -rf /system",
            "rm -fr /*",
            "dd if=/dev/zero of=/dev/block/bootdevice",
            "mkfs.ext4 /dev/block/sda",
            "fdisk /dev/block/sda"
        )

        for (cmd in dangerous) {
            val result = engine.executeScript(cmd)
            assertTrue("Expected $cmd to fail", result.isFailure)
            assertTrue(
                "Expected SecurityException for $cmd",
                result.exceptionOrNull() is SecurityException
            )
        }
    }

    @Test
    fun executeScript_emptyCommand_failsWithIllegalArgumentException() = runTest(testDispatcher) {
        val result = engine.executeScript("   ")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun executeScript_nonZeroExitCode_capturedCorrectly() = runTest(testDispatcher) {
        fakeBackend.registerCommandHandler("foobar") {
            CommandResult.failure(exitCode = 127, errorLines = listOf("command not found: foobar"))
        }

        val result = engine.executeScript("foobar")
        assertTrue(result.isSuccess)

        val data = result.getOrThrow()
        assertEquals(127, data.exitCode)
        assertEquals("command not found: foobar", data.errorOutput)
        assertFalse(data.isSuccess)
    }
}
