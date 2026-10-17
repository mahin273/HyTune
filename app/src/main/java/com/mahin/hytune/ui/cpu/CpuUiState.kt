package com.mahin.hytune.ui.cpu

import com.mahin.hytune.domain.cpu.model.CpuClusterPolicy
import com.mahin.hytune.domain.cpu.model.CpuCoreState

data class CpuUiState(
    val isLoading: Boolean = true,
    val isApplying: Boolean = false,
    val clusterPolicies: List<CpuClusterPolicy> = emptyList(),
    val coreStates: List<CpuCoreState> = emptyList(),
    val stagedGovernors: Map<Int, String> = emptyMap(),
    val stagedMinFreqs: Map<Int, Long> = emptyMap(),
    val stagedMaxFreqs: Map<Int, Long> = emptyMap(),
    val liveCoreFrequencies: Map<Int, Long> = emptyMap(),
    val userMessage: String? = null
) {
    fun isDirty(clusterId: Int): Boolean {
        val policy = clusterPolicies.find { it.clusterId == clusterId } ?: return false
        val stagedGov = stagedGovernors[clusterId] ?: policy.currentGovernor
        val stagedMin = stagedMinFreqs[clusterId] ?: policy.minFreqKHz
        val stagedMax = stagedMaxFreqs[clusterId] ?: policy.maxFreqKHz
        return stagedGov != policy.currentGovernor || stagedMin != policy.minFreqKHz || stagedMax != policy.maxFreqKHz
    }
}
