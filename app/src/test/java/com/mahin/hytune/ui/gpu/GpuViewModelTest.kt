package com.mahin.hytune.ui.gpu

import com.mahin.hytune.core.privilege.FakePrivilegeBackend
import com.mahin.hytune.domain.gpu.GpuControlEngine
import com.mahin.hytune.domain.probe.HardwareProbe
import com.mahin.hytune.domain.probe.model.GpuVendor
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GpuViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeBackend: FakePrivilegeBackend
    private lateinit var hardwareProbe: HardwareProbe
    private lateinit var gpuControlEngine: GpuControlEngine
    private lateinit var samplerEngine: SystemSamplerEngine
    private lateinit var viewModel: GpuViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        fakeBackend = FakePrivilegeBackend()

        // Setup Adreno GPU
        val kgsl = "/sys/class/kgsl/kgsl-3d0"
        fakeBackend.setFile("$kgsl/gpu_available_frequencies", "300000 450000 650000 800000\n")
        fakeBackend.setFile("$kgsl/devfreq/cur_freq", "450000\n")
        fakeBackend.setFile("$kgsl/devfreq/min_freq", "300000\n")
        fakeBackend.setFile("$kgsl/devfreq/max_freq", "800000\n")
        fakeBackend.setFile("$kgsl/devfreq/governor", "msm-adreno-tz\n")
        fakeBackend.setFile("$kgsl/devfreq/available_governors", "msm-adreno-tz performance powersave\n")

        hardwareProbe = HardwareProbe(fakeBackend)
        gpuControlEngine = GpuControlEngine(fakeBackend, hardwareProbe, testDispatcher)
        samplerEngine = SystemSamplerEngine(fakeBackend, testDispatcher)

        viewModel = GpuViewModel(gpuControlEngine, samplerEngine)
        viewModel.stopSampling()
    }

    @After
    fun tearDown() {
        viewModel.stopSampling()
        Dispatchers.resetMain()
    }

    @Test
    fun initialLoading_populatesPolicyAndStagedValues() = runTest {
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.policy)
        assertEquals(GpuVendor.ADRENO, state.policy!!.vendor)
        assertEquals("msm-adreno-tz", state.stagedGovernor)
        assertEquals(300000L, state.stagedMinFreqKHz)
        assertEquals(800000L, state.stagedMaxFreqKHz)
        assertFalse(state.isDirty())
    }

    @Test
    fun onGovernorChanged_updatesStagedGovernorAndFlagsDirty() = runTest {
        viewModel.onGovernorChanged("performance")

        val state = viewModel.uiState.value
        assertEquals("performance", state.stagedGovernor)
        assertTrue(state.isDirty())
    }

    @Test
    fun onMinFreqChanged_updatesStagedMinAndClampsMaxIfNecessary() = runTest {
        viewModel.onMinFreqChanged(800000L)

        val state = viewModel.uiState.value
        assertEquals(800000L, state.stagedMinFreqKHz)
        assertEquals(800000L, state.stagedMaxFreqKHz)
        assertTrue(state.isDirty())
    }

    @Test
    fun onMaxFreqChanged_updatesStagedMaxAndClampsMinIfNecessary() = runTest {
        viewModel.onMaxFreqChanged(300000L)

        val state = viewModel.uiState.value
        assertEquals(300000L, state.stagedMinFreqKHz)
        assertEquals(300000L, state.stagedMaxFreqKHz)
        assertTrue(state.isDirty())
    }

    @Test
    fun applyGpuSettings_commitsChangesToEngineAndClearsDirty() = runTest {
        viewModel.onGovernorChanged("performance")
        viewModel.onMinFreqChanged(450000L)
        viewModel.onMaxFreqChanged(650000L)
        assertTrue(viewModel.uiState.value.isDirty())

        viewModel.applyGpuSettings()

        val state = viewModel.uiState.value
        assertFalse(state.isApplying)
        assertFalse(state.isDirty())
        assertNotNull(state.userMessage)
        assertTrue(state.userMessage!!.contains("successfully"))

        val kgsl = "/sys/class/kgsl/kgsl-3d0"
        assertEquals("performance", fakeBackend.readFile("$kgsl/devfreq/governor").getOrNull()?.trim())
        assertEquals("450000", fakeBackend.readFile("$kgsl/devfreq/min_freq").getOrNull()?.trim())
        assertEquals("650000", fakeBackend.readFile("$kgsl/devfreq/max_freq").getOrNull()?.trim())
    }

    @Test
    fun resetGpuSettings_restoresOriginalValues() = runTest {
        viewModel.onGovernorChanged("performance")
        viewModel.onMinFreqChanged(650000L)
        assertTrue(viewModel.uiState.value.isDirty())

        viewModel.resetGpuSettings()

        val state = viewModel.uiState.value
        assertEquals("msm-adreno-tz", state.stagedGovernor)
        assertEquals(300000L, state.stagedMinFreqKHz)
        assertEquals(800000L, state.stagedMaxFreqKHz)
        assertFalse(state.isDirty())
    }
}
