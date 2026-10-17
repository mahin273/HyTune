package com.mahin.hytune.domain.profile.model

data class MemoryProfileConfig(
    val swappiness: Int? = null,
    val vfsCachePressure: Int? = null,
    val zramSizeBytes: Long? = null,
    val zramAlgorithm: String? = null,
    val dropCachesBeforeApply: Boolean = false
)
