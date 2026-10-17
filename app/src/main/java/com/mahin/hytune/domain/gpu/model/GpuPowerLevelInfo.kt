package com.mahin.hytune.domain.gpu.model

data class GpuPowerLevelInfo(
    val numPwrlevels: Int,
    val minPwrlevel: Int,
    val maxPwrlevel: Int,
    val defaultPwrlevel: Int? = null
)
