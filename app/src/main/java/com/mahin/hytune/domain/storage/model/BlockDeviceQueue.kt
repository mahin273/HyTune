package com.mahin.hytune.domain.storage.model

data class BlockDeviceQueue(
    val name: String,
    val isRotational: Boolean,
    val currentScheduler: String,
    val availableSchedulers: List<String>,
    val readAheadKb: Int,
    val sizeBytes: Long
)
