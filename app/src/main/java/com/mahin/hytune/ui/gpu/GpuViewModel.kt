package com.mahin.hytune.ui.gpu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mahin.hytune.domain.gpu.GpuControlEngine
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
class GpuViewModel @Inject constructor(
    private val gpuControlEngine: GpuControlEngine,
    private val samplerEngine: SystemSamplerEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(GpuUiState())
    val uiState: StateFlow<GpuUiState> = _uiState.asStateFlow()

    private var samplingJob: Job? = null

    init {
        loadGpuPolicy()
        startSampling()
    }

    fun loadGpuPolicy() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val policyResult = gpuControlEngine.getGpuPolicy()
            val policy = policyResult.getOrNull()

            _uiState.update {
                it.copy(
                    isLoading = false,
                    policy = policy,
                    stagedGovernor = policy?.currentGovernor,
                    stagedMinFreqKHz = policy?.minFreqKHz,
                    stagedMaxFreqKHz = policy?.maxFreqKHz,
                    stagedMinPwrlevel = policy?.powerLevelInfo?.minPwrlevel,
                    stagedMaxPwrlevel = policy?.powerLevelInfo?.maxPwrlevel,
                    liveFreqKHz = policy?.curFreqKHz
                )
            }
        }
    }

    fun startSampling(intervalMs: Long = 1000L) {
        samplingJob?.cancel()
        samplingJob = viewModelScope.launch {
            samplerEngine.sampleStream(intervalMs).collect { _ ->
                // Live poll curFreq from engine
                val refreshed = gpuControlEngine.getGpuPolicy().getOrNull()
                if (refreshed != null) {
                    _uiState.update { it.copy(liveFreqKHz = refreshed.curFreqKHz) }
                }
            }
        }
    }

    fun stopSampling() {
        samplingJob?.cancel()
        samplingJob = null
    }

    fun onGovernorChanged(governor: String) {
        _uiState.update { it.copy(stagedGovernor = governor) }
    }

    fun onMinFreqChanged(minFreqKHz: Long) {
        _uiState.update { state ->
            val curMax = state.stagedMaxFreqKHz ?: minFreqKHz
            val newMax = if (minFreqKHz > curMax) minFreqKHz else curMax
            state.copy(stagedMinFreqKHz = minFreqKHz, stagedMaxFreqKHz = newMax)
        }
    }

    fun onMaxFreqChanged(maxFreqKHz: Long) {
        _uiState.update { state ->
            val curMin = state.stagedMinFreqKHz ?: maxFreqKHz
            val newMin = if (maxFreqKHz < curMin) maxFreqKHz else curMin
            state.copy(stagedMinFreqKHz = newMin, stagedMaxFreqKHz = maxFreqKHz)
        }
    }

    fun onPowerLevelsChanged(minPwrlevel: Int?, maxPwrlevel: Int?) {
        _uiState.update {
            it.copy(stagedMinPwrlevel = minPwrlevel, stagedMaxPwrlevel = maxPwrlevel)
        }
    }

    fun applyGpuSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isApplying = true) }
            val state = _uiState.value
            var error: Throwable? = null

            val gov = state.stagedGovernor
            if (gov != null && gov != state.policy?.currentGovernor) {
                val govResult = gpuControlEngine.setGovernor(gov)
                if (govResult.isFailure) error = govResult.exceptionOrNull()
            }

            val min = state.stagedMinFreqKHz
            val max = state.stagedMaxFreqKHz
            if (error == null && min != null && max != null && (min != state.policy?.minFreqKHz || max != state.policy?.maxFreqKHz)) {
                val freqResult = gpuControlEngine.setFrequencies(min, max)
                if (freqResult.isFailure) error = freqResult.exceptionOrNull()
            }

            val pwrMin = state.stagedMinPwrlevel
            val pwrMax = state.stagedMaxPwrlevel
            if (error == null && (pwrMin != null || pwrMax != null)) {
                val pwrResult = gpuControlEngine.setPowerLevel(pwrMin, pwrMax)
                if (pwrResult.isFailure) error = pwrResult.exceptionOrNull()
            }

            val refreshedPolicy = gpuControlEngine.getGpuPolicy().getOrNull()
            _uiState.update {
                it.copy(
                    isApplying = false,
                    policy = refreshedPolicy,
                    stagedGovernor = refreshedPolicy?.currentGovernor,
                    stagedMinFreqKHz = refreshedPolicy?.minFreqKHz,
                    stagedMaxFreqKHz = refreshedPolicy?.maxFreqKHz,
                    stagedMinPwrlevel = refreshedPolicy?.powerLevelInfo?.minPwrlevel,
                    stagedMaxPwrlevel = refreshedPolicy?.powerLevelInfo?.maxPwrlevel,
                    userMessage = if (error == null) "GPU settings applied successfully" else error.message
                )
            }
        }
    }

    fun resetGpuSettings() {
        val policy = _uiState.value.policy ?: return
        _uiState.update {
            it.copy(
                stagedGovernor = policy.currentGovernor,
                stagedMinFreqKHz = policy.minFreqKHz,
                stagedMaxFreqKHz = policy.maxFreqKHz,
                stagedMinPwrlevel = policy.powerLevelInfo?.minPwrlevel,
                stagedMaxPwrlevel = policy.powerLevelInfo?.maxPwrlevel
            )
        }
    }

    fun userMessageShown() {
        _uiState.update { it.copy(userMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        stopSampling()
    }
}
