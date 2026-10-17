package com.mahin.hytune.domain.cpu.model

import com.mahin.hytune.domain.probe.model.ClusterType

data class CpuClusterPolicy(
    val clusterId: Int,
    val policyPath: String,
    val affectedCores: List<Int>,
    val type: ClusterType = ClusterType.UNKNOWN,
    val availableGovernors: List<String>,
    val currentGovernor: String,
    val availableFrequenciesKHz: List<Long>,
    val minFreqKHz: Long,
    val maxFreqKHz: Long,
    val curFreqKHz: Long,
    val governorTunables: Map<String, String> = emptyMap()
)
