package com.mahin.hytune.domain.gpu.model

import com.mahin.hytune.domain.probe.model.GpuVendor

data class GpuPolicy(
    val vendor: GpuVendor,
    val basePath: String,
    val availableFrequenciesKHz: List<Long>,
    val minFreqKHz: Long,
    val maxFreqKHz: Long,
    val curFreqKHz: Long,
    val currentGovernor: String,
    val availableGovernors: List<String>,
    val powerLevelInfo: GpuPowerLevelInfo? = null,
    val idleTimerMs: Int? = null
)
