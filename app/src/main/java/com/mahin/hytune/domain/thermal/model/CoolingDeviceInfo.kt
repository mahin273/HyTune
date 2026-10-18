package com.mahin.hytune.domain.thermal.model

data class CoolingDeviceInfo(
    val id: Int,
    val name: String,
    val curState: Int,
    val maxState: Int,
    val isThrottling: Boolean = curState > 0
)
