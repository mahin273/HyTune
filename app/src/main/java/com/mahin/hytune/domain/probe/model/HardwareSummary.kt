package com.mahin.hytune.domain.probe.model

data class HardwareSummary(
    val socName: String,
    val totalCores: Int,
    val clusters: List<CpuClusterInfo>,
    val gpu: GpuInfo?
)
