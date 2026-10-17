package com.mahin.hytune.ui.gpu

import com.mahin.hytune.domain.gpu.model.GpuPolicy

data class GpuUiState(
    val isLoading: Boolean = true,
    val isApplying: Boolean = false,
    val policy: GpuPolicy? = null,
    val stagedGovernor: String? = null,
    val stagedMinFreqKHz: Long? = null,
    val stagedMaxFreqKHz: Long? = null,
    val stagedMinPwrlevel: Int? = null,
    val stagedMaxPwrlevel: Int? = null,
    val liveFreqKHz: Long? = null,
    val userMessage: String? = null
) {
    fun isDirty(): Boolean {
        val p = policy ?: return false
        val govDirty = stagedGovernor != null && stagedGovernor != p.currentGovernor
        val minDirty = stagedMinFreqKHz != null && stagedMinFreqKHz != p.minFreqKHz
        val maxDirty = stagedMaxFreqKHz != null && stagedMaxFreqKHz != p.maxFreqKHz
        val pwrMinDirty = stagedMinPwrlevel != null && stagedMinPwrlevel != p.powerLevelInfo?.minPwrlevel
        val pwrMaxDirty = stagedMaxPwrlevel != null && stagedMaxPwrlevel != p.powerLevelInfo?.maxPwrlevel
        return govDirty || minDirty || maxDirty || pwrMinDirty || pwrMaxDirty
    }
}
