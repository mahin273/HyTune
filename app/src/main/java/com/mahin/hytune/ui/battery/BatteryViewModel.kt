package com.mahin.hytune.ui.battery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mahin.hytune.data.local.datastore.AppSettingsRepository
import com.mahin.hytune.domain.battery.BatteryRepository
import com.mahin.hytune.domain.battery.ChargeControlEngine
import com.mahin.hytune.domain.battery.model.BatteryState
import com.mahin.hytune.domain.battery.model.ChargeControlConfig
import com.mahin.hytune.domain.battery.model.ChargeControlStatus
import com.mahin.hytune.domain.battery.model.ChargingSession
import com.mahin.hytune.domain.overlay.FloatingOverlayManager
import com.mahin.hytune.domain.overlay.model.OverlayConfig
import com.mahin.hytune.domain.thermal.ThermalRepository
import com.mahin.hytune.domain.thermal.model.ThermalSnapshot
import com.mahin.hytune.domain.thermal.model.ThermalThrottleEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BatteryUiState(
    val batteryState: BatteryState = BatteryState(),
    val sessions: List<ChargingSession> = emptyList(),
    val chargeStatus: ChargeControlStatus = ChargeControlStatus(),
    val chargeConfig: ChargeControlConfig = ChargeControlConfig(),
    val thermalSnapshot: ThermalSnapshot = ThermalSnapshot(),
    val throttleEvents: List<ThermalThrottleEvent> = emptyList(),
    val overlayConfig: OverlayConfig = OverlayConfig(),
    val isOverlayShowing: Boolean = false,
    val canDrawOverlays: Boolean = false,
    val isLoading: Boolean = false,
    val userMessage: String? = null
)

