package com.mahin.hytune.domain.battery.model

data class ChargeControlStatus(
    val isSupported: Boolean = false,
    val isChargingEnabled: Boolean = true,
    val isBypassActive: Boolean = false,
    val currentLimitMa: Int = 0,
    val activeChargingNode: String? = null,
    val activeLimitNode: String? = null
)
