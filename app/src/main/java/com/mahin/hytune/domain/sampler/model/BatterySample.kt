package com.mahin.hytune.domain.sampler.model

data class BatterySample(
    val percent: Int,
    val temperatureC: Float,
    val voltageV: Float,
    val currentAmperes: Float,
    val powerWatts: Float
)
