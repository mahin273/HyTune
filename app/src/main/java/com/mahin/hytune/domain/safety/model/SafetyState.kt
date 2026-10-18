package com.mahin.hytune.domain.safety.model

data class SafetyState(
    val bootloopCrashCount: Int = 0,
    val isSafeRecoveryModeActive: Boolean = false,
    val isStabilityWindowActive: Boolean = false,
    val hasBaselineSnapshot: Boolean = false,
    val dryRunPreviewEnabled: Boolean = true
)
