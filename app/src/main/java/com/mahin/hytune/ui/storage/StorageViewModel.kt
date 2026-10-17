package com.mahin.hytune.ui.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mahin.hytune.domain.storage.StorageControlEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StorageViewModel @Inject constructor(
    private val storageControlEngine: StorageControlEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(StorageUiState())
    val uiState: StateFlow<StorageUiState> = _uiState.asStateFlow()

    init {
        loadStorageInfo()
    }

    fun loadStorageInfo() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val devicesResult = storageControlEngine.getBlockDevices()
            val devices = devicesResult.getOrDefault(emptyList())

            val stagedSched = devices.associate { it.name to it.currentScheduler }
            val stagedRead = devices.associate { it.name to it.readAheadKb }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    blockDevices = devices,
                    stagedSchedulers = stagedSched,
                    stagedReadAhead = stagedRead
                )
            }
        }
    }

    fun onSchedulerChanged(deviceName: String, scheduler: String) {
        _uiState.update {
            it.copy(stagedSchedulers = it.stagedSchedulers + (deviceName to scheduler))
        }
    }

    fun onReadAheadChanged(deviceName: String, readAheadKb: Int) {
        _uiState.update {
            it.copy(stagedReadAhead = it.stagedReadAhead + (deviceName to readAheadKb))
        }
    }

    fun applyStorageSettings(targetDeviceName: String? = null) {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isApplying = true, errorMessage = null) }

            val devicesToApply = if (targetDeviceName != null) {
                state.blockDevices.filter { it.name == targetDeviceName }
            } else {
                state.blockDevices
            }

            for (dev in devicesToApply) {
                val stagedSched = state.stagedSchedulers[dev.name]
                if (stagedSched != null && stagedSched != dev.currentScheduler) {
                    val schedRes = storageControlEngine.setScheduler(dev.name, stagedSched)
                    if (schedRes.isFailure) {
                        _uiState.update {
                            it.copy(
                                isApplying = false,
                                errorMessage = "Failed to set scheduler for ${dev.name}: ${schedRes.exceptionOrNull()?.message}"
                            )
                        }
                        return@launch
                    }
                }

                val stagedRead = state.stagedReadAhead[dev.name]
                if (stagedRead != null && stagedRead != dev.readAheadKb) {
                    val readRes = storageControlEngine.setReadAhead(dev.name, stagedRead)
                    if (readRes.isFailure) {
                        _uiState.update {
                            it.copy(
                                isApplying = false,
                                errorMessage = "Failed to set read-ahead for ${dev.name}: ${readRes.exceptionOrNull()?.message}"
                            )
                        }
                        return@launch
                    }
                }
            }

            // Reload fresh state
            val freshDevices = storageControlEngine.getBlockDevices().getOrDefault(emptyList())
            val freshSched = freshDevices.associate { it.name to it.currentScheduler }
            val freshRead = freshDevices.associate { it.name to it.readAheadKb }

            _uiState.update {
                it.copy(
                    isApplying = false,
                    blockDevices = freshDevices,
                    stagedSchedulers = freshSched,
                    stagedReadAhead = freshRead,
                    userMessage = if (targetDeviceName != null) {
                        "Storage settings for $targetDeviceName applied successfully"
                    } else {
                        "All storage settings applied successfully"
                    }
                )
            }
        }
    }

    fun resetStorageSettings(targetDeviceName: String? = null) {
        val state = _uiState.value
        if (targetDeviceName != null) {
            val dev = state.blockDevices.find { it.name == targetDeviceName } ?: return
            _uiState.update {
                it.copy(
                    stagedSchedulers = it.stagedSchedulers + (targetDeviceName to dev.currentScheduler),
                    stagedReadAhead = it.stagedReadAhead + (targetDeviceName to dev.readAheadKb)
                )
            }
        } else {
            val defaultSched = state.blockDevices.associate { it.name to it.currentScheduler }
            val defaultRead = state.blockDevices.associate { it.name to it.readAheadKb }
            _uiState.update {
                it.copy(
                    stagedSchedulers = defaultSched,
                    stagedReadAhead = defaultRead
                )
            }
        }
    }

    fun runTrim(mountPoint: String = "/data") {
        viewModelScope.launch {
            _uiState.update { it.copy(isTrimming = true, errorMessage = null) }
            val trimRes = storageControlEngine.runFsTrim(mountPoint)
            if (trimRes.isSuccess) {
                val result = trimRes.getOrThrow()
                _uiState.update {
                    it.copy(
                        isTrimming = false,
                        fstrimResult = result,
                        userMessage = "TRIM complete: ${formatBytes(result.trimmedBytes)} trimmed on $mountPoint"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isTrimming = false,
                        errorMessage = "TRIM failed: ${trimRes.exceptionOrNull()?.message}"
                    )
                }
            }
        }
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 bytes"
        val mb = bytes / (1024L * 1024L)
        return if (mb >= 1024L) {
            String.format(java.util.Locale.US, "%.2f GB", mb / 1024.0)
        } else {
            "$mb MB"
        }
    }
}
