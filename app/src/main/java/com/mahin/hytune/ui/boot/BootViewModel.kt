package com.mahin.hytune.ui.boot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mahin.hytune.data.local.datastore.AppSettingsRepository
import com.mahin.hytune.domain.boot.BootApplyEngine
import com.mahin.hytune.domain.safety.BootloopCrashGuard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BootUiState(
    val applyOnBoot: Boolean = false,
    val bootApplyDelaySeconds: Int = 30,
    val bootApplyNotificationEnabled: Boolean = true,
    val lastBootApplyStatus: String? = null,
    val bootloopCrashCount: Int = 0,
    val safeRecoveryModeActive: Boolean = false,
    val isTesting: Boolean = false,
    val testMessage: String? = null
)

@HiltViewModel
class BootViewModel @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository,
    private val bootloopCrashGuard: BootloopCrashGuard,
    private val bootApplyEngine: BootApplyEngine
) : ViewModel() {

    private val isTesting = MutableStateFlow(false)
    private val testMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<BootUiState> = combine(
        appSettingsRepository.settings,
        isTesting,
        testMessage
    ) { settings, testing, msg ->
        BootUiState(
            applyOnBoot = settings.applyOnBoot,
            bootApplyDelaySeconds = settings.bootApplyDelaySeconds,
            bootApplyNotificationEnabled = settings.bootApplyNotificationEnabled,
            lastBootApplyStatus = settings.lastBootApplyStatus,
            bootloopCrashCount = settings.bootloopCrashCount,
            safeRecoveryModeActive = settings.safeRecoveryModeActive,
            isTesting = testing,
            testMessage = msg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = BootUiState()
    )

    fun setApplyOnBoot(enabled: Boolean) = viewModelScope.launch {
        appSettingsRepository.setApplyOnBoot(enabled)
    }

    fun setBootApplyDelaySeconds(delaySeconds: Int) = viewModelScope.launch {
        appSettingsRepository.setBootApplyDelaySeconds(delaySeconds)
    }

    fun setBootApplyNotificationEnabled(enabled: Boolean) = viewModelScope.launch {
        appSettingsRepository.setBootApplyNotificationEnabled(enabled)
    }

    fun resetCrashCounter() = viewModelScope.launch {
        bootloopCrashGuard.resetCrashCounter()
    }

    fun testBootApplyNow() = viewModelScope.launch {
        isTesting.value = true
        testMessage.value = null
        try {
            val result = bootApplyEngine.testBootApplyNow()
            testMessage.value = result.message
        } catch (e: Throwable) {
            testMessage.value = "Test failed: ${e.message}"
        } finally {
            isTesting.value = false
        }
    }

    fun dismissTestMessage() {
        testMessage.value = null
    }
}
