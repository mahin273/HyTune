package com.mahin.hytune.ui.cpu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mahin.hytune.domain.cpu.CpuControlEngine
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
class CpuViewModel @Inject constructor(
    private val cpuControlEngine: CpuControlEngine,
    private val samplerEngine: SystemSamplerEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(CpuUiState())
    val uiState: StateFlow<CpuUiState> = _uiState.asStateFlow()

    private var samplingJob: Job? = null

    init {
        loadCpuTopology()
        startSampling()
    }

    fun loadCpuTopology() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val policiesResult = cpuControlEngine.getClusterPolicies()
            val coresResult = cpuControlEngine.getCoreStates()

            val policies = policiesResult.getOrDefault(emptyList())
            val cores = coresResult.getOrDefault(emptyList())

            val stagedGovs = policies.associate { it.clusterId to it.currentGovernor }
            val stagedMins = policies.associate { it.clusterId to it.minFreqKHz }
            val stagedMaxs = policies.associate { it.clusterId to it.maxFreqKHz }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    clusterPolicies = policies,
                    coreStates = cores,
                    stagedGovernors = stagedGovs,
                    stagedMinFreqs = stagedMins,
                    stagedMaxFreqs = stagedMaxs
                )
            }
        }
    }

    fun startSampling(intervalMs: Long = 2000L) {
        samplingJob?.cancel()
        samplingJob = viewModelScope.launch {
            samplerEngine.sampleStream(intervalMs).collect { snapshot ->
                _uiState.update { state ->
                    state.copy(liveCoreFrequencies = snapshot.cpu.coreFrequenciesKHz)
                }
            }
        }
    }

    fun stopSampling() {
        samplingJob?.cancel()
        samplingJob = null
    }

    fun onGovernorChanged(clusterId: Int, governor: String) {
        _uiState.update { state ->
            val updated = state.stagedGovernors.toMutableMap()
            updated[clusterId] = governor
            state.copy(stagedGovernors = updated)
        }
    }

    fun onMinFreqChanged(clusterId: Int, minFreqKHz: Long) {
        _uiState.update { state ->
            val updatedMins = state.stagedMinFreqs.toMutableMap()
            val updatedMaxs = state.stagedMaxFreqs.toMutableMap()

            updatedMins[clusterId] = minFreqKHz
            val currentMax = updatedMaxs[clusterId] ?: minFreqKHz
            if (minFreqKHz > currentMax) {
                updatedMaxs[clusterId] = minFreqKHz
            }

            state.copy(stagedMinFreqs = updatedMins, stagedMaxFreqs = updatedMaxs)
        }
    }

    fun onMaxFreqChanged(clusterId: Int, maxFreqKHz: Long) {
        _uiState.update { state ->
            val updatedMins = state.stagedMinFreqs.toMutableMap()
            val updatedMaxs = state.stagedMaxFreqs.toMutableMap()

            updatedMaxs[clusterId] = maxFreqKHz
            val currentMin = updatedMins[clusterId] ?: maxFreqKHz
            if (maxFreqKHz < currentMin) {
                updatedMins[clusterId] = maxFreqKHz
            }

            state.copy(stagedMinFreqs = updatedMins, stagedMaxFreqs = updatedMaxs)
        }
    }

    fun onCoreOnlineToggled(coreId: Int, online: Boolean) {
        viewModelScope.launch {
            val result = cpuControlEngine.setCoreOnline(coreId, online)
            if (result.isSuccess) {
                val updatedCores = cpuControlEngine.getCoreStates().getOrDefault(emptyList())
                _uiState.update {
                    it.copy(
                        coreStates = updatedCores,
                        userMessage = "Core $coreId is now ${if (online) "online" else "offline"}"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        userMessage = result.exceptionOrNull()?.message
                            ?: "Failed to toggle Core $coreId"
                    )
                }
            }
        }
    }

    fun applyClusterSettings(clusterId: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isApplying = true) }
            val state = _uiState.value
            val targetGov = state.stagedGovernors[clusterId]
            val targetMin = state.stagedMinFreqs[clusterId]
            val targetMax = state.stagedMaxFreqs[clusterId]

            var error: Throwable? = null
            if (targetGov != null) {
                val govResult = cpuControlEngine.setGovernor(clusterId, targetGov)
                if (govResult.isFailure) error = govResult.exceptionOrNull()
            }

            if (error == null && targetMin != null && targetMax != null) {
                val freqResult = cpuControlEngine.setFrequencies(clusterId, targetMin, targetMax)
                if (freqResult.isFailure) error = freqResult.exceptionOrNull()
            }

            val refreshedPolicies = cpuControlEngine.getClusterPolicies().getOrDefault(emptyList())
            _uiState.update {
                it.copy(
                    isApplying = false,
                    clusterPolicies = refreshedPolicies,
                    userMessage = if (error == null) "Cluster $clusterId applied successfully" else error.message
                )
            }
        }
    }

    fun resetClusterSettings(clusterId: Int) {
        val policy = _uiState.value.clusterPolicies.find { it.clusterId == clusterId } ?: return
        _uiState.update { state ->
            val updatedGovs = state.stagedGovernors.toMutableMap()
            val updatedMins = state.stagedMinFreqs.toMutableMap()
            val updatedMaxs = state.stagedMaxFreqs.toMutableMap()

            updatedGovs[clusterId] = policy.currentGovernor
            updatedMins[clusterId] = policy.minFreqKHz
            updatedMaxs[clusterId] = policy.maxFreqKHz

            state.copy(
                stagedGovernors = updatedGovs,
                stagedMinFreqs = updatedMins,
                stagedMaxFreqs = updatedMaxs
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
