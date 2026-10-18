package com.mahin.hytune.domain.engine.model

import kotlinx.serialization.Serializable

@Serializable
data class HardwareStateSnapshot(
    val timestampEpochMs: Long = System.currentTimeMillis(),
    val cpuGovernors: Map<Int, String> = emptyMap(),
    val cpuMinFreqsKHz: Map<Int, Long> = emptyMap(),
    val cpuMaxFreqsKHz: Map<Int, Long> = emptyMap(),
    val cpuCoreStates: Map<Int, Boolean> = emptyMap(),
    val gpuGovernor: String? = null,
    val gpuMinFreqKHz: Long? = null,
    val gpuMaxFreqKHz: Long? = null,
    val swappiness: Int? = null,
    val vfsCachePressure: Int? = null,
    val storageSchedulers: Map<String, String> = emptyMap(),
    val storageReadAheadKb: Map<String, Int> = emptyMap()
)
