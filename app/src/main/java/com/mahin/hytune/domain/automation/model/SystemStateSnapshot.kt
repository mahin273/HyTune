package com.mahin.hytune.domain.automation.model

data class SystemStateSnapshot(
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val temperatureC: Float = 25f,
    val screenOn: Boolean = true,
    val foregroundPackage: String = "",
    val timestampEpochMs: Long = System.currentTimeMillis()
)
