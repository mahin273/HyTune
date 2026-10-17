package com.mahin.hytune.ui.cpu

import com.mahin.hytune.core.privilege.FakePrivilegeBackend
import com.mahin.hytune.domain.cpu.CpuControlEngine
import com.mahin.hytune.domain.probe.HardwareProbe
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
class CpuViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeBackend: FakePrivilegeBackend
    private lateinit var hardwareProbe: HardwareProbe
    private lateinit var cpuControlEngine: CpuControlEngine
    private lateinit var samplerEngine: SystemSamplerEngine
    private lateinit var viewModel: CpuViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        fakeBackend = FakePrivilegeBackend()

        // Setup CPU directory and cores
        fakeBackend.setFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq", "600000\n")
        fakeBackend.setFile("/sys/devices/system/cpu/cpu1/online", "1\n")
        fakeBackend.setFile("/sys/devices/system/cpu/cpu2/online", "1\n")
        fakeBackend.setFile("/sys/devices/system/cpu/cpu3/online", "1\n")
        fakeBackend.setFile("/sys/devices/system/cpu/cpu4/online", "1\n")
        fakeBackend.setFile("/sys/devices/system/cpu/cpu5/online", "1\n")
        fakeBackend.setFile("/sys/devices/system/cpu/cpu6/online", "1\n")
        fakeBackend.setFile("/sys/devices/system/cpu/cpu7/online", "1\n")

        // Policy 0 (Little cluster)
        val p0 = "/sys/devices/system/cpu/cpufreq/policy0"
        fakeBackend.setFile("$p0/affected_cpus", "0 1 2 3\n")
        fakeBackend.setFile("$p0/scaling_available_governors", "schedutil performance powersave\n")
        fakeBackend.setFile("$p0/scaling_governor", "schedutil\n")
        fakeBackend.setFile("$p0/scaling_available_frequencies", "300000 600000 1200000 1800000\n")
        fakeBackend.setFile("$p0/cpuinfo_min_freq", "300000\n")
        fakeBackend.setFile("$p0/cpuinfo_max_freq", "1800000\n")
        fakeBackend.setFile("$p0/scaling_min_freq", "300000\n")
        fakeBackend.setFile("$p0/scaling_max_freq", "1800000\n")
        fakeBackend.setFile("$p0/scaling_cur_freq", "600000\n")

        // Policy 4 (Big cluster)
        val p4 = "/sys/devices/system/cpu/cpufreq/policy4"
        fakeBackend.setFile("$p4/affected_cpus", "4 5 6 7\n")
        fakeBackend.setFile("$p4/scaling_available_governors", "schedutil performance\n")
        fakeBackend.setFile("$p4/scaling_governor", "schedutil\n")
        fakeBackend.setFile("$p4/scaling_available_frequencies", "800000 1400000 2200000 2800000\n")
        fakeBackend.setFile("$p4/cpuinfo_min_freq", "800000\n")
        fakeBackend.setFile("$p4/cpuinfo_max_freq", "2800000\n")
        fakeBackend.setFile("$p4/scaling_min_freq", "800000\n")
        fakeBackend.setFile("$p4/scaling_max_freq", "2800000\n")
        fakeBackend.setFile("$p4/scaling_cur_freq", "1400000\n")

        hardwareProbe = HardwareProbe(fakeBackend)
        cpuControlEngine = CpuControlEngine(fakeBackend, hardwareProbe, testDispatcher)
        samplerEngine = SystemSamplerEngine(fakeBackend, testDispatcher)

        viewModel = CpuViewModel(cpuControlEngine, samplerEngine)
        viewModel.stopSampling()
    }

    @After
    fun tearDown() {
        viewModel.stopSampling()
        Dispatchers.resetMain()
    }

    @Test
    fun initialLoading_populatesClusterPoliciesAndCoreStates() = runTest {
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(2, state.clusterPolicies.size)
        assertEquals(8, state.coreStates.size)

        assertEquals("schedutil", state.stagedGovernors[0])
        assertEquals(300000L, state.stagedMinFreqs[0])
        assertEquals(1800000L, state.stagedMaxFreqs[0])
        assertFalse(state.isDirty(0))
        assertFalse(state.isDirty(4))
    }

    @Test
    fun onGovernorChanged_updatesStagedGovernorAndFlagsDirty() = runTest {
        viewModel.onGovernorChanged(0, "performance")

        val state = viewModel.uiState.value
        assertEquals("performance", state.stagedGovernors[0])
        assertTrue(state.isDirty(0))
    }

    @Test
    fun onMinFreqChanged_updatesStagedMinFreqAndClampsMaxIfNecessary() = runTest {
        viewModel.onMinFreqChanged(0, 1800000L)

        val state = viewModel.uiState.value
        assertEquals(1800000L, state.stagedMinFreqs[0])
        assertEquals(1800000L, state.stagedMaxFreqs[0])
        assertTrue(state.isDirty(0))
    }

    @Test
    fun onMaxFreqChanged_updatesStagedMaxFreqAndClampsMinIfNecessary() = runTest {
        viewModel.onMaxFreqChanged(0, 300000L)

        val state = viewModel.uiState.value
        assertEquals(300000L, state.stagedMaxFreqs[0])
        assertEquals(300000L, state.stagedMinFreqs[0])
        assertTrue(state.isDirty(0))
    }

    @Test
    fun applyClusterSettings_commitsChangesToEngineAndRefreshes() = runTest {
        viewModel.onGovernorChanged(0, "performance")
        viewModel.onMinFreqChanged(0, 600000L)
        viewModel.onMaxFreqChanged(0, 1200000L)

        viewModel.applyClusterSettings(0)

        val state = viewModel.uiState.value
        assertFalse(state.isApplying)
        assertFalse(state.isDirty(0))
        assertNotNull(state.userMessage)
        assertTrue(state.userMessage!!.contains("successfully"))

        val sysfsGov = fakeBackend.readFile("/sys/devices/system/cpu/cpufreq/policy0/scaling_governor").getOrNull()?.trim()
        val sysfsMin = fakeBackend.readFile("/sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq").getOrNull()?.trim()?.toLongOrNull()
        val sysfsMax = fakeBackend.readFile("/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq").getOrNull()?.trim()?.toLongOrNull()

        assertEquals("performance", sysfsGov)
        assertEquals(600000L, sysfsMin)
        assertEquals(1200000L, sysfsMax)
    }

    @Test
    fun resetClusterSettings_restoresStagedValues() = runTest {
        viewModel.onGovernorChanged(0, "performance")
        viewModel.onMinFreqChanged(0, 1200000L)
        assertTrue(viewModel.uiState.value.isDirty(0))

        viewModel.resetClusterSettings(0)

        val state = viewModel.uiState.value
        assertEquals("schedutil", state.stagedGovernors[0])
        assertEquals(300000L, state.stagedMinFreqs[0])
        assertEquals(1800000L, state.stagedMaxFreqs[0])
        assertFalse(state.isDirty(0))
    }

    @Test
    fun onCoreOnlineToggled_dispatchesToEngineAndUpdatesCoreStates() = runTest {
        viewModel.onCoreOnlineToggled(coreId = 1, online = false)

        val state = viewModel.uiState.value
        val core1 = state.coreStates.find { it.coreId == 1 }
        assertNotNull(core1)
        assertFalse(core1!!.isOnline)
        assertTrue(state.userMessage?.contains("offline") == true)

        val sysfsOnline = fakeBackend.readFile("/sys/devices/system/cpu/cpu1/online").getOrNull()?.trim()
        assertEquals("0", sysfsOnline)
    }

    @Test
    fun onCoreOnlineToggled_refusingCore0DisplaysErrorMessage() = runTest {
        viewModel.onCoreOnlineToggled(coreId = 0, online = false)

        val state = viewModel.uiState.value
        assertNotNull(state.userMessage)
        assertTrue(state.userMessage!!.contains("boot core"))
    }
}
