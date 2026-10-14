package com.mahin.hytune.domain.sampler

import com.mahin.hytune.core.privilege.FakePrivilegeBackend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SystemSamplerEngineTest {

    private lateinit var fakeBackend: FakePrivilegeBackend
    private lateinit var engine: SystemSamplerEngine
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        fakeBackend = FakePrivilegeBackend()
        engine = SystemSamplerEngine(privilegeBackend = fakeBackend, ioDispatcher = testDispatcher)
    }

    @Test
    fun sampleCpu_deltaCalculatesAccurateUsage() = runTest(testDispatcher) {
        fakeBackend.setFile("/proc/stat", "cpu  1000 0 200 8800 0 0 0 0\n")
        val initialSample = engine.sampleCpu()
        assertEquals(0f, initialSample.overallUsagePercent, 0.01f)

        fakeBackend.setFile("/proc/stat", "cpu  1300 0 300 9200 0 0 0 0\n")
        val secondSample = engine.sampleCpu()
        assertEquals(50.0f, secondSample.overallUsagePercent, 0.01f)
    }

    @Test
    fun sampleCpu_perCoreFrequenciesExtracted() = runTest(testDispatcher) {
        val cpuBase = "/sys/devices/system/cpu"
        fakeBackend.setFile("$cpuBase/cpu0/cpufreq/scaling_cur_freq", "1804800")
        fakeBackend.setFile("$cpuBase/cpu4/cpufreq/scaling_cur_freq", "2419200")

        val sample = engine.sampleCpu()

        assertEquals(2, sample.coreFrequenciesKHz.size)
        assertEquals(1804800L, sample.coreFrequenciesKHz[0])
        assertEquals(2419200L, sample.coreFrequenciesKHz[4])
    }

    @Test
    fun sampleMemory_accurateRamAndSwapAccounting() = runTest(testDispatcher) {
        val meminfo = """
            MemTotal:        8000000 kB
            MemFree:         1000000 kB
            MemAvailable:    4000000 kB
            Buffers:          200000 kB
            Cached:          1800000 kB
            SwapTotal:       2000000 kB
            SwapFree:         500000 kB
        """.trimIndent()

        fakeBackend.setFile("/proc/meminfo", meminfo)

        val memory = engine.sampleMemory()

        assertEquals(8000000L * 1024L, memory.totalBytes)
        assertEquals(4000000L * 1024L, memory.availableBytes)
        assertEquals(4000000L * 1024L, memory.usedBytes)
        assertEquals(50.0f, memory.usagePercent, 0.01f)
        assertEquals(2000000L * 1024L, memory.swapTotalBytes)
        assertEquals(1500000L * 1024L, memory.swapUsedBytes)
        assertEquals(75.0f, memory.swapUsagePercent, 0.01f)
    }

    @Test
    fun sampleThermals_normalizesMillidegreesAndCategorizesSensors() = runTest(testDispatcher) {
        val thermalBase = "/sys/class/thermal"
        fakeBackend.setFile("$thermalBase/thermal_zone0/type", "tsens_tz_sensor0")
        fakeBackend.setFile("$thermalBase/thermal_zone0/temp", "41500")

        fakeBackend.setFile("$thermalBase/thermal_zone1/type", "battery")
        fakeBackend.setFile("$thermalBase/thermal_zone1/temp", "33200")

        val thermals = engine.sampleThermals()

        assertEquals(41.5f, thermals.socTempC, 0.01f)
        assertEquals(33.2f, thermals.batteryTempC, 0.01f)
        assertEquals(41.5f, thermals.maxTempC, 0.01f)
        assertEquals(2, thermals.zones.size)
    }

    @Test
    fun sampleBattery_calculatesPowerInWatts() = runTest(testDispatcher) {
        val batteryBase = "/sys/class/power_supply/battery"
        fakeBackend.setFile("$batteryBase/capacity", "85")
        fakeBackend.setFile("$batteryBase/temp", "320")
        fakeBackend.setFile("$batteryBase/voltage_now", "4000000")
        fakeBackend.setFile("$batteryBase/current_now", "-500000")

        val battery = engine.sampleBattery()

        assertNotNull(battery)
        assertEquals(85, battery?.percent)
        assertEquals(32.0f, battery!!.temperatureC, 0.01f)
        assertEquals(4.0f, battery.voltageV, 0.01f)
        assertEquals(-0.5f, battery.currentAmperes, 0.01f)
        assertEquals(-2.0f, battery.powerWatts, 0.01f)
    }

    @Test
    fun sampleStream_emitsSnapshotsSequentially() = runTest(testDispatcher) {
        fakeBackend.setFile("/proc/stat", "cpu  1000 0 200 8800 0 0 0 0\n")
        fakeBackend.setFile("/proc/meminfo", "MemTotal: 4000000 kB\nMemAvailable: 2000000 kB\n")

        val snapshots = mutableListOf<com.mahin.hytune.domain.sampler.model.SystemSnapshot>()
        val flow = engine.sampleStream(intervalMs = 10)

        val job = testScheduler.advanceUntilIdle()
        val collected = engine.sampleStream(intervalMs = 10).take(2).toList()

        assertEquals(2, collected.size)
        assertTrue(collected[0].timestampEpochMs > 0)
        assertTrue(collected[1].timestampEpochMs >= collected[0].timestampEpochMs)
    }
}
