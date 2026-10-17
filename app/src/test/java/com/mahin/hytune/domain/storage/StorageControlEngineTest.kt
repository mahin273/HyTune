package com.mahin.hytune.domain.storage

import com.mahin.hytune.core.privilege.CommandResult
import com.mahin.hytune.core.privilege.FakePrivilegeBackend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StorageControlEngineTest {

    private lateinit var fakeBackend: FakePrivilegeBackend
    private lateinit var engine: StorageControlEngine
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        fakeBackend = FakePrivilegeBackend()
        engine = StorageControlEngine(
            privilegeBackend = fakeBackend,
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun getBlockDevices_discoversPhysicalStorage_andFiltersVirtualDevices() = runTest(testDispatcher) {
        // Setup /sys/block directory
        fakeBackend.setFile("/sys/block/sda/queue/scheduler", "none [mq-deadline] kyber bfq\n")
        fakeBackend.setFile("/sys/block/sda/queue/read_ahead_kb", "128\n")
        fakeBackend.setFile("/sys/block/sda/queue/rotational", "0\n")
        fakeBackend.setFile("/sys/block/sda/size", "250069680\n") // ~128 GB

        fakeBackend.setFile("/sys/block/mmcblk0/queue/scheduler", "[none] mq-deadline\n")
        fakeBackend.setFile("/sys/block/mmcblk0/queue/read_ahead_kb", "512\n")
        fakeBackend.setFile("/sys/block/mmcblk0/queue/rotational", "0\n")
        fakeBackend.setFile("/sys/block/mmcblk0/size", "62517248\n") // ~32 GB

        // Virtual devices to filter out
        fakeBackend.setFile("/sys/block/loop0/queue/scheduler", "none\n")
        fakeBackend.setFile("/sys/block/ram0/queue/scheduler", "none\n")
        fakeBackend.setFile("/sys/block/zram0/queue/scheduler", "none\n")

        val result = engine.getBlockDevices()
        assertTrue(result.isSuccess)

        val devices = result.getOrThrow()
        assertEquals(2, devices.size)

        val sda = devices.find { it.name == "sda" }
        assertNotNull(sda)
        assertEquals("mq-deadline", sda!!.currentScheduler)
        assertEquals(listOf("none", "mq-deadline", "kyber", "bfq"), sda.availableSchedulers)
        assertEquals(128, sda.readAheadKb)
        assertFalse(sda.isRotational)
        assertEquals(250069680L * 512L, sda.sizeBytes)

        val mmc = devices.find { it.name == "mmcblk0" }
        assertNotNull(mmc)
        assertEquals("none", mmc!!.currentScheduler)
        assertEquals(512, mmc.readAheadKb)
    }

    @Test
    fun getBlockDevices_returnsEmptyList_whenSysBlockDoesNotExist() = runTest(testDispatcher) {
        val result = engine.getBlockDevices()
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun setScheduler_writesToQueueScheduler_whenSchedulerIsAvailable() = runTest(testDispatcher) {
        fakeBackend.setFile("/sys/block/sda/queue/scheduler", "none [mq-deadline] kyber\n")

        val result = engine.setScheduler("sda", "none")
        assertTrue(result.isSuccess)
        assertEquals("none", fakeBackend.getFile("/sys/block/sda/queue/scheduler"))
    }

    @Test
    fun setScheduler_throwsException_whenSchedulerIsNotAvailable() = runTest(testDispatcher) {
        fakeBackend.setFile("/sys/block/sda/queue/scheduler", "none [mq-deadline]\n")

        val result = engine.setScheduler("sda", "bfq")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun setScheduler_throwsException_whenDeviceDoesNotExist() = runTest(testDispatcher) {
        val result = engine.setScheduler("sdz", "none")
        assertTrue(result.isFailure)
    }

    @Test
    fun setReadAhead_clampsBetween32And4096_andWritesToSysfs() = runTest(testDispatcher) {
        fakeBackend.setFile("/sys/block/sda/queue/read_ahead_kb", "128\n")

        engine.setReadAhead("sda", 8192)
        assertEquals("4096", fakeBackend.getFile("/sys/block/sda/queue/read_ahead_kb"))

        engine.setReadAhead("sda", 16)
        assertEquals("32", fakeBackend.getFile("/sys/block/sda/queue/read_ahead_kb"))

        engine.setReadAhead("sda", 512)
        assertEquals("512", fakeBackend.getFile("/sys/block/sda/queue/read_ahead_kb"))
    }

    @Test
    fun setGlobalReadAhead_updatesAllDiscoveredDevices() = runTest(testDispatcher) {
        fakeBackend.setFile("/sys/block/sda/queue/scheduler", "[mq-deadline]\n")
        fakeBackend.setFile("/sys/block/sda/queue/read_ahead_kb", "128\n")
        fakeBackend.setFile("/sys/block/sdb/queue/scheduler", "[none]\n")
        fakeBackend.setFile("/sys/block/sdb/queue/read_ahead_kb", "128\n")

        val result = engine.setGlobalReadAhead(1024)
        assertTrue(result.isSuccess)

        assertEquals("1024", fakeBackend.getFile("/sys/block/sda/queue/read_ahead_kb"))
        assertEquals("1024", fakeBackend.getFile("/sys/block/sdb/queue/read_ahead_kb"))
    }

    @Test
    fun runFsTrim_parsesTrimmedBytes_onSuccess() = runTest(testDispatcher) {
        fakeBackend.registerCommandHandler("fstrim") {
            CommandResult.success(listOf("/data: 1452679168 bytes trimmed"))
        }

        val result = engine.runFsTrim("/data")
        assertTrue(result.isSuccess)

        val trim = result.getOrThrow()
        assertTrue(trim.isSuccess)
        assertEquals("/data", trim.mountPoint)
        assertEquals(1452679168L, trim.trimmedBytes)
    }

    @Test
    fun runFsTrim_handlesFailureGracefully() = runTest(testDispatcher) {
        fakeBackend.registerCommandHandler("fstrim") {
            CommandResult.failure(1, listOf("fstrim: /data: FITRIM not supported"))
        }

        val result = engine.runFsTrim("/data")
        assertTrue(result.isSuccess)

        val trim = result.getOrThrow()
        assertFalse(trim.isSuccess)
        assertEquals(0L, trim.trimmedBytes)
        assertTrue(trim.rawOutput.contains("FITRIM not supported"))
    }
}
