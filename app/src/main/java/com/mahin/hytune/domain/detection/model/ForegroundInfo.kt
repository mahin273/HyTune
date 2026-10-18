package com.mahin.hytune.domain.detection.model

data class ForegroundInfo(
    val packageName: String,
    val className: String? = null,
    val source: DetectionSource = DetectionSource.UNKNOWN,
    val isHomeLauncher: Boolean = false,
    val timestampEpochMs: Long = System.currentTimeMillis()
)
