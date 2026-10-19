package com.mahin.hytune.domain.diagnostics.engine

import com.mahin.hytune.domain.diagnostics.model.DeadPixelColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DisplayDiagnosticsEngineTest {

    private lateinit var engine: DisplayDiagnosticsEngine

    @Before
    fun setUp() {
        engine = DisplayDiagnosticsEngine()
    }

    @Test
    fun colorCycle_progressesThroughAllPrimaryColors() {
        assertEquals(DeadPixelColor.RED, engine.currentColor)

        assertEquals(DeadPixelColor.GREEN, engine.nextColor())
        assertEquals(DeadPixelColor.BLUE, engine.nextColor())
        assertEquals(DeadPixelColor.WHITE, engine.nextColor())
        assertEquals(DeadPixelColor.BLACK, engine.nextColor())
        assertEquals(DeadPixelColor.RED, engine.nextColor())

        // Previous color
        assertEquals(DeadPixelColor.BLACK, engine.previousColor())
        assertEquals(DeadPixelColor.WHITE, engine.previousColor())

        // Reset
        assertEquals(DeadPixelColor.RED, engine.resetColor())
    }

    @Test
    fun recordTouchEvent_calculatesSamplingRateAndJitter() {
        val initial = engine.recordTouchEvent(1_000_000_000L)
        assertEquals(1, initial.sampleCount)
        assertEquals(0f, initial.touchPollingRateHz, 0.01f)

        // Simulate 120Hz events (~8.333ms intervals, 8_333_333 nanos)
        var currentTimeNanos = 1_000_000_000L
        val intervalNanos = 8_333_333L

        var lastMetrics = initial
        for (i in 1..20) {
            currentTimeNanos += intervalNanos
            lastMetrics = engine.recordTouchEvent(currentTimeNanos, pointerCount = 2)
        }

        assertEquals(21, lastMetrics.sampleCount)
        assertEquals(2, lastMetrics.pointerCount)
        // 8.33ms -> ~120 Hz
        assertTrue("Expected ~120Hz polling rate, got ${lastMetrics.touchPollingRateHz}", lastMetrics.touchPollingRateHz in 118f..122f)
        assertTrue("Expected very low jitter for periodic input, got ${lastMetrics.jitterMs}", lastMetrics.jitterMs < 0.1f)

        // Test reset
        engine.resetTouchMetrics()
        val afterReset = engine.recordTouchEvent(2_000_000_000L)
        assertEquals(1, afterReset.sampleCount)
        assertEquals(0f, afterReset.touchPollingRateHz, 0.01f)
    }

    @Test
    fun recordTouchEvent_ignoresDuplicateAndNegativeDeltas() {
        engine.recordTouchEvent(1_000_000_000L)
        // Same timestamp
        val dupMetrics = engine.recordTouchEvent(1_000_000_000L)
        assertEquals(0f, dupMetrics.touchPollingRateHz, 0.01f)

        // Negative delta (clock anomaly)
        val anomalyMetrics = engine.recordTouchEvent(999_000_000L)
        assertEquals(0f, anomalyMetrics.touchPollingRateHz, 0.01f)
    }
}
