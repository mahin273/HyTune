package com.mahin.hytune.domain.sysfs.engine

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
class SysfsBrowserEngineTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeBackend: FakePrivilegeBackend
    private lateinit var engine: SysfsBrowserEngine

    @Before
    fun setUp() {
        fakeBackend = FakePrivilegeBackend()
        engine = SysfsBrowserEngine(
            privilegeBackend = fakeBackend,
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun isPathAllowed_validAndInvalidPaths() {
        assertTrue(engine.isPathAllowed("/sys"))
        assertTrue(engine.isPathAllowed("/sys/devices/system/cpu"))
        assertTrue(engine.isPathAllowed("/proc"))
        assertTrue(engine.isPathAllowed("/proc/sys/vm"))

        assertFalse(engine.isPathAllowed("/data"))
        assertFalse(engine.isPathAllowed("/system"))
        assertFalse(engine.isPathAllowed("/etc"))
        assertFalse(engine.isPathAllowed("/"))
    }

    @Test
    fun listDirectory_parsesLsOutputIntoSysfsNodes() = runTest(testDispatcher) {
        val lsLines = listOf(
            "total 0",
            "drwxr-xr-x  3 root root    0 Jan  1 00:00 .",
            "drwxr-xr-x 12 root root    0 Jan  1 00:00 ..",
            "drwxr-xr-x  4 root root    0 Jan  1 00:00 cpu0",
            "-rw-r--r--  1 root root 4096 Jan  1 00:00 scaling_governor",
            "-r--r--r--  1 root root 4096 Jan  1 00:00 scaling_cur_freq"
        )

        fakeBackend.registerCommandHandler("ls -la \"/sys/devices/system/cpu\"") {
            CommandResult.success(lsLines)
        }

        val result = engine.listDirectory("/sys/devices/system/cpu")
        assertTrue(result.isSuccess)

        val nodes = result.getOrThrow()
        assertEquals(3, nodes.size)

        // Directories first: cpu0
        val dirNode = nodes[0]
        assertEquals("cpu0", dirNode.name)
        assertTrue(dirNode.isDirectory)
        assertEquals("/sys/devices/system/cpu/cpu0", dirNode.path)

        // File nodes
        val freqNode = nodes.find { it.name == "scaling_cur_freq" }
        assertEquals(true, freqNode?.isReadable)
        assertEquals(false, freqNode?.isWritable)

        val govNode = nodes.find { it.name == "scaling_governor" }
        assertEquals(true, govNode?.isReadable)
        assertEquals(true, govNode?.isWritable)
    }

    @Test
    fun listDirectory_disallowedPath_fails() = runTest(testDispatcher) {
        val result = engine.listDirectory("/data/local/tmp")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun readNode_callsHeadAndReturnsContent() = runTest(testDispatcher) {
        fakeBackend.registerCommandHandler("head -c 65536 \"/sys/devices/system/cpu/cpu0/scaling_cur_freq\"") {
            CommandResult.success(listOf("1804800"))
        }

        val result = engine.readNode("/sys/devices/system/cpu/cpu0/scaling_cur_freq")
        assertTrue(result.isSuccess)
        assertEquals("1804800", result.getOrThrow())
    }

    @Test
    fun writeNode_escapesAndWritesValue() = runTest(testDispatcher) {
        fakeBackend.registerCommandHandler("echo -n 'schedutil' > \"/sys/devices/system/cpu/cpu0/scaling_governor\"") {
            CommandResult.success()
        }

        val result = engine.writeNode("/sys/devices/system/cpu/cpu0/scaling_governor", "schedutil")
        assertTrue(result.isSuccess)
        val history = fakeBackend.getExecutedCommands()
        assertTrue(history.contains("echo -n 'schedutil' > \"/sys/devices/system/cpu/cpu0/scaling_governor\""))
    }

    @Test
    fun writeNode_protectedSubstrings_blocked() = runTest(testDispatcher) {
        val blocked = engine.writeNode("/proc/sysrq-trigger", "c")
        assertTrue(blocked.isFailure)
        assertTrue(blocked.exceptionOrNull() is SecurityException)
    }
}
