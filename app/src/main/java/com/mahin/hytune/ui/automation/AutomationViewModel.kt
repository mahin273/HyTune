package com.mahin.hytune.ui.automation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mahin.hytune.domain.automation.AutomationRepository
import com.mahin.hytune.domain.automation.model.AutomationRule
import com.mahin.hytune.domain.automation.model.SystemStateSnapshot
import com.mahin.hytune.domain.profile.ProfileRepository
import com.mahin.hytune.domain.profile.model.TuningProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AutomationUiState(
    val rules: List<AutomationRule> = emptyList(),
    val isEngineRunning: Boolean = false,
    val currentSystemState: SystemStateSnapshot = SystemStateSnapshot(),
    val lastExecutedRules: List<AutomationRule> = emptyList(),
    val availableProfiles: List<TuningProfile> = emptyList()
)

@HiltViewModel
class AutomationViewModel @Inject constructor(
    private val automationRepository: AutomationRepository,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private data class EngineState(
        val isRunning: Boolean,
        val systemState: SystemStateSnapshot,
        val lastExecuted: List<AutomationRule>
    )

    private val engineState = combine(
        automationRepository.isEngineRunning,
        automationRepository.currentSystemState,
        automationRepository.lastExecutedRules
    ) { isRunning, systemState, lastExecuted ->
        EngineState(isRunning, systemState, lastExecuted)
    }

    val uiState: StateFlow<AutomationUiState> = combine(
        automationRepository.getAllRules(),
        profileRepository.getAllProfiles(),
        engineState
    ) { rules, profiles, engine ->
        AutomationUiState(
            rules = rules,
            isEngineRunning = engine.isRunning,
            currentSystemState = engine.systemState,
            lastExecutedRules = engine.lastExecuted,
            availableProfiles = profiles
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AutomationUiState()
    )

    fun toggleRuleEnabled(id: Long, isEnabled: Boolean) {
        viewModelScope.launch {
            automationRepository.setRuleEnabled(id, isEnabled)
        }
    }

    fun saveRule(rule: AutomationRule) {
        viewModelScope.launch {
            if (rule.id == 0L) {
                automationRepository.insertRule(rule)
            } else {
                automationRepository.updateRule(rule)
            }
        }
    }

    fun deleteRule(id: Long) {
        viewModelScope.launch {
            automationRepository.deleteRule(id)
        }
    }

    fun toggleEngine(enable: Boolean) {
        if (enable) {
            automationRepository.startEngine()
        } else {
            automationRepository.stopEngine()
        }
    }

    fun triggerManualEvaluation(state: SystemStateSnapshot) {
        viewModelScope.launch {
            automationRepository.evaluateState(state)
        }
    }
}
