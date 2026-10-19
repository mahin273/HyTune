package com.mahin.hytune.domain.fps.model

data class FrameMetricsSnapshot(
    val fps: Double = 0.0,
    val targetFps: Int = 60,
    val frameTimeMs: Double = 0.0,
    val jankCount: Int = 0,
    val totalFrames: Int = 0,
    val jankPercentage: Double = 0.0,
    val refreshPeriodNs: Long = 16_666_666L,
    val source: FpsSource = FpsSource.NONE,
    val packageName: String = "",
    val timestampEpochMs: Long = System.currentTimeMillis()
)
