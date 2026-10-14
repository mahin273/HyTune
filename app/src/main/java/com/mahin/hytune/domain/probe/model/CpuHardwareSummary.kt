package com.mahin.hytune.domain.probe.model

data class CpuHardwareSummary(
    val coreCount: Int,
    val hardwareName: String,
    val architecture: String = "",
    val implementer: String = ""
)
