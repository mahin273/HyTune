package com.mahin.hytune.domain.appmanager.model

data class AppSummary(
    val packageName: String,
    val appLabel: String,
    val versionName: String,
    val versionCode: Long,
    val isSystemApp: Boolean,
    val isUpdatedSystemApp: Boolean,
    val isEnabled: Boolean,
    val isSuspended: Boolean,
    val isGame: Boolean,
    val installTimeEpochMs: Long,
    val updateTimeEpochMs: Long,
    val targetSdk: Int,
    val minSdk: Int,
    val uid: Int
)
