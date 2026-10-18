package com.mahin.hytune.ui.battery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mahin.hytune.domain.battery.BatteryRepository
import com.mahin.hytune.domain.battery.model.BatteryState
import com.mahin.hytune.domain.battery.model.ChargingSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BatteryUiState(
    val batteryState: BatteryState = BatteryState(),
    val sessions: List<ChargingSession> = emptyList(),
    val isLoading: Boolean = false,
    val userMessage: String? = null
)

@HiltViewModel
class BatteryViewModel @Inject constructor(
    private val repository: BatteryRepository
) : ViewModel() {

    private val _userMessage = MutableStateFlow<String?>(null)
    private val _isLoading = MutableStateFlow(false)

    val uiState: StateFlow<BatteryUiState> = combine(
        repository.getBatteryStateFlow(1000L),
        repository.getAllChargingSessions(),
        _isLoading,
        _userMessage
    ) { batteryState, sessions, isLoading, message ->
        BatteryUiState(
            batteryState = batteryState,
            sessions = sessions,
            isLoading = isLoading,
            userMessage = message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BatteryUiState()
    )

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
