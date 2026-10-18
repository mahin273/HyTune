package com.mahin.hytune.ui.process

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mahin.hytune.domain.process.ProcessRepository
import com.mahin.hytune.domain.process.model.ProcessFilter
import com.mahin.hytune.domain.process.model.ProcessInfo
import com.mahin.hytune.domain.process.model.ProcessSignal
import com.mahin.hytune.domain.process.model.ProcessSortBy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProcessUiState(
    val processes: List<ProcessInfo> = emptyList(),
    val filteredProcesses: List<ProcessInfo> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val sortBy: ProcessSortBy = ProcessSortBy.CPU,
    val sortAscending: Boolean = false,
    val filter: ProcessFilter = ProcessFilter.ALL,
    val selectedProcess: ProcessInfo? = null,
    val selectedProcessOomAdj: Int? = null,
    val isOperationInProgress: Boolean = false,
    val statusMessage: String? = null
)

@HiltViewModel
class ProcessViewModel @Inject constructor(
    private val repository: ProcessRepository
) : ViewModel() {

    private val _rawProcesses = MutableStateFlow<List<ProcessInfo>>(emptyList())
    private val _isLoading = MutableStateFlow(false)
    private val _searchQuery = MutableStateFlow("")
    private val _sortBy = MutableStateFlow(ProcessSortBy.CPU)
    private val _sortAscending = MutableStateFlow(false)
    private val _filter = MutableStateFlow(ProcessFilter.ALL)
    private val _selectedProcess = MutableStateFlow<ProcessInfo?>(null)
    private val _selectedProcessOomAdj = MutableStateFlow<Int?>(null)
    private val _isOperationInProgress = MutableStateFlow(false)
    private val _statusMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ProcessUiState> = combine(
        _rawProcesses,
        _isLoading,
        _searchQuery,
        _sortBy,
        _sortAscending,
        _filter,
        _selectedProcess,
        _selectedProcessOomAdj,
        _isOperationInProgress,
        _statusMessage
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val processes = args[0] as List<ProcessInfo>
        val isLoading = args[1] as Boolean
        val query = args[2] as String
        val sortBy = args[3] as ProcessSortBy
        val sortAscending = args[4] as Boolean
        val filter = args[5] as ProcessFilter
        val selectedProcess = args[6] as ProcessInfo?
        val selectedOomAdj = args[7] as Int?
        val isOpInProgress = args[8] as Boolean
        val statusMsg = args[9] as String?

        val filtered = computeFilteredAndSortedList(
            processes = processes,
            query = query,
            sortBy = sortBy,
            sortAscending = sortAscending,
            filter = filter
        )

        ProcessUiState(
            processes = processes,
            filteredProcesses = filtered,
            isLoading = isLoading,
            searchQuery = query,
            sortBy = sortBy,
            sortAscending = sortAscending,
            filter = filter,
            selectedProcess = selectedProcess,
            selectedProcessOomAdj = selectedOomAdj,
            isOperationInProgress = isOpInProgress,
            statusMessage = statusMsg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ProcessUiState()
    )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val list = repository.getProcesses()
                _rawProcesses.value = list

                // If selected process is open, update its snapshot
                val currentSelected = _selectedProcess.value
                if (currentSelected != null) {
                    val updated = list.firstOrNull { it.pid == currentSelected.pid }
                    _selectedProcess.value = updated
                    if (updated != null) {
                        _selectedProcessOomAdj.value = repository.getOomScoreAdj(updated.pid)
                    }
                }
            } catch (t: Throwable) {
                _statusMessage.value = "Failed to load processes: ${t.message ?: "Unknown error"}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortBy(sortBy: ProcessSortBy) {
        if (_sortBy.value == sortBy) {
            _sortAscending.value = !_sortAscending.value
        } else {
            _sortBy.value = sortBy
            _sortAscending.value = (sortBy == ProcessSortBy.NAME || sortBy == ProcessSortBy.PID)
        }
    }

    fun setFilter(filter: ProcessFilter) {
        _filter.value = filter
    }

    fun selectProcess(process: ProcessInfo?) {
        _selectedProcess.value = process
        if (process != null) {
            viewModelScope.launch {
                _selectedProcessOomAdj.value = repository.getOomScoreAdj(process.pid)
            }
        } else {
            _selectedProcessOomAdj.value = null
        }
    }

    fun sendSignal(process: ProcessInfo, signal: ProcessSignal) {
        viewModelScope.launch {
            _isOperationInProgress.value = true
            val result = repository.sendSignal(process.pid, process.name, signal)
            result.onSuccess { msg ->
                _statusMessage.value = msg
                if (signal == ProcessSignal.KILL || signal == ProcessSignal.TERM) {
                    _selectedProcess.value = null
                }
                refresh()
            }.onFailure { err ->
                _statusMessage.value = err.message ?: "Signal execution failed"
            }
            _isOperationInProgress.value = false
        }
    }

    fun setNice(process: ProcessInfo, nice: Int) {
        viewModelScope.launch {
            _isOperationInProgress.value = true
            val result = repository.setNice(process.pid, process.name, nice)
            result.onSuccess { msg ->
                _statusMessage.value = msg
                refresh()
            }.onFailure { err ->
                _statusMessage.value = err.message ?: "Failed to set nice level"
            }
            _isOperationInProgress.value = false
        }
    }

    fun setOomScoreAdj(process: ProcessInfo, adj: Int) {
        viewModelScope.launch {
            _isOperationInProgress.value = true
            val result = repository.setOomScoreAdj(process.pid, process.name, adj)
            result.onSuccess { msg ->
                _statusMessage.value = msg
                _selectedProcessOomAdj.value = adj
                refresh()
            }.onFailure { err ->
                _statusMessage.value = err.message ?: "Failed to set OOM score adjustment"
            }
            _isOperationInProgress.value = false
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    private fun computeFilteredAndSortedList(
        processes: List<ProcessInfo>,
        query: String,
        sortBy: ProcessSortBy,
        sortAscending: Boolean,
        filter: ProcessFilter
    ): List<ProcessInfo> {
        val trimmedQuery = query.trim().lowercase()

        val filteredByQuery = if (trimmedQuery.isEmpty()) {
            processes
        } else {
            processes.filter {
                it.name.lowercase().contains(trimmedQuery) ||
                        it.pid.toString().contains(trimmedQuery) ||
                        (it.packageName?.lowercase()?.contains(trimmedQuery) == true) ||
                        it.user.lowercase().contains(trimmedQuery)
            }
        }

        val filteredByType = when (filter) {
            ProcessFilter.ALL -> filteredByQuery
            ProcessFilter.USER_APPS -> filteredByQuery.filter { it.packageName != null && !it.isCritical }
            ProcessFilter.SYSTEM -> filteredByQuery.filter { it.isCritical || it.user == "root" || it.user == "system" }
        }

        val comparator: Comparator<ProcessInfo> = when (sortBy) {
            ProcessSortBy.CPU -> compareBy { it.cpuUsagePercent }
            ProcessSortBy.RAM -> compareBy { it.rssKb }
            ProcessSortBy.PID -> compareBy { it.pid }
            ProcessSortBy.NAME -> compareBy { it.name.lowercase() }
        }

        return if (sortAscending) {
            filteredByType.sortedWith(comparator)
        } else {
            filteredByType.sortedWith(comparator.reversed())
        }
    }
}
