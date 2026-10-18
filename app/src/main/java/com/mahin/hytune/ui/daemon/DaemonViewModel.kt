package com.mahin.hytune.ui.daemon

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mahin.hytune.data.local.datastore.AppSettings
import com.mahin.hytune.data.local.datastore.AppSettingsRepository
import com.mahin.hytune.service.DaemonController
import com.mahin.hytune.service.model.DaemonState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DaemonViewModel @Inject constructor(
    private val daemonController: DaemonController,
    private val appSettingsRepository: AppSettingsRepository
) : ViewModel() {

    val daemonState: StateFlow<DaemonState> = daemonController.daemonState

    val appSettings: StateFlow<AppSettings> = appSettingsRepository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettings()
        )

    private val _isBatteryOptimizationIgnored = MutableStateFlow(
        daemonController.isIgnoringBatteryOptimizations()
    )
    val isBatteryOptimizationIgnored: StateFlow<Boolean> = _isBatteryOptimizationIgnored.asStateFlow()

    fun refreshBatteryOptimizationStatus() {
        _isBatteryOptimizationIgnored.value = daemonController.isIgnoringBatteryOptimizations()
    }

    fun toggleDaemon(enable: Boolean): kotlinx.coroutines.Job = viewModelScope.launch {
        if (enable) {
            daemonController.startDaemon()
        } else {
            daemonController.stopDaemon()
        }
    }

    fun setNotificationDetailsEnabled(enabled: Boolean): kotlinx.coroutines.Job = viewModelScope.launch {
        appSettingsRepository.setDaemonNotificationDetailsEnabled(enabled)
    }

    fun createRequestIgnoreBatteryOptimizationsIntent(): Intent {
        return daemonController.createRequestIgnoreBatteryOptimizationsIntent()
    }
}
