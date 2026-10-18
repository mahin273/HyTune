package com.mahin.hytune.data.local.datastore

data class AppSettings(
    val activeProfileId: String = "balance",
    val temperatureUnitCelsius: Boolean = true,
    val floatingMonitorEnabled: Boolean = false,
    val samplingIntervalMs: Long = 1000L,
    val safetyRollbackTimeoutSec: Int = 15,
    val perAppProfilesEnabled: Boolean = false,
    val dynamicThemeEnabled: Boolean = true,
    val bootloopCrashCount: Int = 0,
    val lastApplyTimestampEpochMs: Long = 0L,
    val isApplyingSettings: Boolean = false,
    val safeRecoveryModeActive: Boolean = false,
    val dryRunPreviewEnabled: Boolean = true,
    val baselineSnapshotJson: String? = null,
    val gracefulShutdownOccurred: Boolean = false,
    val daemonServiceEnabled: Boolean = false,
    val daemonNotificationDetailsEnabled: Boolean = true
)
