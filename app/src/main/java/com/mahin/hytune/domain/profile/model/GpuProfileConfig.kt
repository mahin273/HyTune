package com.mahin.hytune.domain.profile.model

data class GpuProfileConfig(
    val governor: String? = null,
    val minFreqTarget: FrequencyTarget? = null,
    val maxFreqTarget: FrequencyTarget? = null,
    val powerLevelTarget: Int? = null
)
