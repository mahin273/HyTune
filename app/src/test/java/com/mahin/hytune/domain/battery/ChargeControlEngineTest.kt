package com.mahin.hytune.domain.battery

import com.mahin.hytune.core.privilege.Capability
import com.mahin.hytune.core.privilege.FakePrivilegeBackend
import com.mahin.hytune.core.privilege.PrivilegeTier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChargeControlEngineTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeBackend: FakePrivilegeBackend
    private lateinit var engine: ChargeControlEngine

    @Before
    fun setUp() {
        fakeBackend = FakePrivilegeBackend(
            initialTier = PrivilegeTier.ROOT,
            initialCapabilities = Capability.entries.toSet()
        )
        engine = ChargeControlEngine(fakeBackend, testDispatcher)
    }

    @Test
    fun getStatus_whenRootAndNodesExist_returnsSupportedAndParsesValues() = runTest(testDispatcher) {
        val chargingNode = "/sys/class/power_supply/battery/charging_enabled"
        val limitNode = "/sys/class/power_supply/battery/constant_charge_current_max"

        fakeBackend.setFile(chargingNode, "1\n")
        fakeBackend.setFile(limitNode, "1500000\n")

        val status = engine.getStatus()
        assertTrue(status.isSupported)
        assertTrue(status.isChargingEnabled)
        assertFalse(status.isBypassActive)
        assertEquals(1500, status.currentLimitMa)
        assertEquals(chargingNode, status.activeChargingNode)
        assertEquals(limitNode, status.activeLimitNode)
    }

    @Test
    fun getStatus_whenNoNodesFound_returnsUnsupported() = runTest(testDispatcher) {
        val status = engine.getStatus()
        assertFalse(status.isSupported)
        assertTrue(status.isChargingEnabled)
        assertEquals(0, status.currentLimitMa)
    }

    @Test
    fun setBypassCharging_whenRoot_writesZeroToChargingEnabled() = runTest(testDispatcher) {
        val node = "/sys/class/power_supply/battery/charging_enabled"
        fakeBackend.setFile(node, "1\n")

        val result = engine.setBypassCharging(true)
        assertTrue(result.isSuccess)
        assertEquals("0", fakeBackend.getFile(node))

        val disableBypass = engine.setBypassCharging(false)
        assertTrue(disableBypass.isSuccess)
        assertEquals("1", fakeBackend.getFile(node))
    }

    @Test
    fun setBypassCharging_withInputSuspend_writesInvertedValues() = runTest(testDispatcher) {
        val node = "/sys/class/power_supply/battery/input_suspend"
        fakeBackend.setFile(node, "0\n")

        val result = engine.setBypassCharging(true)
        assertTrue(result.isSuccess)
        assertEquals("1", fakeBackend.getFile(node))

        val disableBypass = engine.setBypassCharging(false)
        assertTrue(disableBypass.isSuccess)
        assertEquals("0", fakeBackend.getFile(node))
    }

    @Test
    fun setChargeCurrentLimit_convertsToMicroamps() = runTest(testDispatcher) {
        val limitNode = "/sys/class/power_supply/battery/constant_charge_current_max"
        fakeBackend.setFile(limitNode, "2000000\n")

        val result = engine.setChargeCurrentLimit(1200)
        assertTrue(result.isSuccess)
        assertEquals("1200000", fakeBackend.getFile(limitNode))

        val resetResult = engine.setChargeCurrentLimit(0)
        assertTrue(resetResult.isSuccess)
        assertEquals("0", fakeBackend.getFile(limitNode))
    }

    @Test
    fun chargeControl_whenNonRoot_returnsFailure() = runTest(testDispatcher) {
        fakeBackend.tier = PrivilegeTier.SHIZUKU
        val node = "/sys/class/power_supply/battery/charging_enabled"
        fakeBackend.setFile(node, "1\n")

        val bypassResult = engine.setBypassCharging(true)
        assertTrue(bypassResult.isFailure)

        val limitResult = engine.setChargeCurrentLimit(1000)
        assertTrue(limitResult.isFailure)
    }
}
