package com.mahin.hytune.domain.probe.model

data class GpuInfo(
    val vendor: GpuVendor,
    val basePath: String,
    val availableFrequenciesKHz: List<Long>,
    val currentGovernor: String = "",
    val availableGovernors: List<String> = emptyList(),
    val minFreqKHz: Long = availableFrequenciesKHz.minOrNull() ?: 0L,
    val maxFreqKHz: Long = availableFrequenciesKHz.maxOrNull() ?: 0L
)
