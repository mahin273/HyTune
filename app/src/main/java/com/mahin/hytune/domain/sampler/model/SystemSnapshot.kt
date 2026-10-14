package com.mahin.hytune.domain.sampler.model

data class SystemSnapshot(
    val timestampEpochMs: Long,
    val cpu: CpuSample,
    val memory: MemorySample,
    val thermals: ThermalSample,
    val battery: BatterySample?
)
