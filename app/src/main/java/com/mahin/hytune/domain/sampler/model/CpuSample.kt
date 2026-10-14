package com.mahin.hytune.domain.sampler.model

data class CpuSample(
    val overallUsagePercent: Float,
    val coreFrequenciesKHz: Map<Int, Long> = emptyMap()
)
