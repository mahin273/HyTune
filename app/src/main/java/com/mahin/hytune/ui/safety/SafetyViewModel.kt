package com.mahin.hytune.ui.safety

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mahin.hytune.domain.safety.SafetyManager
import com.mahin.hytune.domain.safety.model.SafetyState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SafetyViewModel @Inject constructor(
    private val safetyManager: SafetyManager
) : ViewModel() {

    val safetyState: StateFlow<SafetyState> = safetyManager.safetyState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SafetyState()
        )

    private val _isPanicRestoring = MutableStateFlow(false)
    val isPanicRestoring: StateFlow<Boolean> = _isPanicRestoring.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun panicRestore(): kotlinx.coroutines.Job = viewModelScope.launch {
        _isPanicRestoring.value = true
        val result = safetyManager.panicRestore()
        _isPanicRestoring.value = false

        result.onSuccess {
            _statusMessage.value = "Hardware successfully restored to factory baseline."
        }.onFailure { error ->
            _statusMessage.value = "Panic restore failed: ${error.message}"
        }
    }

    fun setDryRunPreviewEnabled(enabled: Boolean): kotlinx.coroutines.Job = viewModelScope.launch {
        safetyManager.setDryRunPreviewEnabled(enabled)
    }

    fun resetCrashCounter(): kotlinx.coroutines.Job = viewModelScope.launch {
        safetyManager.resetCrashCounter()
        _statusMessage.value = "Crash counter reset to 0."
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}
