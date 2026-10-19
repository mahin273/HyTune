package com.mahin.hytune.domain.diagnostics.engine

import com.mahin.hytune.domain.diagnostics.model.DeadPixelColor
import com.mahin.hytune.domain.diagnostics.model.TouchDiagnosticMetrics
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class DisplayDiagnosticsEngine @Inject constructor() {

    private val colorCycle = listOf(
        DeadPixelColor.RED,
        DeadPixelColor.GREEN,
        DeadPixelColor.BLUE,
        DeadPixelColor.WHITE,
        DeadPixelColor.BLACK
    )

    private var colorIndex = 0

    val currentColor: DeadPixelColor
        get() = colorCycle[colorIndex]

    fun nextColor(): DeadPixelColor {
        colorIndex = (colorIndex + 1) % colorCycle.size
        return currentColor
    }

    fun previousColor(): DeadPixelColor {
        colorIndex = if (colorIndex > 0) colorIndex - 1 else colorCycle.size - 1
        return currentColor
    }

    fun resetColor(): DeadPixelColor {
        colorIndex = 0
        return currentColor
    }

    private val touchTimestampsNanos = mutableListOf<Long>()
    private val maxTouchWindow = 120

    @Synchronized
    fun recordTouchEvent(timestampNanos: Long, pointerCount: Int = 1): TouchDiagnosticMetrics {
        touchTimestampsNanos.add(timestampNanos)
        if (touchTimestampsNanos.size > maxTouchWindow) {
            touchTimestampsNanos.removeAt(0)
        }

        if (touchTimestampsNanos.size < 2) {
            return TouchDiagnosticMetrics(
                sampleCount = touchTimestampsNanos.size,
                averageIntervalMs = 0f,
                touchPollingRateHz = 0f,
                jitterMs = 0f,
                pointerCount = pointerCount
            )
        }

        val deltasMs = mutableListOf<Float>()
        for (i in 1 until touchTimestampsNanos.size) {
            val deltaMs = (touchTimestampsNanos[i] - touchTimestampsNanos[i - 1]) / 1_000_000f
            if (deltaMs > 0.05f && deltaMs < 1000f) {
                deltasMs.add(deltaMs)
            }
        }

        if (deltasMs.isEmpty()) {
            return TouchDiagnosticMetrics(
                sampleCount = touchTimestampsNanos.size,
                averageIntervalMs = 0f,
                touchPollingRateHz = 0f,
                jitterMs = 0f,
                pointerCount = pointerCount
            )
        }

        val avgIntervalMs = deltasMs.average().toFloat()
        val pollingRateHz = if (avgIntervalMs > 0f) (1000f / avgIntervalMs) else 0f

        val variance = deltasMs.map { (it - avgIntervalMs) * (it - avgIntervalMs) }.average()
        val jitterMs = sqrt(variance).toFloat()

        return TouchDiagnosticMetrics(
            sampleCount = deltasMs.size + 1,
            averageIntervalMs = avgIntervalMs,
            touchPollingRateHz = pollingRateHz,
            jitterMs = jitterMs,
            pointerCount = pointerCount
        )
    }

    @Synchronized
    fun resetTouchMetrics() {
        touchTimestampsNanos.clear()
    }
}
