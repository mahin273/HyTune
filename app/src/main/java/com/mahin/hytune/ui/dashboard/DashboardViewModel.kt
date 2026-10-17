package com.mahin.hytune.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mahin.hytune.data.local.datastore.AppSettingsRepository
import com.mahin.hytune.data.local.db.dao.ProfileDao
import com.mahin.hytune.domain.probe.HardwareProbe
import com.mahin.hytune.domain.sampler.SystemSamplerEngine
import com.mahin.hytune.domain.sampler.model.SystemSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val hardwareProbe: HardwareProbe,
    private val samplerEngine: SystemSamplerEngine,
    private val settingsRepository: AppSettingsRepository,
    private val profileDao: ProfileDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var sampleJob: kotlinx.coroutines.Job? = null

    init {
        loadHardwareInfo()
        observeSettingsAndProfiles()
        startSampling()
    }

    private fun loadHardwareInfo() {
        viewModelScope.launch {
            val summary = hardwareProbe.probeHardware()
            _uiState.update { it.copy(hardwareSummary = summary) }
        }
    }

    private fun observeSettingsAndProfiles() {
        viewModelScope.launch {
            combine(
                settingsRepository.settings,
                profileDao.getAllProfiles()
            ) { settings, profiles ->
                _uiState.update { state ->
                    state.copy(
                        activeProfileId = settings.activeProfileId,
                        profiles = profiles,
                        temperatureInCelsius = settings.temperatureUnitCelsius
                    )
                }
            }.collect {}
        }
    }

    fun startSampling(intervalMs: Long = 1000L) {
        sampleJob?.cancel()
        sampleJob = viewModelScope.launch {
            samplerEngine.sampleStream(intervalMs).collect { snapshot ->
                processSnapshot(snapshot)
            }
        }
    }

    fun stopSampling() {
        sampleJob?.cancel()
        sampleJob = null
    }

    fun processSnapshot(snapshot: SystemSnapshot) {
        _uiState.update { state ->
            val newCpuHistory = (state.cpuLoadHistory + snapshot.cpu.overallUsagePercent)
                .takeLast(DashboardUiState.MAX_HISTORY_POINTS)
            val newRamHistory = (state.ramUsageHistory + snapshot.memory.usagePercent)
                .takeLast(DashboardUiState.MAX_HISTORY_POINTS)
            val newTempHistory = (state.tempHistory + snapshot.thermals.maxTempC)
                .takeLast(DashboardUiState.MAX_HISTORY_POINTS)
            val newBatteryHistory = (state.batteryWattageHistory + (snapshot.battery?.powerWatts ?: 0f))
                .takeLast(DashboardUiState.MAX_HISTORY_POINTS)

            state.copy(
                latestSnapshot = snapshot,
                cpuLoadHistory = newCpuHistory,
                ramUsageHistory = newRamHistory,
                tempHistory = newTempHistory,
                batteryWattageHistory = newBatteryHistory,
                isLoading = false
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopSampling()
    }

    fun onProfileSelected(profileId: String) {
        viewModelScope.launch {
            settingsRepository.setActiveProfileId(profileId)
        }
    }
}
