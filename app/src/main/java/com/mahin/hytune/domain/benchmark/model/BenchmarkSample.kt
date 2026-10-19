package com.mahin.hytune.domain.benchmark.model

import kotlinx.serialization.Serializable

@Serializable
data class BenchmarkSample(
    val timestampEpochMs: Long,
    val relativeTimeSec: Int,
    val fps: Double,
    val frameTimeMs: Double,
    val cpuLoadPercent: Int,
    val cpuFreqMhz: Int,
    val gpuLoadPercent: Int,
    val gpuFreqMhz: Int,
    val batteryPowerWatts: Float,
    val temperatureC: Float,
    val ramUsedMb: Int
)
