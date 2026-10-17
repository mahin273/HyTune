package com.mahin.hytune.ui.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mahin.hytune.domain.memory.MemoryControlEngine
import com.mahin.hytune.domain.memory.model.DropCacheType
import com.mahin.hytune.domain.sampler.SystemSamplerEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val memoryControlEngine: MemoryControlEngine,
    private val samplerEngine: SystemSamplerEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemoryUiState())
    val uiState: StateFlow<MemoryUiState> = _uiState.asStateFlow()

    private var samplingJob: Job? = null

    init {
        loadMemoryState()
        startSampling()
    }

    fun loadMemoryState() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val tunables = memoryControlEngine.getVmTunables().getOrNull()
            val zram = memoryControlEngine.getZramInfo().getOrNull()

            _uiState.update {
                it.copy(
                    isLoading = false,
                    vmTunables = tunables,
                    zramInfo = zram,
                    stagedSwappiness = tunables?.swappiness,
                    stagedVfsCachePressure = tunables?.vfsCachePressure,
                    stagedZramSizeBytes = zram?.disksizeBytes,
                    stagedAlgorithm = zram?.activeAlgorithm
                )
            }
        }
    }

    fun startSampling(intervalMs: Long = 2000L) {
        samplingJob?.cancel()
        samplingJob = viewModelScope.launch {
            samplerEngine.sampleStream(intervalMs).collect {
                val zram = memoryControlEngine.getZramInfo().getOrNull()
                if (zram != null) {
                    _uiState.update { current ->
                        current.copy(zramInfo = zram)
                    }
                }
            }
        }
    }

    fun stopSampling() {
        samplingJob?.cancel()
        samplingJob = null
    }

    fun onSwappinessChanged(value: Int) {
        _uiState.update { it.copy(stagedSwappiness = value) }
    }

    fun onVfsCachePressureChanged(value: Int) {
        _uiState.update { it.copy(stagedVfsCachePressure = value) }
    }

    fun onZramSizeChanged(sizeBytes: Long) {
        _uiState.update { it.copy(stagedZramSizeBytes = sizeBytes) }
    }

    fun onAlgorithmChanged(algorithm: String) {
        _uiState.update { it.copy(stagedAlgorithm = algorithm) }
    }

    fun applyMemorySettings() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isApplying = true, errorMessage = null) }

            state.stagedSwappiness?.let { swappiness ->
                if (swappiness != state.vmTunables?.swappiness) {
                    val res = memoryControlEngine.setSwappiness(swappiness)
                    if (res.isFailure) {
                        _uiState.update {
                            it.copy(
                                isApplying = false,
                                errorMessage = "Failed to set swappiness: ${res.exceptionOrNull()?.message}"
                            )
                        }
                        return@launch
                    }
                }
            }

            state.stagedVfsCachePressure?.let { cachePressure ->
                if (cachePressure != state.vmTunables?.vfsCachePressure) {
                    val res = memoryControlEngine.setVfsCachePressure(cachePressure)
                    if (res.isFailure) {
                        _uiState.update {
                            it.copy(
                                isApplying = false,
                                errorMessage = "Failed to set VFS cache pressure: ${res.exceptionOrNull()?.message}"
                            )
                        }
                        return@launch
                    }
                }
            }

            val targetSize = state.stagedZramSizeBytes
            val targetAlgo = state.stagedAlgorithm
            val sizeChanged = targetSize != null && targetSize != state.zramInfo?.disksizeBytes
            val algoChanged = targetAlgo != null && targetAlgo != state.zramInfo?.activeAlgorithm

            if (sizeChanged || algoChanged) {
                val newSize = targetSize ?: (state.zramInfo?.disksizeBytes ?: 0L)
                val newAlgo = if (algoChanged) targetAlgo else null
                val resizeRes = memoryControlEngine.resizeZram(newSize, newAlgo)
                if (resizeRes.isFailure) {
                    _uiState.update {
                        it.copy(
                            isApplying = false,
                            errorMessage = "Failed to reconfigure ZRAM: ${resizeRes.exceptionOrNull()?.message}"
                        )
                    }
                    return@launch
                }
            }

            val freshTunables = memoryControlEngine.getVmTunables().getOrNull()
            val freshZram = memoryControlEngine.getZramInfo().getOrNull()

            _uiState.update {
                it.copy(
                    isApplying = false,
                    vmTunables = freshTunables,
                    zramInfo = freshZram,
                    stagedSwappiness = freshTunables?.swappiness,
                    stagedVfsCachePressure = freshTunables?.vfsCachePressure,
                    stagedZramSizeBytes = freshZram?.disksizeBytes,
                    stagedAlgorithm = freshZram?.activeAlgorithm,
                    userMessage = "Memory settings applied successfully"
                )
            }
        }
    }

    fun trimCaches(type: DropCacheType = DropCacheType.ALL) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTrimming = true, errorMessage = null) }
            val res = memoryControlEngine.dropCaches(type)
            if (res.isSuccess) {
                _uiState.update {
                    it.copy(
                        isTrimming = false,
                        userMessage = "Page caches dropped successfully"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isTrimming = false,
                        errorMessage = "Failed to drop caches: ${res.exceptionOrNull()?.message}"
                    )
                }
            }
        }
    }

    fun compactZram() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCompacting = true, errorMessage = null) }
            val res = memoryControlEngine.compactZram()
            if (res.isSuccess) {
                val freshZram = memoryControlEngine.getZramInfo().getOrNull()
                _uiState.update {
                    it.copy(
                        isCompacting = false,
                        zramInfo = freshZram ?: it.zramInfo,
                        userMessage = "ZRAM compaction triggered successfully"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isCompacting = false,
                        errorMessage = "Failed to compact ZRAM: ${res.exceptionOrNull()?.message}"
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

    override fun onCleared() {
        super.onCleared()
        stopSampling()
    }
}