@HiltViewModel
class BatteryViewModel @Inject constructor(
    private val repository: BatteryRepository,
    private val chargeControlEngine: ChargeControlEngine? = null,
    private val thermalRepository: ThermalRepository? = null,
    private val appSettingsRepository: AppSettingsRepository? = null,
    private val floatingOverlayManager: FloatingOverlayManager? = null
) : ViewModel() {

    private val _userMessage = MutableStateFlow<String?>(null)
    private val _isLoading = MutableStateFlow(false)
    private val _chargeStatus = MutableStateFlow(ChargeControlStatus())

    init {
        refreshChargeStatus()
    }

    private data class BatteryGroup(
        val batteryState: BatteryState,
        val sessions: List<ChargingSession>,
        val chargeStatus: ChargeControlStatus,
        val chargeConfig: ChargeControlConfig
    )

    private data class TelemetryGroup(
        val thermalSnapshot: ThermalSnapshot,
        val throttleEvents: List<ThermalThrottleEvent>,
        val overlayConfig: OverlayConfig,
        val isOverlayShowing: Boolean
    )

    private val chargeConfigFlow = appSettingsRepository?.settings?.let { settingsFlow ->
        settingsFlow.map { settings ->
            ChargeControlConfig(
                bypassChargingEnabled = settings.bypassChargingEnabled,
                chargeLimitEnabled = settings.chargeLimitEnabled,
                chargeLimitPercent = settings.chargeLimitPercent,
                chargeCurrentLimitMa = settings.chargeCurrentLimitMa
            )
        }
    } ?: flowOf(ChargeControlConfig())

    private val thermalSnapshotFlow = thermalRepository?.observeThermalSnapshot(1000L) ?: flowOf(ThermalSnapshot())
    private val throttleEventsFlow = thermalRepository?.observeRecentThrottleEvents(20) ?: flowOf(emptyList())
    private val overlayConfigFlow = floatingOverlayManager?.config ?: flowOf(OverlayConfig())
    private val overlayShowingFlow = floatingOverlayManager?.isShowing ?: flowOf(false)

    val uiState: StateFlow<BatteryUiState> = combine(
        combine(
            repository.getBatteryStateFlow(1000L),
            repository.getAllChargingSessions(),
            _chargeStatus,
            chargeConfigFlow
        ) { battery, sessions, chargeStatus, chargeConfig ->
            BatteryGroup(battery, sessions, chargeStatus, chargeConfig)
        },
        combine(
            thermalSnapshotFlow,
            throttleEventsFlow,
            overlayConfigFlow,
            overlayShowingFlow
        ) { thermal, throttles, overlayConfig, overlayShowing ->
            TelemetryGroup(thermal, throttles, overlayConfig, overlayShowing)
        },
        combine(_isLoading, _userMessage) { loading, message ->
            Pair(loading, message)
        }
    ) { bg, tg, uiMisc ->
        BatteryUiState(
            batteryState = bg.batteryState,
            sessions = bg.sessions,
            chargeStatus = bg.chargeStatus,
            chargeConfig = bg.chargeConfig,
            thermalSnapshot = tg.thermalSnapshot,
            throttleEvents = tg.throttleEvents,
            overlayConfig = tg.overlayConfig,
            isOverlayShowing = tg.isOverlayShowing,
            canDrawOverlays = floatingOverlayManager?.canDrawOverlays() ?: false,
            isLoading = uiMisc.first,
            userMessage = uiMisc.second
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BatteryUiState(canDrawOverlays = floatingOverlayManager?.canDrawOverlays() ?: false)
    )

    fun canDrawOverlays(): Boolean {
        return floatingOverlayManager?.canDrawOverlays() ?: false
    }

    fun toggleOverlay(enable: Boolean) {
        viewModelScope.launch {
            if (floatingOverlayManager == null) {
                _userMessage.value = "Overlay manager not available"
                return@launch
            }
            if (!floatingOverlayManager.canDrawOverlays()) {
                _userMessage.value = "Overlay permission required"
                return@launch
            }
            if (enable) {
                val shown = floatingOverlayManager.showOverlay()
                if (!shown) {
                    _userMessage.value = "Failed to display overlay"
                }
            } else {
                floatingOverlayManager.hideOverlay()
            }
        }
    }

    fun setOverlayCompactMode(compact: Boolean) {
        viewModelScope.launch {
            appSettingsRepository?.setOverlayCompactMode(compact)
        }
    }

    fun setOverlayOpacity(opacity: Float) {
        viewModelScope.launch {
            appSettingsRepository?.setOverlayOpacity(opacity)
        }
    }

    fun setOverlayMetricVisibility(
        cpu: Boolean,
        gpu: Boolean,
        batt: Boolean,
        temp: Boolean,
        ram: Boolean,
        fps: Boolean = true
    ) {
        viewModelScope.launch {
            appSettingsRepository?.setOverlayMetricVisibility(cpu, gpu, batt, temp, ram, fps)
        }
    }

    fun refreshChargeStatus() {
        if (chargeControlEngine == null) return
        viewModelScope.launch {
            try {
                _chargeStatus.value = chargeControlEngine.getStatus()
            } catch (_: Throwable) {
                // Ignore refresh error
            }
        }
    }

    fun setBypassCharging(enabled: Boolean) {
        viewModelScope.launch {
            if (chargeControlEngine == null) {
                _userMessage.value = "Charge control not available"
                return@launch
            }
            _isLoading.value = true
            val result = chargeControlEngine.setBypassCharging(enabled)
            if (result.isSuccess) {
                appSettingsRepository?.setBypassChargingEnabled(enabled)
                refreshChargeStatus()
                _userMessage.value = if (enabled) "Bypass charging enabled" else "Bypass charging disabled"
            } else {
                _userMessage.value = "Failed: ${result.exceptionOrNull()?.message ?: "Unknown error"}"
            }
            _isLoading.value = false
        }
    }

    fun setChargeCurrentLimit(limitMa: Int) {
        viewModelScope.launch {
            if (chargeControlEngine == null) {
                _userMessage.value = "Charge control not available"
                return@launch
            }
            _isLoading.value = true
            val result = chargeControlEngine.setChargeCurrentLimit(limitMa)
            if (result.isSuccess) {
                appSettingsRepository?.setChargeCurrentLimitMa(limitMa)
                refreshChargeStatus()
                val msg = if (limitMa <= 0) "Charge current limit reset to unlimited" else "Charge current limited to $limitMa mA"
                _userMessage.value = msg
            } else {
                _userMessage.value = "Failed: ${result.exceptionOrNull()?.message ?: "Unknown error"}"
            }
            _isLoading.value = false
        }
    }

    fun setChargeLimitConfig(enabled: Boolean, percent: Int) {
        viewModelScope.launch {
            appSettingsRepository?.setChargeLimitEnabled(enabled)
            appSettingsRepository?.setChargeLimitPercent(percent)
            _userMessage.value = if (enabled) "Battery limit set to $percent%" else "Battery limit disabled"
        }
    }

    fun clearThrottleHistory() {
        viewModelScope.launch {
            try {
                thermalRepository?.clearThrottleHistory()
                _userMessage.value = "Thermal throttle history cleared"
            } catch (t: Throwable) {
                _userMessage.value = "Failed to clear throttle history: ${t.message ?: "Unknown error"}"
            }
        }
    }

    fun clearSessionHistory() {
        viewModelScope.launch {
            try {
                repository.clearSessionHistory()
                _userMessage.value = "Charging session history cleared"
            } catch (t: Throwable) {
                _userMessage.value = "Failed to clear history: ${t.message ?: "Unknown error"}"
            }
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }
}
