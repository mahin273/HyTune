package com.mahin.hytune.ui.memory

import com.mahin.hytune.core.privilege.FakePrivilegeBackend
import com.mahin.hytune.domain.memory.MemoryControlEngine
import com.mahin.hytune.domain.memory.model.DropCacheType
import com.mahin.hytune.domain.sampler.SystemSamplerEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MemoryViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeBackend: FakePrivilegeBackend
    private lateinit var memoryControlEngine: MemoryControlEngine
    private lateinit var samplerEngine: SystemSamplerEngine
    private lateinit var viewModel: MemoryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        fakeBackend = FakePrivilegeBackend()

        // Mock VM files
        fakeBackend.setFile("/proc/sys/vm/swappiness", "100\n")
        fakeBackend.setFile("/proc/sys/vm/vfs_cache_pressure", "100\n")
        fakeBackend.setFile("/proc/sys/vm/dirty_ratio", "20\n")
        fakeBackend.setFile("/proc/sys/vm/dirty_background_ratio", "10\n")

        // Mock ZRAM files
        fakeBackend.setFile("/sys/block/zram0/disksize", "2147483648\n") // 2 GB
        fakeBackend.setFile("/sys/block/zram0/comp_algorithm", "[lz4] zstd\n")
        fakeBackend.setFile("/sys/block/zram0/mm_stat", " 200000000  80000000  85000000 0 0 0 0\n")
        fakeBackend.setFile("/dev/block/zram0", "")
        fakeBackend.setFile("/proc/swaps", "/dev/block/zram0 partition 2097148 0 32758\n")

        memoryControlEngine = MemoryControlEngine(fakeBackend, testDispatcher)
        samplerEngine = SystemSamplerEngine(fakeBackend, testDispatcher)

        viewModel = MemoryViewModel(memoryControlEngine, samplerEngine)
        viewModel.stopSampling()
    }

    @After
    fun tearDown() {
        viewModel.stopSampling()
        Dispatchers.resetMain()
    }

    @Test
    fun initialLoad_populatesLiveAndStagedValues() = runTest {
        val state = viewModel.uiState.value

        assertNotNull(state.vmTunables)
        assertEquals(100, state.vmTunables?.swappiness)
        assertEquals(100, state.stagedSwappiness)

        assertNotNull(state.zramInfo)
        assertEquals(2147483648L, state.zramInfo?.disksizeBytes)
        assertEquals(2147483648L, state.stagedZramSizeBytes)
        assertEquals("lz4", state.zramInfo?.activeAlgorithm)
        assertEquals("lz4", state.stagedAlgorithm)

        assertFalse(state.isDirty)
    }

    @Test
    fun onSwappinessChanged_updatesStagedAndSetsIsDirty() = runTest {
        viewModel.onSwappinessChanged(60)

        val state = viewModel.uiState.value
        assertEquals(60, state.stagedSwappiness)
        assertTrue(state.isDirty)
    }

    @Test
    fun onVfsCachePressureChanged_updatesStagedAndSetsIsDirty() = runTest {
        viewModel.onVfsCachePressureChanged(150)

        val state = viewModel.uiState.value
        assertEquals(150, state.stagedVfsCachePressure)
        assertTrue(state.isDirty)
    }

    @Test
    fun onZramSizeChanged_updatesStagedAndSetsIsDirty() = runTest {
        viewModel.onZramSizeChanged(4294967296L) // 4 GB

        val state = viewModel.uiState.value
        assertEquals(4294967296L, state.stagedZramSizeBytes)
        assertTrue(state.isDirty)
    }

    @Test
    fun onAlgorithmChanged_updatesStagedAndSetsIsDirty() = runTest {
        viewModel.onAlgorithmChanged("zstd")

        val state = viewModel.uiState.value
        assertEquals("zstd", state.stagedAlgorithm)
        assertTrue(state.isDirty)
    }

    @Test
    fun applyMemorySettings_writesToEngineAndResetsDirtyState() = runTest {
        viewModel.onSwappinessChanged(80)
        viewModel.onVfsCachePressureChanged(120)
        viewModel.onZramSizeChanged(4294967296L)
        viewModel.onAlgorithmChanged("zstd")

        assertTrue(viewModel.uiState.value.isDirty)

        viewModel.applyMemorySettings()

        val state = viewModel.uiState.value
        assertFalse(state.isDirty)
        assertEquals("Memory settings applied successfully", state.userMessage)
        assertNull(state.errorMessage)

        // Verify writes in fake backend
        assertEquals("80", fakeBackend.getFile("/proc/sys/vm/swappiness"))
        assertEquals("120", fakeBackend.getFile("/proc/sys/vm/vfs_cache_pressure"))
        assertEquals("4294967296", fakeBackend.getFile("/sys/block/zram0/disksize"))
        assertEquals("zstd", fakeBackend.getFile("/sys/block/zram0/comp_algorithm"))
    }

    @Test
    fun trimCaches_invokesEngineAndSetsUserMessage() = runTest {
        viewModel.trimCaches(DropCacheType.ALL)

        val state = viewModel.uiState.value
        assertEquals("Page caches dropped successfully", state.userMessage)
        assertNull(state.errorMessage)
        assertEquals("3", fakeBackend.getFile("/proc/sys/vm/drop_caches"))
    }

    @Test
    fun compactZram_invokesEngineAndSetsUserMessage() = runTest {
        fakeBackend.setFile("/sys/block/zram0/compact", "0\n")

        viewModel.compactZram()

        val state = viewModel.uiState.value
        assertEquals("ZRAM compaction triggered successfully", state.userMessage)
        assertNull(state.errorMessage)
        assertEquals("1", fakeBackend.getFile("/sys/block/zram0/compact"))
    }
}
