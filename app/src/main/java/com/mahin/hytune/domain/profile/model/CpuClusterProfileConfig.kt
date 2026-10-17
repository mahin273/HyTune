package com.mahin.hytune.domain.profile.model

data class CpuClusterProfileConfig(
    val clusterId: Int,
    val governor: String? = null,
    val minFreqTarget: FrequencyTarget? = null,
    val maxFreqTarget: FrequencyTarget? = null,
    val coreOnlineStates: Map<Int, Boolean>? = null
)
