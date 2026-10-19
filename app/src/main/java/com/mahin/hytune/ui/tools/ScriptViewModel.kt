package com.mahin.hytune.ui.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mahin.hytune.domain.script.ScriptRepository
import com.mahin.hytune.domain.script.model.CustomScript
import com.mahin.hytune.domain.script.model.ScriptExecutionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScriptUiState(
    val scripts: List<CustomScript> = emptyList(),
    val isExecuting: Boolean = false,
    val executingScriptId: Long? = null,
    val lastExecutionResult: ScriptExecutionResult? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class ScriptViewModel @Inject constructor(
    private val scriptRepository: ScriptRepository
) : ViewModel() {

    private val _isExecuting = MutableStateFlow(false)
    private val _executingScriptId = MutableStateFlow<Long?>(null)
    private val _lastExecutionResult = MutableStateFlow<ScriptExecutionResult?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)

    private val scriptsFlow = scriptRepository.getAllScripts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    val uiState: StateFlow<ScriptUiState> = combine(
        scriptsFlow,
        _isExecuting,
        _executingScriptId,
        _lastExecutionResult,
        _errorMessage
    ) { scripts, isExecuting, executingScriptId, lastExecutionResult, errorMessage ->
        ScriptUiState(
            scripts = scripts,
            isExecuting = isExecuting,
            executingScriptId = executingScriptId,
            lastExecutionResult = lastExecutionResult,
            errorMessage = errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ScriptUiState()
    )

    fun executeScript(id: Long) {
        viewModelScope.launch {
            _isExecuting.value = true
            _executingScriptId.value = id
            _errorMessage.value = null

            val result = scriptRepository.executeScript(id)
            result.onSuccess { execResult ->
                _lastExecutionResult.value = execResult
            }.onFailure { ex ->
                _errorMessage.value = ex.message ?: "Execution failed"
            }

            _isExecuting.value = false
            _executingScriptId.value = null
        }
    }

    fun executeRawScript(content: String, runAsRoot: Boolean = true) {
        viewModelScope.launch {
            _isExecuting.value = true
            _errorMessage.value = null

            val result = scriptRepository.executeRawScript(content, runAsRoot)
            result.onSuccess { execResult ->
                _lastExecutionResult.value = execResult
            }.onFailure { ex ->
                _errorMessage.value = ex.message ?: "Execution failed"
            }

            _isExecuting.value = false
        }
    }

    fun saveScript(script: CustomScript) {
        viewModelScope.launch {
            try {
                scriptRepository.saveScript(script)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to save script"
            }
        }
    }

    fun deleteScript(id: Long) {
        viewModelScope.launch {
            try {
                scriptRepository.deleteScript(id)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to delete script"
            }
        }
    }

    fun toggleRunAtBoot(id: Long, runAtBoot: Boolean) {
        viewModelScope.launch {
            try {
                scriptRepository.setRunAtBoot(id, runAtBoot)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to update boot configuration"
            }
        }
    }

    fun clearExecutionResult() {
        _lastExecutionResult.value = null
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
