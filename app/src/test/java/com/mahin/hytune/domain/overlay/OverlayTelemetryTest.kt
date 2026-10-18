package com.mahin.hytune.domain.overlay

import com.mahin.hytune.domain.overlay.model.OverlayConfig
import com.mahin.hytune.domain.overlay.model.OverlayTelemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayTelemetryTest {

    @Test
    fun toCompactText_formatsAllVisibleMetrics() {
        val config = OverlayConfig(
            showCpu = true,
            showGpu = true,
            showBattery = true,
            showTemp = true,
            showRam = true
        )
        val telemetry = OverlayTelemetry(
            cpuLoadPercent = 35,
            cpuFreqMhz = 2400,
            gpuLoadPercent = 60,
            gpuFreqMhz = 650,
            ramUsedMb = 3200,
            ramTotalMb = 7800,
            batteryPowerWatts = 4.5f,
            temperatureC = 41.2f,
            isThrottling = false
        )

        val compactText = telemetry.toCompactText(config)
        assertTrue(compactText.contains("CPU 2400MHz"))
        assertTrue(compactText.contains("GPU 650MHz"))
        assertTrue(compactText.contains("41 C"))
        assertTrue(compactText.contains("4.5W"))
        assertTrue(compactText.contains("RAM 3200/7800MB"))
        assertFalse(compactText.contains("THROTTLE"))
    }

    @Test
    fun toCompactText_whenThrottling_includesThrottleBadge() {
        val config = OverlayConfig()
        val telemetry = OverlayTelemetry(
            cpuLoadPercent = 80,
            cpuFreqMhz = 1200,
            temperatureC = 82f,
            isThrottling = true
        )

        val compactText = telemetry.toCompactText(config)
        assertTrue(compactText.contains("THROTTLE"))
    }

    @Test
    fun toCompactText_whenAllMetricsDisabled_returnsDefaultHud() {
        val config = OverlayConfig(
            showCpu = false,
            showGpu = false,
            showBattery = false,
            showTemp = false,
            showRam = false
        )
        val telemetry = OverlayTelemetry()
        val compactText = telemetry.toCompactText(config)
        assertEquals("HyTune HUD", compactText)
    }
}
