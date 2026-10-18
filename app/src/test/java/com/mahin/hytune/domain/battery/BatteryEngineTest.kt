package com.mahin.hytune.domain.battery

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mahin.hytune.core.privilege.Capability
import com.mahin.hytune.core.privilege.FakePrivilegeBackend
import com.mahin.hytune.core.privilege.PrivilegeTier
import com.mahin.hytune.domain.battery.model.BatteryHealth
import com.mahin.hytune.domain.battery.model.BatteryStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class BatteryEngineTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var fakeBackend: FakePrivilegeBackend
    private lateinit var engine: BatteryEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        fakeBackend = FakePrivilegeBackend(
            initialTier = PrivilegeTier.ROOT,
            initialCapabilities = Capability.entries.toSet()
        )
        engine = BatteryEngine(
            context = context,
            privilegeBackend = fakeBackend,
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun sample_readsFromSysfsCorrectly() = runTest(testDispatcher) {
        val basePath = "/sys/class/power_supply/battery"
        fakeBackend.setFile("$basePath/capacity", "85")
        fakeBackend.setFile("$basePath/status", "Charging")
        fakeBackend.setFile("$basePath/health", "Good")
        fakeBackend.setFile("$basePath/voltage_now", "4150000") // 4.15V in uV
        fakeBackend.setFile("$basePath/current_now", "2100000") // 2.1A in uA
        fakeBackend.setFile("$basePath/temp", "335") // 33.5C
        fakeBackend.setFile("$basePath/charge_counter", "4200000")
        fakeBackend.setFile("$basePath/technology", "Li-poly")

        val state = engine.sample()

        assertEquals(85, state.levelPercent)
        assertEquals(BatteryStatus.CHARGING, state.status)
        assertEquals(BatteryHealth.GOOD, state.health)
        assertEquals(4150, state.voltageMv)
        assertEquals(2100, state.currentMa)
        assertEquals(33.5f, state.temperatureC, 0.1f)
        assertEquals("Li-poly", state.technology)
        assertEquals(4200000L, state.chargeCounterUah)
        assertTrue(state.isCharging)

        // 4.15V * 2.1A = 8.715W
        assertEquals(8.715f, state.powerWatts, 0.05f)
    }

    @Test
    fun sample_normalizesDischargingCurrent() = runTest(testDispatcher) {
        val basePath = "/sys/class/power_supply/battery"
        fakeBackend.setFile("$basePath/capacity", "60")
        fakeBackend.setFile("$basePath/status", "Discharging")
        fakeBackend.setFile("$basePath/voltage_now", "3850000") // 3.85V
        fakeBackend.setFile("$basePath/current_now", "450000") // 450mA
        fakeBackend.setFile("$basePath/temp", "280")

        val state = engine.sample()

        assertEquals(60, state.levelPercent)
        assertEquals(BatteryStatus.DISCHARGING, state.status)
        assertEquals(-450, state.currentMa) // Discharging should be negative
        assertEquals(28.0f, state.temperatureC, 0.1f)

        // 3.85V * 0.45A = 1.73W
        assertEquals(1.7325f, state.powerWatts, 0.05f)
    }

    @Test
    fun sample_fallsBackToBatteryManagerWhenSysfsEmpty() = runTest(testDispatcher) {
        // No files set in fakeBackend, simulate unprivileged fallback
        fakeBackend.tier = PrivilegeTier.NONE

        val state = engine.sample()

        assertNotNull(state)
        assertTrue(state.levelPercent in 0..100)
        assertTrue(state.voltageMv > 0)
    }
}
