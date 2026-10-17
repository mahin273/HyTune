package com.mahin.hytune.domain.gpu

import com.mahin.hytune.core.privilege.FakePrivilegeBackend
import com.mahin.hytune.domain.probe.HardwareProbe
import com.mahin.hytune.domain.probe.model.GpuVendor
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
class GpuControlEngineTest {

    private lateinit var fakeBackend: FakePrivilegeBackend
    private lateinit var hardwareProbe: HardwareProbe
    private lateinit var engine: GpuControlEngine
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        fakeBackend = FakePrivilegeBackend()
        hardwareProbe = HardwareProbe(fakeBackend)
        engine = GpuControlEngine(
            privilegeBackend = fakeBackend,
            hardwareProbe = hardwareProbe,
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun getGpuPolicy_adrenoDiscoveredWithPowerLevelsAndFrequencies() = runTest(testDispatcher) {
        val kgsl = "/sys/class/kgsl/kgsl-3d0"
        fakeBackend.setFile("$kgsl/gpu_available_frequencies", "300000 450000 650000 800000\n")
        fakeBackend.setFile("$kgsl/devfreq/cur_freq", "450000\n")
        fakeBackend.setFile("$kgsl/devfreq/min_freq", "300000\n")
        fakeBackend.setFile("$kgsl/devfreq/max_freq", "800000\n")
        fakeBackend.setFile("$kgsl/devfreq/governor", "msm-adreno-tz\n")
        fakeBackend.setFile("$kgsl/devfreq/available_governors", "msm-adreno-tz performance powersave\n")
        fakeBackend.setFile("$kgsl/num_pwrlevels", "4\n")
        fakeBackend.setFile("$kgsl/min_pwrlevel", "0\n")
        fakeBackend.setFile("$kgsl/max_pwrlevel", "3\n")
        fakeBackend.setFile("$kgsl/default_pwrlevel", "2\n")
        fakeBackend.setFile("$kgsl/idle_timer", "64\n")

        val result = engine.getGpuPolicy()
        assertTrue(result.isSuccess)

        val policy = result.getOrThrow()
        assertNotNull(policy)
        assertEquals(GpuVendor.ADRENO, policy!!.vendor)
        assertEquals(4, policy.availableFrequenciesKHz.size)
        assertEquals(300000L, policy.minFreqKHz)
        assertEquals(800000L, policy.maxFreqKHz)
        assertEquals(450000L, policy.curFreqKHz)
        assertEquals("msm-adreno-tz", policy.currentGovernor)
        assertEquals(listOf("msm-adreno-tz", "performance", "powersave"), policy.availableGovernors)

        assertNotNull(policy.powerLevelInfo)
        assertEquals(4, policy.powerLevelInfo!!.numPwrlevels)
        assertEquals(0, policy.powerLevelInfo!!.minPwrlevel)
        assertEquals(3, policy.powerLevelInfo!!.maxPwrlevel)
        assertEquals(64, policy.idleTimerMs)
    }

    @Test
    fun setGovernor_validGovernorSucceedsAndUpdatesSysfs() = runTest(testDispatcher) {
        val kgsl = "/sys/class/kgsl/kgsl-3d0"
        fakeBackend.setFile("$kgsl/gpu_available_frequencies", "300000 650000\n")
        fakeBackend.setFile("$kgsl/devfreq/governor", "msm-adreno-tz\n")
        fakeBackend.setFile("$kgsl/devfreq/available_governors", "msm-adreno-tz performance powersave\n")

        val result = engine.setGovernor("performance")
        assertTrue(result.isSuccess)

        val readBack = fakeBackend.readFile("$kgsl/devfreq/governor").getOrNull()?.trim()
        assertEquals("performance", readBack)
    }

    @Test
    fun setGovernor_unsupportedGovernorFails() = runTest(testDispatcher) {
        val kgsl = "/sys/class/kgsl/kgsl-3d0"
        fakeBackend.setFile("$kgsl/gpu_available_frequencies", "300000 650000\n")
        fakeBackend.setFile("$kgsl/devfreq/governor", "msm-adreno-tz\n")
        fakeBackend.setFile("$kgsl/devfreq/available_governors", "msm-adreno-tz performance\n")

        val result = engine.setGovernor("simple_ondemand")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun setFrequencies_adaptiveOrderingRaisesMaxBeforeMinWhenMinExceedsCurrentMax() = runTest(testDispatcher) {
        val kgsl = "/sys/class/kgsl/kgsl-3d0"
        fakeBackend.setFile("$kgsl/gpu_available_frequencies", "300000 450000 650000 800000\n")
        fakeBackend.setFile("$kgsl/devfreq/governor", "msm-adreno-tz\n")
        fakeBackend.setFile("$kgsl/devfreq/available_governors", "msm-adreno-tz\n")

        // Setup initial low ceiling (max = 450000)
        fakeBackend.setFile("$kgsl/devfreq/min_freq", "300000\n")
        fakeBackend.setFile("$kgsl/devfreq/max_freq", "450000\n")

        // Target: min = 650000, max = 800000 (targetMin > currentMax of 450000)
        val result = engine.setFrequencies(minFreqKHz = 650000L, maxFreqKHz = 800000L)
        assertTrue(result.isSuccess)

        val minRead = fakeBackend.readFile("$kgsl/devfreq/min_freq").getOrNull()?.trim()?.toLongOrNull()
        val maxRead = fakeBackend.readFile("$kgsl/devfreq/max_freq").getOrNull()?.trim()?.toLongOrNull()

        assertEquals(650000L, minRead)
        assertEquals(800000L, maxRead)
    }

    @Test
    fun getGpuPolicy_maliDevfreqDiscoveredAndControlled() = runTest(testDispatcher) {
        val mali = "/sys/class/devfreq/13040000.mali"
        fakeBackend.setFile("$mali/available_frequencies", "260000 400000 600000 850000\n")
        fakeBackend.setFile("$mali/cur_freq", "400000\n")
        fakeBackend.setFile("$mali/min_freq", "260000\n")
        fakeBackend.setFile("$mali/max_freq", "850000\n")
        fakeBackend.setFile("$mali/governor", "simple_ondemand\n")
        fakeBackend.setFile("$mali/available_governors", "simple_ondemand performance powersave\n")

        val result = engine.getGpuPolicy()
        assertTrue(result.isSuccess)

        val policy = result.getOrThrow()
        assertNotNull(policy)
        assertEquals(GpuVendor.MALI, policy!!.vendor)
        assertEquals(4, policy.availableFrequenciesKHz.size)
        assertEquals("simple_ondemand", policy.currentGovernor)

        // Test frequency adjustment on Mali
        val freqResult = engine.setFrequencies(minFreqKHz = 400000L, maxFreqKHz = 600000L)
        assertTrue(freqResult.isSuccess)

        val minRead = fakeBackend.readFile("$mali/min_freq").getOrNull()?.trim()?.toLongOrNull()
        val maxRead = fakeBackend.readFile("$mali/max_freq").getOrNull()?.trim()?.toLongOrNull()

        assertEquals(400000L, minRead)
        assertEquals(600000L, maxRead)
    }

    @Test
    fun setPowerLevelAndIdleTimer_writesValuesSafely() = runTest(testDispatcher) {
        val kgsl = "/sys/class/kgsl/kgsl-3d0"
        fakeBackend.setFile("$kgsl/gpu_available_frequencies", "300000 650000\n")
        fakeBackend.setFile("$kgsl/devfreq/governor", "msm-adreno-tz\n")
        fakeBackend.setFile("$kgsl/devfreq/available_governors", "msm-adreno-tz\n")
        fakeBackend.setFile("$kgsl/num_pwrlevels", "4\n")
        fakeBackend.setFile("$kgsl/min_pwrlevel", "0\n")
        fakeBackend.setFile("$kgsl/max_pwrlevel", "3\n")
        fakeBackend.setFile("$kgsl/idle_timer", "64\n")

        val pwrResult = engine.setPowerLevel(minPwrlevel = 1, maxPwrlevel = 2)
        assertTrue(pwrResult.isSuccess)
        assertEquals("1", fakeBackend.readFile("$kgsl/min_pwrlevel").getOrNull()?.trim())
        assertEquals("2", fakeBackend.readFile("$kgsl/max_pwrlevel").getOrNull()?.trim())

        val timerResult = engine.setIdleTimer(100)
        assertTrue(timerResult.isSuccess)
        assertEquals("100", fakeBackend.readFile("$kgsl/idle_timer").getOrNull()?.trim())
    }
}
