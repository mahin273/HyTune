package com.mahin.hytune.domain.sampler.model

data class ThermalSample(
    val socTempC: Float,
    val batteryTempC: Float,
    val maxTempC: Float,
    val zones: Map<String, Float> = emptyMap()
)
