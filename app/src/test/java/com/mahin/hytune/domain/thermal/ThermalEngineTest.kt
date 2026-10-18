package com.mahin.hytune.domain.thermal

import com.mahin.hytune.core.privilege.Capability
import com.mahin.hytune.core.privilege.FakePrivilegeBackend
import com.mahin.hytune.core.privilege.PrivilegeTier
import com.mahin.hytune.domain.thermal.model.ThermalZoneType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThermalEngineTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeBackend: FakePrivilegeBackend
    private lateinit var engine: ThermalEngine

    @Before
    fun setUp() {
        fakeBackend = FakePrivilegeBackend(
            initialTier = PrivilegeTier.ROOT,
            initialCapabilities = Capability.entries.toSet()
        )
        engine = ThermalEngine(fakeBackend, testDispatcher)
    }

    @Test
    fun sampleThermals_parsesZonesAndNormalizesTemperature() = runTest(testDispatcher) {
        val base = "/sys/class/thermal"
        fakeBackend.setFile("$base/thermal_zone0/type", "cpu-1-0-usr\n")
        fakeBackend.setFile("$base/thermal_zone0/temp", "43500\n")

        fakeBackend.setFile("$base/thermal_zone1/type", "gpuss-0-usr\n")
        fakeBackend.setFile("$base/thermal_zone1/temp", "41000\n")

        val snapshot = engine.sampleThermals()
        assertEquals(2, snapshot.zones.size)

        val cpuZone = snapshot.zones.first { it.id == 0 }
        assertEquals("cpu-1-0-usr", cpuZone.name)
        assertEquals(43.5f, cpuZone.tempC, 0.01f)
        assertEquals(ThermalZoneType.CPU, cpuZone.type)
        assertFalse(cpuZone.isThrottling)

        val gpuZone = snapshot.zones.first { it.id == 1 }
        assertEquals("gpuss-0-usr", gpuZone.name)
        assertEquals(41.0f, gpuZone.tempC, 0.01f)
        assertEquals(ThermalZoneType.GPU, gpuZone.type)
    }

    @Test
    fun sampleThermals_classifiesCategoriesCorrectly() = runTest(testDispatcher) {
        val base = "/sys/class/thermal"
        fakeBackend.setFile("$base/thermal_zone0/type", "battery\n")
        fakeBackend.setFile("$base/thermal_zone0/temp", "320\n") // 32.0 C (tenths)

        fakeBackend.setFile("$base/thermal_zone1/type", "soc_max\n")
        fakeBackend.setFile("$base/thermal_zone1/temp", "45.0\n") // 45.0 C

        fakeBackend.setFile("$base/thermal_zone2/type", "modem-usr\n")
        fakeBackend.setFile("$base/thermal_zone2/temp", "38000\n")

        val snapshot = engine.sampleThermals()
        val battZone = snapshot.zones.first { it.id == 0 }
        assertEquals(ThermalZoneType.BATTERY, battZone.type)
        assertEquals(32.0f, battZone.tempC, 0.01f)

        val socZone = snapshot.zones.first { it.id == 1 }
        assertEquals(ThermalZoneType.SOC, socZone.type)
        assertEquals(45.0f, socZone.tempC, 0.01f)

        val modemZone = snapshot.zones.first { it.id == 2 }
        assertEquals(ThermalZoneType.MODEM, modemZone.type)
    }

    @Test
    fun sampleThermals_detectsCoolingDeviceThrottling() = runTest(testDispatcher) {
        val base = "/sys/class/thermal"
        fakeBackend.setFile("$base/thermal_zone0/type", "cpu-0\n")
        fakeBackend.setFile("$base/thermal_zone0/temp", "55000\n")

        fakeBackend.setFile("$base/cooling_device0/type", "thermal-cpufreq-0\n")
        fakeBackend.setFile("$base/cooling_device0/cur_state", "2\n")
        fakeBackend.setFile("$base/cooling_device0/max_state", "5\n")

        val snapshot = engine.sampleThermals()
        assertEquals(1, snapshot.coolingDevices.size)
        val cdev = snapshot.coolingDevices.first()
        assertEquals("thermal-cpufreq-0", cdev.name)
        assertEquals(2, cdev.curState)
        assertEquals(5, cdev.maxState)
        assertTrue(cdev.isThrottling)
        assertTrue(snapshot.isThrottling)
    }

    @Test
    fun sampleThermals_identifiesHottestZone() = runTest(testDispatcher) {
        val base = "/sys/class/thermal"
        fakeBackend.setFile("$base/thermal_zone0/type", "cpu-0\n")
        fakeBackend.setFile("$base/thermal_zone0/temp", "45000\n")

        fakeBackend.setFile("$base/thermal_zone1/type", "cpu-prime\n")
        fakeBackend.setFile("$base/thermal_zone1/temp", "72000\n")

        val snapshot = engine.sampleThermals()
        assertNotNull(snapshot.hottestZone)
        assertEquals("cpu-prime", snapshot.hottestZone?.name)
        assertEquals(72.0f, snapshot.hottestZone?.tempC ?: 0f, 0.01f)
    }

    @Test
    fun sampleThermals_whenTempExceedsThreshold_setsIsThrottling() = runTest(testDispatcher) {
        val base = "/sys/class/thermal"
        fakeBackend.setFile("$base/thermal_zone0/type", "cpu-0\n")
        fakeBackend.setFile("$base/thermal_zone0/temp", "78000\n") // 78.0 C > 75.0 C CPU threshold

        val snapshot = engine.sampleThermals()
        assertTrue(snapshot.zones.first().isThrottling)
        assertTrue(snapshot.isThrottling)
    }
}
