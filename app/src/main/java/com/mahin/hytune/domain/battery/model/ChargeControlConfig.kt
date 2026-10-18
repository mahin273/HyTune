package com.mahin.hytune.domain.battery.model

data class ChargeControlConfig(
    val bypassChargingEnabled: Boolean = false,
    val chargeLimitEnabled: Boolean = false,
    val chargeLimitPercent: Int = 80,
    val chargeCurrentLimitMa: Int = 0
)
