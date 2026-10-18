package com.mahin.hytune.domain.thermal.model

data class ThermalThrottleEvent(
    val id: Long = 0,
    val timestampEpochMs: Long,
    val zoneName: String,
    val temperatureC: Float,
    val coolingDeviceState: String,
    val triggerReason: String
)
