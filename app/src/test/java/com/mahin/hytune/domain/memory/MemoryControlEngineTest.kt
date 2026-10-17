package com.mahin.hytune.domain.memory

import com.mahin.hytune.core.privilege.CommandResult
import com.mahin.hytune.core.privilege.FakePrivilegeBackend
import com.mahin.hytune.domain.memory.model.DropCacheType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MemoryControlEngineTest {

    private lateinit var fakeBackend: FakePrivilegeBackend
    private lateinit var engine: MemoryControlEngine
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        fakeBackend = FakePrivilegeBackend()
        engine = MemoryControlEngine(
            privilegeBackend = fakeBackend,
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun getVmTunables_returnsParsedValues_whenSysfsFilesExist() = runTest(testDispatcher) {
        fakeBackend.setFile("/proc/sys/vm/swappiness", "100\n")
        fakeBackend.setFile("/proc/sys/vm/vfs_cache_pressure", "120\n")
        fakeBackend.setFile("/proc/sys/vm/dirty_ratio", "30\n")
        fakeBackend.setFile("/proc/sys/vm/dirty_background_ratio", "15\n")
        fakeBackend.setFile("/proc/sys/vm/extra_free_kbytes", "24576\n")

        val result = engine.getVmTunables()
        assertTrue(result.isSuccess)

        val tunables = result.getOrThrow()
        assertEquals(100, tunables.swappiness)
        assertEquals(120, tunables.vfsCachePressure)
        assertEquals(30, tunables.dirtyRatio)
        assertEquals(15, tunables.dirtyBackgroundRatio)
        assertEquals(24576L, tunables.extraFreeKbytes)
    }

    @Test
    fun getVmTunables_usesDefaults_whenFilesMissing() = runTest(testDispatcher) {
        val result = engine.getVmTunables()
        assertTrue(result.isSuccess)

        val tunables = result.getOrThrow()
        assertEquals(60, tunables.swappiness)
        assertEquals(100, tunables.vfsCachePressure)
        assertEquals(20, tunables.dirtyRatio)
        assertEquals(10, tunables.dirtyBackgroundRatio)
        assertNull(tunables.extraFreeKbytes)
    }

    @Test
    fun setSwappiness_clampsValueBetween0And200() = runTest(testDispatcher) {
        engine.setSwappiness(250)
        assertEquals("200", fakeBackend.getFile("/proc/sys/vm/swappiness"))

        engine.setSwappiness(-20)
        assertEquals("0", fakeBackend.getFile("/proc/sys/vm/swappiness"))

        engine.setSwappiness(80)
        assertEquals("80", fakeBackend.getFile("/proc/sys/vm/swappiness"))
    }

    @Test
    fun setVfsCachePressure_clampsValueBetween0And500() = runTest(testDispatcher) {
        engine.setVfsCachePressure(600)
        assertEquals("500", fakeBackend.getFile("/proc/sys/vm/vfs_cache_pressure"))

        engine.setVfsCachePressure(-10)
        assertEquals("0", fakeBackend.getFile("/proc/sys/vm/vfs_cache_pressure"))

        engine.setVfsCachePressure(150)
        assertEquals("150", fakeBackend.getFile("/proc/sys/vm/vfs_cache_pressure"))
    }

    @Test
    fun setDirtyRatios_clampsBothValues() = runTest(testDispatcher) {
        engine.setDirtyRatios(dirtyRatio = 20, dirtyBackgroundRatio = 35)
        // dirtyBackgroundRatio clamped to dirtyRatio (20)
        assertEquals("20", fakeBackend.getFile("/proc/sys/vm/dirty_background_ratio"))
        assertEquals("20", fakeBackend.getFile("/proc/sys/vm/dirty_ratio"))
    }

    @Test
    fun getZramInfo_parsesMmStatAndBracketedAlgorithms() = runTest(testDispatcher) {
        fakeBackend.setFile("/sys/block/zram0/disksize", "4294967296\n")
        fakeBackend.setFile("/sys/block/zram0/comp_algorithm", "lzo [lz4] zstd\n")
        fakeBackend.setFile("/sys/block/zram0/max_comp_streams", "8\n")
        // mm_stat: orig_data_size compr_data_size mem_used_total ...
        fakeBackend.setFile("/sys/block/zram0/mm_stat", " 356802560  118934186  133038080        0  182390784      12000          0\n")
        fakeBackend.setFile("/proc/swaps", "Filename\tType\tSize\tUsed\tPriority\n/dev/block/zram0\tpartition\t4194300\t116144\t32758\n")

        val result = engine.getZramInfo()
        assertTrue(result.isSuccess)

        val info = result.getOrThrow()
        assertNotNull(info)
        assertEquals(true, info!!.enabled)
        assertEquals(4294967296L, info.disksizeBytes)
        assertEquals("lz4", info.activeAlgorithm)
        assertEquals(listOf("lzo", "lz4", "zstd"), info.availableAlgorithms)
        assertEquals(356802560L, info.origSizeBytes)
        assertEquals(118934186L, info.comprSizeBytes)
        assertEquals(133038080L, info.memUsedTotalBytes)
        assertEquals(8, info.maxCompStreams)
        assertTrue(info.compressionRatio > 2.9f)
        assertTrue(info.spaceSavingsPercent >= 66)
    }

    @Test
    fun getZramInfo_fallsBackToIndividualStatFiles_whenMmStatMissing() = runTest(testDispatcher) {
        fakeBackend.setFile("/sys/block/zram0/disksize", "2147483648\n")
        fakeBackend.setFile("/sys/block/zram0/comp_algorithm", "[zstd] lz4\n")
        fakeBackend.setFile("/sys/block/zram0/orig_data_size", "200000000\n")
        fakeBackend.setFile("/sys/block/zram0/compr_data_size", "80000000\n")
        fakeBackend.setFile("/sys/block/zram0/mem_used_total", "85000000\n")
        fakeBackend.setFile("/proc/swaps", "/dev/zram0 partition 2097148 0 -2\n")

        val result = engine.getZramInfo()
        assertTrue(result.isSuccess)

        val info = result.getOrThrow()
        assertNotNull(info)
        assertEquals("zstd", info!!.activeAlgorithm)
        assertEquals(200000000L, info.origSizeBytes)
        assertEquals(80000000L, info.comprSizeBytes)
        assertEquals(85000000L, info.memUsedTotalBytes)
        assertEquals(60, info.spaceSavingsPercent)
    }

    @Test
    fun getZramInfo_returnsNull_whenZramDirectoryDoesNotExist() = runTest(testDispatcher) {
        val result = engine.getZramInfo()
        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
    }

    @Test
    fun resizeZram_executesSafeLifecycle_swapoffResetDisksizeMkswapSwapon() = runTest(testDispatcher) {
        fakeBackend.setFile("/sys/block/zram0/disksize", "2147483648\n")
        fakeBackend.setFile("/dev/block/zram0", "")
        fakeBackend.setFile("/proc/swaps", "/dev/block/zram0 partition 2097148 0 32758\n")

        val result = engine.resizeZram(
            newSizeBytes = 4294967296L,
            algorithm = "zstd"
        )
        assertTrue(result.isSuccess)

        // Verify write operations
        assertEquals("1", fakeBackend.getFile("/sys/block/zram0/reset"))
        assertEquals("zstd", fakeBackend.getFile("/sys/block/zram0/comp_algorithm"))
        assertEquals("4294967296", fakeBackend.getFile("/sys/block/zram0/disksize"))

        // Verify command sequence
        val history = fakeBackend.getExecutedCommands()
        assertEquals("swapoff /dev/block/zram0", history[0])
        assertEquals("mkswap /dev/block/zram0", history[1])
        assertEquals("swapon /dev/block/zram0 -p 32758", history[2])
    }

    @Test
    fun resizeZram_throwsException_whenSwapoffFails() = runTest(testDispatcher) {
        fakeBackend.setFile("/sys/block/zram0/disksize", "2147483648\n")
        fakeBackend.setFile("/dev/block/zram0", "")
        fakeBackend.setFile("/proc/swaps", "/dev/block/zram0 partition 2097148 0 32758\n")

        fakeBackend.registerCommandHandler("swapoff") {
            CommandResult.failure(1, listOf("swapoff: /dev/block/zram0: Cannot allocate memory"))
        }

        val result = engine.resizeZram(4294967296L)
        assertTrue(result.isFailure)
        assertNull(fakeBackend.getFile("/sys/block/zram0/reset"))
    }

    @Test
    fun resizeZram_turnsOffZram_whenTargetSizeIsZero() = runTest(testDispatcher) {
        fakeBackend.setFile("/sys/block/zram0/disksize", "2147483648\n")
        fakeBackend.setFile("/dev/block/zram0", "")
        fakeBackend.setFile("/proc/swaps", "/dev/block/zram0 partition 2097148 0 32758\n")

        val result = engine.resizeZram(0L)
        assertTrue(result.isSuccess)

        assertEquals("1", fakeBackend.getFile("/sys/block/zram0/reset"))
        val history = fakeBackend.getExecutedCommands()
        assertEquals(1, history.size)
        assertEquals("swapoff /dev/block/zram0", history[0])
    }

    @Test
    fun dropCaches_executesSyncAndWritesDropCaches() = runTest(testDispatcher) {
        val result = engine.dropCaches(DropCacheType.ALL)
        assertTrue(result.isSuccess)

        assertEquals("sync", fakeBackend.getExecutedCommands().first())
        assertEquals("3", fakeBackend.getFile("/proc/sys/vm/drop_caches"))
    }

    @Test
    fun compactZram_writesOneToCompactNode_whenExists() = runTest(testDispatcher) {
        fakeBackend.setFile("/sys/block/zram0/compact", "0\n")

        val result = engine.compactZram()
        assertTrue(result.isSuccess)
        assertEquals("1", fakeBackend.getFile("/sys/block/zram0/compact"))
    }
}
