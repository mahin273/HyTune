package com.mahin.hytune.domain.cpu

import com.mahin.hytune.core.privilege.FakePrivilegeBackend
import com.mahin.hytune.domain.probe.HardwareProbe
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
class CpuControlEngineTest {

    private lateinit var fakeBackend: FakePrivilegeBackend
    private lateinit var hardwareProbe: HardwareProbe
    private lateinit var engine: CpuControlEngine
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
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

        // Policy 0 (Little cluster: cores 0-3)
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
        fakeBackend.setFile("$p0/schedutil/rate_limit_us", "2000\n")
        fakeBackend.setFile("$p0/schedutil/up_rate_limit_us", "500\n")

        // Policy 4 (Big cluster: cores 4-7)
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
        fakeBackend.setFile("$p4/schedutil/rate_limit_us", "1000\n")

        hardwareProbe = HardwareProbe(fakeBackend)
        engine = CpuControlEngine(
            privilegeBackend = fakeBackend,
            hardwareProbe = hardwareProbe,
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun getClusterPolicies_discoversPoliciesWithFrequenciesAndGovernors() = runTest(testDispatcher) {
        val result = engine.getClusterPolicies()
        assertTrue(result.isSuccess)

        val policies = result.getOrThrow()
        assertEquals(2, policies.size)

        val little = policies[0]
        assertEquals(0, little.clusterId)
        assertEquals(listOf(0, 1, 2, 3), little.affectedCores)
        assertEquals("schedutil", little.currentGovernor)
        assertEquals(listOf("schedutil", "performance", "powersave"), little.availableGovernors)
        assertEquals(300000L, little.minFreqKHz)
        assertEquals(1800000L, little.maxFreqKHz)
        assertEquals(600000L, little.curFreqKHz)
        assertEquals("2000", little.governorTunables["rate_limit_us"])

        val big = policies[1]
        assertEquals(4, big.clusterId)
        assertEquals(listOf(4, 5, 6, 7), big.affectedCores)
        assertEquals("schedutil", big.currentGovernor)
        assertEquals(2800000L, big.maxFreqKHz)
    }

    @Test
    fun setGovernor_validGovernorSucceedsAndUpdatesSysfs() = runTest(testDispatcher) {
        val result = engine.setGovernor(clusterId = 0, governor = "performance")
        assertTrue(result.isSuccess)

        val updated = fakeBackend.readFile("/sys/devices/system/cpu/cpufreq/policy0/scaling_governor").getOrNull()?.trim()
        assertEquals("performance", updated)
    }

    @Test
    fun setGovernor_unsupportedGovernorFailsWithoutWriting() = runTest(testDispatcher) {
        val result = engine.setGovernor(clusterId = 0, governor = "conservative")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)

        val governor = fakeBackend.readFile("/sys/devices/system/cpu/cpufreq/policy0/scaling_governor").getOrNull()?.trim()
        assertEquals("schedutil", governor)
    }

    @Test
    fun setFrequencies_snapsToAvailableStepsAndAppliesSafely() = runTest(testDispatcher) {
        // Target 550000 (should snap to 600000) and 1300000 (should snap to 1200000)
        val result = engine.setFrequencies(clusterId = 0, minFreqKHz = 550000L, maxFreqKHz = 1300000L)
        assertTrue(result.isSuccess)

        val min = fakeBackend.readFile("/sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq").getOrNull()?.trim()?.toLongOrNull()
        val max = fakeBackend.readFile("/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq").getOrNull()?.trim()?.toLongOrNull()

        assertEquals(600000L, min)
        assertEquals(1200000L, max)
    }

    @Test
    fun setFrequencies_adaptiveOrderingRaisesMaxBeforeMinWhenMinExceedsCurrentMax() = runTest(testDispatcher) {
        val p0 = "/sys/devices/system/cpu/cpufreq/policy0"
        // Setup initial low ceiling
        fakeBackend.setFile("$p0/scaling_min_freq", "300000\n")
        fakeBackend.setFile("$p0/scaling_max_freq", "600000\n")

        // New targets: min=1200000, max=1800000 (min > currentMax of 600000)
        val result = engine.setFrequencies(p0, minFreqKHz = 1200000L, maxFreqKHz = 1800000L)
        assertTrue(result.isSuccess)

        val min = fakeBackend.readFile("$p0/scaling_min_freq").getOrNull()?.trim()?.toLongOrNull()
        val max = fakeBackend.readFile("$p0/scaling_max_freq").getOrNull()?.trim()?.toLongOrNull()

        assertEquals(1200000L, min)
        assertEquals(1800000L, max)
    }

    @Test
    fun getCoreStates_reportsOnlineStatusAndConfigurability() = runTest(testDispatcher) {
        val statesResult = engine.getCoreStates()
        assertTrue(statesResult.isSuccess)

        val states = statesResult.getOrThrow()
        assertEquals(8, states.size)

        // Core 0 must not be configurable
        val core0 = states[0]
        assertEquals(0, core0.coreId)
        assertTrue(core0.isOnline)
        assertFalse(core0.isConfigurable)

        // Core 1 must be configurable
        val core1 = states[1]
        assertEquals(1, core1.coreId)
        assertTrue(core1.isOnline)
        assertTrue(core1.isConfigurable)
    }

    @Test
    fun setCoreOnline_offlinesAndOnlinesCoreSuccessfully() = runTest(testDispatcher) {
        // Offline Core 1
        val offlineResult = engine.setCoreOnline(coreId = 1, online = false)
        assertTrue(offlineResult.isSuccess)

        var core1Online = fakeBackend.readFile("/sys/devices/system/cpu/cpu1/online").getOrNull()?.trim()
        assertEquals("0", core1Online)

        // Online Core 1
        val onlineResult = engine.setCoreOnline(coreId = 1, online = true)
        assertTrue(onlineResult.isSuccess)

        core1Online = fakeBackend.readFile("/sys/devices/system/cpu/cpu1/online").getOrNull()?.trim()
        assertEquals("1", core1Online)
    }

    @Test
    fun setCoreOnline_refusesToOfflineCore0() = runTest(testDispatcher) {
        val result = engine.setCoreOnline(coreId = 0, online = false)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun setCoreOnline_refusesToOfflineLastRemainingOnlineCore() = runTest(testDispatcher) {
        // Offline cores 2..7 first
        for (i in 2..7) {
            fakeBackend.setFile("/sys/devices/system/cpu/cpu$i/online", "0\n")
        }
        // Core 0 lacks /online node in our setup. Now simulate Core 0 is offline if node exists, or test with core 1
        fakeBackend.setFile("/sys/devices/system/cpu/cpu0/online", "0\n")
        fakeBackend.setFile("/sys/devices/system/cpu/cpu1/online", "1\n")

        // Now Core 1 is the ONLY online core on the system. Offlining it must fail!
        val result = engine.setCoreOnline(coreId = 1, online = false)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun governorTunables_readsAndWritesTunablesCorrectly() = runTest(testDispatcher) {
        val p0 = "/sys/devices/system/cpu/cpufreq/policy0"
        val tunables = engine.getGovernorTunables(p0).getOrThrow()
        assertEquals("2000", tunables["rate_limit_us"])
        assertEquals("500", tunables["up_rate_limit_us"])

        val setResult = engine.setGovernorTunable(p0, "rate_limit_us", "1000")
        assertTrue(setResult.isSuccess)

        val updated = fakeBackend.readFile("$p0/schedutil/rate_limit_us").getOrNull()?.trim()
        assertEquals("1000", updated)
    }
}
