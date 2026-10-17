package com.mahin.hytune.domain.profile.model

data class TuningProfile(
    val id: String,
    val name: String,
    val description: String,
    val mode: ProfileMode,
    val isBuiltIn: Boolean,
    val cpuClusters: Map<Int, CpuClusterProfileConfig> = emptyMap(),
    val gpu: GpuProfileConfig? = null,
    val memory: MemoryProfileConfig? = null,
    val storage: StorageProfileConfig? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)
