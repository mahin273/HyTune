package com.mahin.hytune.domain.thermal.model

data class ThermalZoneInfo(
    val id: Int,
    val name: String,
    val tempC: Float,
    val type: ThermalZoneType,
    val isThrottling: Boolean = false
)
