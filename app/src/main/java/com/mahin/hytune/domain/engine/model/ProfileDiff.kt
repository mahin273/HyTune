package com.mahin.hytune.domain.engine.model

data class CpuClusterDiff(
    val clusterId: Int,
    val targetGovernor: String?,
    val currentGovernor: String,
    val targetMinFreqKHz: Long?,
    val currentMinFreqKHz: Long,
    val targetMaxFreqKHz: Long?,
    val currentMaxFreqKHz: Long
) {
    val hasChanges: Boolean
        get() = (targetGovernor != null && targetGovernor != currentGovernor) ||
                (targetMinFreqKHz != null && targetMinFreqKHz != currentMinFreqKHz) ||
                (targetMaxFreqKHz != null && targetMaxFreqKHz != currentMaxFreqKHz)
}

data class GpuDiff(
    val targetGovernor: String?,
    val currentGovernor: String?,
    val targetMinFreqKHz: Long?,
    val currentMinFreqKHz: Long?,
    val targetMaxFreqKHz: Long?,
    val currentMaxFreqKHz: Long?
) {
    val hasChanges: Boolean
        get() = (targetGovernor != null && targetGovernor != currentGovernor) ||
                (targetMinFreqKHz != null && targetMinFreqKHz != currentMinFreqKHz) ||
                (targetMaxFreqKHz != null && targetMaxFreqKHz != currentMaxFreqKHz)
}

data class MemoryDiff(
    val targetSwappiness: Int?,
    val currentSwappiness: Int?,
    val targetVfsCachePressure: Int?,
    val currentVfsCachePressure: Int?,
    val dropCachesBeforeApply: Boolean
) {
    val hasChanges: Boolean
        get() = (targetSwappiness != null && targetSwappiness != currentSwappiness) ||
                (targetVfsCachePressure != null && targetVfsCachePressure != currentVfsCachePressure) ||
                dropCachesBeforeApply
}

data class StorageDeviceDiff(
    val deviceName: String,
    val targetScheduler: String?,
    val currentScheduler: String,
    val targetReadAheadKb: Int?,
    val currentReadAheadKb: Int
) {
    val hasChanges: Boolean
        get() = (targetScheduler != null && targetScheduler != currentScheduler) ||
                (targetReadAheadKb != null && targetReadAheadKb != currentReadAheadKb)
}

data class ProfileDiff(
    val profileId: String,
    val cpuDiffs: List<CpuClusterDiff> = emptyList(),
    val gpuDiff: GpuDiff? = null,
    val memoryDiff: MemoryDiff? = null,
    val storageDiffs: List<StorageDeviceDiff> = emptyList(),
    val runTrimBeforeApply: Boolean = false
) {
    val hasChanges: Boolean
        get() = cpuDiffs.any { it.hasChanges } ||
                (gpuDiff?.hasChanges == true) ||
                (memoryDiff?.hasChanges == true) ||
                storageDiffs.any { it.hasChanges } ||
                runTrimBeforeApply
}
