package com.mahin.hytune.core.privilege

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class FakePrivilegeBackendTest {

    private lateinit var backend: FakePrivilegeBackend

    @Before
    fun setUp() {
        backend = FakePrivilegeBackend()
    }

    @Test
    fun defaultTierAndCapabilities_areRootAndFullAccess() {
        assertEquals(PrivilegeTier.ROOT, backend.tier)
        assertTrue(backend.hasCapability(Capability.SYSFS_READ))
        assertTrue(backend.hasCapability(Capability.SYSFS_WRITE))
        assertTrue(backend.hasCapability(Capability.APP_FREEZE))
        assertTrue(backend.hasCapability(Capability.DUMPSYS))
        assertTrue(backend.hasCapability(Capability.PROCESS_CONTROL))
        assertTrue(backend.hasCapability(Capability.SETTINGS_WRITE))
    }

    @Test
    fun readFile_seededContent_returnsSuccess() = runTest {
        val path = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq"
        backend.setFile(path, "1804800")

        val result = backend.readFile(path)

        assertTrue(result.isSuccess)
        assertEquals("1804800", result.getOrNull())
    }

    @Test
    fun readFile_nonExistentPath_returnsFailureNoSuchFile() = runTest {
        val result = backend.readFile("/sys/class/thermal/thermal_zone99/temp")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NoSuchFileException)
    }

    @Test
    fun readFile_missingSysfsReadCapability_returnsSecurityException() = runTest {
        backend.removeCapability(Capability.SYSFS_READ)
        backend.setFile("/sys/class/thermal/thermal_zone0/temp", "38000")

        val result = backend.readFile("/sys/class/thermal/thermal_zone0/temp")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
    }

    @Test
    fun writeFile_validPath_mutatesVirtualFilesystem() = runTest {
        val path = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor"

        val writeResult = backend.writeFile(path, "performance")

        assertTrue(writeResult.isSuccess)
        assertEquals("performance", backend.getFile(path))

        val readResult = backend.readFile(path)
        assertTrue(readResult.isSuccess)
        assertEquals("performance", readResult.getOrNull())
    }

    @Test
    fun writeFile_missingSysfsWriteCapability_returnsSecurityException() = runTest {
        backend.removeCapability(Capability.SYSFS_WRITE)
        val path = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor"

        val writeResult = backend.writeFile(path, "powersave")

        assertTrue(writeResult.isFailure)
        assertTrue(writeResult.exceptionOrNull() is SecurityException)
        assertNull(backend.getFile(path))
    }

    @Test
    fun writeFile_readOnlyPath_returnsIOException() = runTest {
        val path = "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq"
        backend.setFile(path, "2803200")
        backend.setReadOnly(path, true)

        val writeResult = backend.writeFile(path, "3000000")

        assertTrue(writeResult.isFailure)
        assertTrue(writeResult.exceptionOrNull() is IOException)
        assertEquals("2803200", backend.getFile(path))
    }

    @Test
    fun exists_recognizesFilesAndDirectoryHierarchies() = runTest {
        backend.setFile("/sys/devices/system/cpu/cpu0/online", "1")

        assertTrue(backend.exists("/sys/devices/system/cpu/cpu0/online"))
        assertTrue(backend.exists("/sys/devices/system/cpu/cpu0"))
        assertTrue(backend.exists("/sys/devices/system/cpu"))
        assertFalse(backend.exists("/sys/devices/system/gpu"))
    }

    @Test
    fun listFiles_returnsDirectChildrenSorted() = runTest {
        backend.setFile("/sys/devices/system/cpu/cpu0/online", "1")
        backend.setFile("/sys/devices/system/cpu/cpu1/online", "1")
        backend.setFile("/sys/devices/system/cpu/cpu2/online", "1")
        backend.setFile("/sys/devices/system/cpu/possible", "0-7")

        val result = backend.listFiles("/sys/devices/system/cpu")

        assertTrue(result.isSuccess)
        val children = result.getOrNull()
        assertNotNull(children)
        assertEquals(listOf("cpu0", "cpu1", "cpu2", "possible"), children)
    }

    @Test
    fun exec_catCommand_returnsFileContent() = runTest {
        val path = "/sys/block/zram0/disksize"
        backend.setFile(path, "4294967296")

        val result = backend.exec("cat $path")

        assertTrue(result.isSuccess)
        assertEquals(0, result.exitCode)
        assertEquals(listOf("4294967296"), result.stdout)
        assertEquals("4294967296", result.output)
    }

    @Test
    fun exec_echoCommand_writesToVirtualFilesystem() = runTest {
        val path = "/sys/block/zram0/comp_algorithm"

        val result = backend.exec("echo lz4 > $path")

        assertTrue(result.isSuccess)
        assertEquals(0, result.exitCode)
        assertEquals("lz4", backend.getFile(path))
    }

    @Test
    fun exec_whichSu_differentiatesRootAndNonRoot() = runTest {
        backend.tier = PrivilegeTier.ROOT
        val rootResult = backend.exec("which su")
        assertTrue(rootResult.isSuccess)
        assertEquals("/system/bin/su", rootResult.output)

        backend.tier = PrivilegeTier.SHIZUKU
        val shizukuResult = backend.exec("which su")
        assertFalse(shizukuResult.isSuccess)
        assertEquals(1, shizukuResult.exitCode)

        backend.tier = PrivilegeTier.NONE
        val noneResult = backend.exec("which su")
        assertFalse(noneResult.isSuccess)
        assertEquals(1, noneResult.exitCode)
    }

    @Test
    fun exec_customCommandHandler_executesAndLogsHistory() = runTest {
        backend.registerCommandHandler("pm list packages") {
            CommandResult.success(listOf("package:com.android.chrome", "package:com.mahin.hytune"))
        }

        val result = backend.exec("pm list packages")

        assertTrue(result.isSuccess)
        assertEquals(2, result.stdout.size)
        assertTrue(result.stdout.contains("package:com.mahin.hytune"))

        val history = backend.getExecutedCommands()
        assertEquals(1, history.size)
        assertEquals("pm list packages", history.first())
    }

    @Test
    fun errorInjection_simulatesReadAndWriteFailures() = runTest {
        val path = "/sys/devices/system/cpu/cpufreq/policy0/scaling_governor"
        backend.setFile(path, "schedutil")

        backend.failNextReadWith = IOException("Bus I/O error")
        val failedRead = backend.readFile(path)
        assertTrue(failedRead.isFailure)
        assertEquals("Bus I/O error", failedRead.exceptionOrNull()?.message)

        val recoveredRead = backend.readFile(path)
        assertTrue(recoveredRead.isSuccess)
        assertEquals("schedutil", recoveredRead.getOrNull())

        backend.failNextWriteWith = IOException("Driver rejected value")
        val failedWrite = backend.writeFile(path, "invalid_governor")
        assertTrue(failedWrite.isFailure)
        assertEquals("Driver rejected value", failedWrite.exceptionOrNull()?.message)

        val successfulWrite = backend.writeFile(path, "powersave")
        assertTrue(successfulWrite.isSuccess)
        assertEquals("powersave", backend.getFile(path))
    }
}
