package com.mahin.hytune.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.mahin.hytune.data.local.datastore.AppSettingsRepository
import com.mahin.hytune.service.battery.BatteryOptimizationManager
import com.mahin.hytune.service.model.DaemonState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DaemonController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appSettingsRepository: AppSettingsRepository,
    private val batteryOptimizationManager: BatteryOptimizationManager
) {

    private val _daemonState = MutableStateFlow(DaemonState.STOPPED)
    val daemonState: StateFlow<DaemonState> = _daemonState.asStateFlow()

    fun updateState(state: DaemonState) {
        _daemonState.value = state
    }

    suspend fun startDaemon() {
        _daemonState.value = DaemonState.STARTING
        appSettingsRepository.setDaemonServiceEnabled(true)
        val intent = Intent(context, HyTuneDaemonService::class.java).apply {
            action = HyTuneDaemonService.ACTION_START
        }
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (_: Exception) {
            _daemonState.value = DaemonState.STOPPED
        }
    }

    suspend fun stopDaemon() {
        _daemonState.value = DaemonState.STOPPING
        appSettingsRepository.setDaemonServiceEnabled(false)
        val intent = Intent(context, HyTuneDaemonService::class.java).apply {
            action = HyTuneDaemonService.ACTION_STOP
        }
        try {
            context.startService(intent)
        } catch (_: Exception) {
            _daemonState.value = DaemonState.STOPPED
        }
    }

    fun isIgnoringBatteryOptimizations(): Boolean {
        return batteryOptimizationManager.isIgnoringBatteryOptimizations()
    }

    fun createRequestIgnoreBatteryOptimizationsIntent(): Intent {
        return batteryOptimizationManager.createRequestIgnoreBatteryOptimizationsIntent()
    }

    fun isRunning(): Boolean {
        return _daemonState.value == DaemonState.RUNNING
    }
}
