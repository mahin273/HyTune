package com.mahin.hytune.ui.storage

import com.mahin.hytune.domain.storage.model.BlockDeviceQueue
import com.mahin.hytune.domain.storage.model.FsTrimResult

data class StorageUiState(
    val isLoading: Boolean = false,
    val isApplying: Boolean = false,
    val isTrimming: Boolean = false,
    val blockDevices: List<BlockDeviceQueue> = emptyList(),
    val stagedSchedulers: Map<String, String> = emptyMap(),
    val stagedReadAhead: Map<String, Int> = emptyMap(),
    val fstrimResult: FsTrimResult? = null,
    val userMessage: String? = null,
    val errorMessage: String? = null
) {
    fun isDirty(deviceName: String? = null): Boolean {
        if (deviceName != null) {
            val dev = blockDevices.find { it.name == deviceName } ?: return false
            val schedDirty = stagedSchedulers[deviceName]?.let { it != dev.currentScheduler } ?: false
            val readAheadDirty = stagedReadAhead[deviceName]?.let { it != dev.readAheadKb } ?: false
            return schedDirty || readAheadDirty
        }
        return blockDevices.any { dev ->
            val schedDirty = stagedSchedulers[dev.name]?.let { it != dev.currentScheduler } ?: false
            val readAheadDirty = stagedReadAhead[dev.name]?.let { it != dev.readAheadKb } ?: false
            schedDirty || readAheadDirty
        }
    }
}
