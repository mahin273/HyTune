package com.mahin.hytune.ui.components.scene

import org.junit.Assert.assertEquals
import org.junit.Test

class SceneBottomStatsCardsTest {

    @Test
    fun testFormatBatteryStatsDoesNotThrowDuplicateFormatFlagsException() {
        val result = formatBatteryStats(batteryPercent = 85, voltageV = 4.123f)
        assertEquals("85%  4.1v", result)

        val fullBattery = formatBatteryStats(batteryPercent = 100, voltageV = 4.35f)
        assertEquals("100%  4.3v", fullBattery)

        val lowBattery = formatBatteryStats(batteryPercent = 5, voltageV = 3.68f)
        assertEquals("5%  3.7v", lowBattery)
    }

    @Test
    fun testFormatPowerStats() {
        val charging = formatPowerStats(powerWatts = 18.5f, isCharging = true)
        assertEquals("+18.50W", charging)

        val discharging = formatPowerStats(powerWatts = -3.25f, isCharging = false)
        assertEquals("-3.25W", discharging)
    }

    @Test
    fun testFormatThermalStats() {
        val temp = formatThermalStats(36.78f)
        assertEquals("36.8°C", temp)
    }

    @Test
    fun testFormatUptime() {
        val uptimeMillis = (2 * 3600 + 15 * 60 + 30) * 1000L
        val formatted = formatUptime(uptimeMillis)
        assertEquals("02:15:30", formatted)
    }
}
