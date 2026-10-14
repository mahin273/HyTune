package com.mahin.hytune.domain.probe.model

data class CpuClusterInfo(
    val clusterId: Int,
    val policyPath: String,
    val coreIds: List<Int>,
    val type: ClusterType,
    val availableFrequenciesKHz: List<Long>,
    val minFreqKHz: Long,
    val maxFreqKHz: Long
)
