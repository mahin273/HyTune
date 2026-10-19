package com.mahin.hytune.ui.display

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mahin.hytune.domain.display.DisplayControlEngine
import com.mahin.hytune.domain.display.model.DisplayDensity
import com.mahin.hytune.domain.display.model.DisplayRefreshRate
import com.mahin.hytune.domain.display.model.DisplayResolution
import com.mahin.hytune.domain.display.model.DisplayStateSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DisplayUiState(
    val snapshot: DisplayStateSnapshot? = null,
    val isLoading: Boolean = false,
    val countdownRemainingSec: Int? = null,
    val stagedResolution: DisplayResolution? = null,
    val stagedDensity: DisplayDensity? = null,
    val userMessage: String? = null
)

@HiltViewModel
class DisplayViewModel @Inject constructor(
    private val displayControlEngine: DisplayControlEngine
) : ViewModel() {

    private val snapshotState = MutableStateFlow<DisplayStateSnapshot?>(null)
    private val isLoadingState = MutableStateFlow(false)
    private val countdownRemainingState = MutableStateFlow<Int?>(null)
    private val stagedResolutionState = MutableStateFlow<DisplayResolution?>(null)
    private val stagedDensityState = MutableStateFlow<DisplayDensity?>(null)
    private val userMessageState = MutableStateFlow<String?>(null)

    private var previousSnapshot: DisplayStateSnapshot? = null
    private var countdownJob: Job? = null

    val uiState: StateFlow<DisplayUiState> = combine(
        snapshotState,
        isLoadingState,
        countdownRemainingState,
        combine(stagedResolutionState, stagedDensityState, userMessageState, ::Triple)
    ) { snapshot, loading, countdown, (stagedRes, stagedDen, message) ->
        DisplayUiState(
            snapshot = snapshot,
            isLoading = loading,
            countdownRemainingSec = countdown,
            stagedResolution = stagedRes,
            stagedDensity = stagedDen,
            userMessage = message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = DisplayUiState(isLoading = true)
    )

    init {
        refreshDisplayState()
    }

    fun refreshDisplayState(): Job = viewModelScope.launch {
        isLoadingState.value = true
        val result = displayControlEngine.getDisplayState()
        snapshotState.value = result.getOrNull()
        if (result.isFailure) {
            userMessageState.value = "Failed to query display: ${result.exceptionOrNull()?.message}"
        }
        isLoadingState.value = false
    }

    fun applyResolutionWithCountdown(
        width: Int,
        height: Int,
        autoScaleDpi: Boolean = true,
        timeoutSec: Int = 15
    ): Job = viewModelScope.launch {
        isLoadingState.value = true
        val current = snapshotState.value ?: displayControlEngine.getDisplayState().getOrNull()
        previousSnapshot = current

        stagedResolutionState.value = DisplayResolution(width, height)

        var targetDpi: Int? = null
        if (autoScaleDpi && current != null) {
            targetDpi = displayControlEngine.calculateScaledDpi(
                baseWidth = current.physicalWidth,
                targetWidth = width,
                baseDpi = current.physicalDpi
            )
            stagedDensityState.value = DisplayDensity(targetDpi)
        }

        val resResult = displayControlEngine.setResolution(width, height)
        if (resResult.isFailure) {
            userMessageState.value = "Failed to apply resolution: ${resResult.exceptionOrNull()?.message}"
            isLoadingState.value = false
            return@launch
        }

        if (targetDpi != null) {
            displayControlEngine.setDensity(targetDpi)
        }

        refreshDisplayState().join()

        startCountdown(timeoutSec)
        isLoadingState.value = false
    }

    fun applyDensityWithCountdown(dpi: Int, timeoutSec: Int = 15): Job = viewModelScope.launch {
        isLoadingState.value = true
        val current = snapshotState.value ?: displayControlEngine.getDisplayState().getOrNull()
        previousSnapshot = current
        stagedDensityState.value = DisplayDensity(dpi)

        val densityResult = displayControlEngine.setDensity(dpi)
        if (densityResult.isFailure) {
            userMessageState.value = "Failed to apply density: ${densityResult.exceptionOrNull()?.message}"
            isLoadingState.value = false
            return@launch
        }

        refreshDisplayState().join()
        startCountdown(timeoutSec)
        isLoadingState.value = false
    }

    fun applyRefreshRate(minHz: Float, peakHz: Float): Job = viewModelScope.launch {
        isLoadingState.value = true
        val result = displayControlEngine.setRefreshRate(minHz, peakHz)
        if (result.isSuccess) {
            userMessageState.value = "Refresh rate set to ${minHz}Hz - ${peakHz}Hz"
        } else {
            userMessageState.value = "Failed to set refresh rate: ${result.exceptionOrNull()?.message}"
        }
        refreshDisplayState().join()
        isLoadingState.value = false
    }

    fun resetRefreshRate(): Job = viewModelScope.launch {
        isLoadingState.value = true
        displayControlEngine.resetRefreshRate()
        userMessageState.value = "Refresh rate reset to dynamic system defaults"
        refreshDisplayState().join()
        isLoadingState.value = false
    }

    fun confirmChanges() {
        countdownJob?.cancel()
        countdownJob = null
        countdownRemainingState.value = null
        stagedResolutionState.value = null
        stagedDensityState.value = null
        previousSnapshot = null
        userMessageState.value = "Display changes confirmed and active"
    }

    fun revertChangesNow(): Job = viewModelScope.launch {
        countdownJob?.cancel()
        countdownJob = null
        countdownRemainingState.value = null
        isLoadingState.value = true

        val baseline = previousSnapshot
        if (baseline != null) {
            displayControlEngine.restoreSnapshot(baseline)
        } else {
            displayControlEngine.resetResolution()
            displayControlEngine.resetDensity()
        }

        previousSnapshot = null
        stagedResolutionState.value = null
        stagedDensityState.value = null
        userMessageState.value = "Reverted display settings to previous baseline"

        refreshDisplayState().join()
        isLoadingState.value = false
    }

    fun resetAllDefaults(): Job = viewModelScope.launch {
        countdownJob?.cancel()
        countdownJob = null
        countdownRemainingState.value = null
        isLoadingState.value = true

        displayControlEngine.resetResolution()
        displayControlEngine.resetDensity()
        displayControlEngine.resetRefreshRate()

        previousSnapshot = null
        stagedResolutionState.value = null
        stagedDensityState.value = null
        userMessageState.value = "Display reset to physical factory defaults"

        refreshDisplayState().join()
        isLoadingState.value = false
    }

    fun dismissUserMessage() {
        userMessageState.value = null
    }

    private fun startCountdown(seconds: Int) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (sec in seconds downTo 1) {
                countdownRemainingState.value = sec
                delay(1000L)
            }
            countdownRemainingState.value = 0
            revertChangesNow().join()
        }
    }
}
