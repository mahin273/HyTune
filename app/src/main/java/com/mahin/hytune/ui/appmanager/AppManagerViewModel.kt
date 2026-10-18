package com.mahin.hytune.ui.appmanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mahin.hytune.domain.appmanager.AppManagerRepository
import com.mahin.hytune.domain.appmanager.model.AppDetails
import com.mahin.hytune.domain.appmanager.model.AppSortOrder
import com.mahin.hytune.domain.appmanager.model.AppSummary
import com.mahin.hytune.domain.appmanager.model.AppTypeFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppManagerUiState(
    val apps: List<AppSummary> = emptyList(),
    val totalCount: Int = 0,
    val userAppCount: Int = 0,
    val systemAppCount: Int = 0,
    val disabledCount: Int = 0,
    val gamesCount: Int = 0,
    val searchQuery: String = "",
    val filter: AppTypeFilter = AppTypeFilter.ALL,
    val sortOrder: AppSortOrder = AppSortOrder.NAME_ASC,
    val isLoading: Boolean = false,
    val selectedAppDetails: AppDetails? = null,
    val isInspecting: Boolean = false,
    val inspectionError: String? = null
)

@HiltViewModel
class AppManagerViewModel @Inject constructor(
    private val repository: AppManagerRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _rawApps = MutableStateFlow<List<AppSummary>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    private val _filter = MutableStateFlow(AppTypeFilter.ALL)
    private val _sortOrder = MutableStateFlow(AppSortOrder.NAME_ASC)
    private val _isLoading = MutableStateFlow(true)
    private val _selectedAppDetails = MutableStateFlow<AppDetails?>(null)
    private val _isInspecting = MutableStateFlow(false)
    private val _inspectionError = MutableStateFlow<String?>(null)

    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    val filter: StateFlow<AppTypeFilter> = _filter.asStateFlow()
    val sortOrder: StateFlow<AppSortOrder> = _sortOrder.asStateFlow()
    val selectedAppDetails: StateFlow<AppDetails?> = _selectedAppDetails.asStateFlow()

    init {
        loadApps(forceRefresh = false)
    }

    private val filterAndSortFlow = combine(
        _searchQuery,
        _filter,
        _sortOrder
    ) { query, filter, sort ->
        Triple(query, filter, sort)
    }

    private val inspectionStateFlow = combine(
        _selectedAppDetails,
        _isInspecting,
        _inspectionError
    ) { details, inspecting, error ->
        Triple(details, inspecting, error)
    }

    val uiState: StateFlow<AppManagerUiState> = combine(
        _rawApps,
        _isLoading,
        filterAndSortFlow,
        inspectionStateFlow
    ) { apps, loading, (query, filter, sort), (details, inspecting, error) ->
        val total = apps.size
        val userApps = apps.count { !it.isSystemApp }
        val systemApps = apps.count { it.isSystemApp }
        val disabled = apps.count { !it.isEnabled || it.isSuspended }
        val games = apps.count { it.isGame }

        val filtered = apps.filter { app ->
            val matchesFilter = when (filter) {
                AppTypeFilter.ALL -> true
                AppTypeFilter.USER -> !app.isSystemApp
                AppTypeFilter.SYSTEM -> app.isSystemApp
                AppTypeFilter.DISABLED -> !app.isEnabled || app.isSuspended
                AppTypeFilter.GAMES -> app.isGame
            }

            val matchesQuery = query.isBlank() ||
                    app.appLabel.contains(query, ignoreCase = true) ||
                    app.packageName.contains(query, ignoreCase = true)

            matchesFilter && matchesQuery
        }

        val sorted = when (sort) {
            AppSortOrder.NAME_ASC -> filtered.sortedBy { it.appLabel.lowercase() }
            AppSortOrder.NAME_DESC -> filtered.sortedByDescending { it.appLabel.lowercase() }
            AppSortOrder.PACKAGE_ASC -> filtered.sortedBy { it.packageName.lowercase() }
            AppSortOrder.INSTALL_TIME_DESC -> filtered.sortedByDescending { it.installTimeEpochMs }
            AppSortOrder.UPDATE_TIME_DESC -> filtered.sortedByDescending { it.updateTimeEpochMs }
            AppSortOrder.TARGET_SDK_DESC -> filtered.sortedByDescending { it.targetSdk }
        }

        AppManagerUiState(
            apps = sorted,
            totalCount = total,
            userAppCount = userApps,
            systemAppCount = systemApps,
            disabledCount = disabled,
            gamesCount = games,
            searchQuery = query,
            filter = filter,
            sortOrder = sort,
            isLoading = loading,
            selectedAppDetails = details,
            isInspecting = inspecting,
            inspectionError = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppManagerUiState(isLoading = true)
    )

    fun loadApps(forceRefresh: Boolean = false) {
        viewModelScope.launch(ioDispatcher) {
            _isLoading.value = true
            repository.getInstalledApps(forceRefresh).collect { list ->
                _rawApps.value = list
                _isLoading.value = false
            }
        }
    }

    fun setAppsForTesting(apps: List<AppSummary>) {
        _rawApps.value = apps
        _isLoading.value = false
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: AppTypeFilter) {
        _filter.value = filter
    }

    fun setSortOrder(order: AppSortOrder) {
        _sortOrder.value = order
    }

    fun selectAppForInspection(packageName: String) {
        viewModelScope.launch(ioDispatcher) {
            _isInspecting.value = true
            _inspectionError.value = null
            repository.getAppDetails(packageName)
                .onSuccess { details ->
                    _selectedAppDetails.value = details
                    _isInspecting.value = false
                }
                .onFailure { error ->
                    _inspectionError.value = error.message ?: "Failed to inspect package"
                    _isInspecting.value = false
                }
        }
    }

    fun clearSelectedApp() {
        _selectedAppDetails.value = null
        _inspectionError.value = null
        _isInspecting.value = false
    }
}
