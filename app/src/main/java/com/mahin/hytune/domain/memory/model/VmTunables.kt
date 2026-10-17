package com.mahin.hytune.domain.memory.model

data class VmTunables(
    val swappiness: Int,
    val vfsCachePressure: Int,
    val dirtyRatio: Int,
    val dirtyBackgroundRatio: Int,
    val extraFreeKbytes: Long? = null
)
