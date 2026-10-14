package com.mahin.hytune.data.local.datastore

data class AppSettings(
    val activeProfileId: String = "balance",
    val temperatureUnitCelsius: Boolean = true,
    val floatingMonitorEnabled: Boolean = false,
    val samplingIntervalMs: Long = 1000L,
    val safetyRollbackTimeoutSec: Int = 15,
    val perAppProfilesEnabled: Boolean = false,
    val dynamicThemeEnabled: Boolean = true
)
