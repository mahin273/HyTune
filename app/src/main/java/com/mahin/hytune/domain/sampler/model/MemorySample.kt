package com.mahin.hytune.domain.sampler.model

data class MemorySample(
    val totalBytes: Long,
    val availableBytes: Long,
    val usedBytes: Long,
    val usagePercent: Float,
    val swapTotalBytes: Long = 0L,
    val swapUsedBytes: Long = 0L,
    val swapUsagePercent: Float = 0f
)
