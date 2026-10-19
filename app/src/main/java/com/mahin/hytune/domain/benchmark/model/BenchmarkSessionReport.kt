package com.mahin.hytune.domain.benchmark.model

data class BenchmarkSessionReport(
    val id: Long = 0L,
    val packageName: String,
    val appName: String,
    val startTimeEpochMs: Long,
    val endTimeEpochMs: Long,
    val durationSeconds: Int,
    val avgFps: Double,
    val onePercentLowFps: Double,
    val pointOnePercentLowFps: Double,
    val minFps: Double,
    val maxFps: Double,
    val totalFrames: Int,
    val jankCount: Int,
    val jankPercentage: Double,
    val stutterScore: Double,
    val avgFrameTimeMs: Double,
    val avgCpuLoadPercent: Int,
    val avgGpuLoadPercent: Int,
    val avgBatteryWatts: Float,
    val peakTemperatureC: Float,
    val histogram: List<BenchmarkHistogramBin> = emptyList(),
    val telemetrySeries: List<BenchmarkSample> = emptyList()
)
