package com.mahin.hytune.ui.viewmodel

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mahin.hytune.data.local.datastore.AppSettingsRepository
import com.mahin.hytune.data.local.db.entity.AppRuleEntity
import com.mahin.hytune.domain.detection.GameClassifier
import com.mahin.hytune.domain.profile.AppRuleRepository
import com.mahin.hytune.domain.profile.BuiltInProfiles
import com.mahin.hytune.domain.profile.ProfileRepository
import com.mahin.hytune.domain.profile.model.TuningProfile
import com.mahin.hytune.ui.model.AppItemUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class AppFilterType {
    ALL,
    GAMES,
    CONFIGURED
}

data class PerAppUiState(
    val perAppProfilesEnabled: Boolean = false,
    val apps: List<AppItemUiModel> = emptyList(),
    val availableProfiles: List<TuningProfile> = BuiltInProfiles.ALL_BUILT_IN,
    val searchQuery: String = "",
    val filterType: AppFilterType = AppFilterType.ALL,
    val isLoading: Boolean = false
)

@HiltViewModel
class PerAppRulesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appRuleRepository: AppRuleRepository,
    private val profileRepository: ProfileRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val gameClassifier: GameClassifier,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filterType = MutableStateFlow(AppFilterType.ALL)
    val filterType: StateFlow<AppFilterType> = _filterType.asStateFlow()

    private val _installedApps = MutableStateFlow<List<AppItemUiModel>>(emptyList())
    private val _isLoading = MutableStateFlow(true)

    init {
        loadInstalledApps()
    }

    private val appRulesAndProfilesFlow = combine(
        appSettingsRepository.settings,
        appRuleRepository.getAllRules(),
        profileRepository.getAllProfiles()
    ) { settings, rules, profiles ->
        Triple(settings, rules, profiles)
    }

    private val appsAndFilterFlow = combine(
        _installedApps,
        _searchQuery,
        _filterType
    ) { apps, query, filter ->
        Triple(apps, query, filter)
    }

    val uiState: StateFlow<PerAppUiState> = combine(
        appRulesAndProfilesFlow,
        appsAndFilterFlow
    ) { (settings, rules, profiles), (apps, query, filter) ->
        val ruleMap = rules.associateBy { it.packageName }
        val mergedApps = apps.map { app ->
            val rule = ruleMap[app.packageName]
            if (rule != null) {
                app.copy(
                    configuredProfileId = rule.profileId,
                    isGame = rule.isGame,
                    targetRefreshRate = rule.targetRefreshRate,
                    dndEnabled = rule.dndEnabled
                )
            } else {
                app
            }
        }

        val filtered = mergedApps.filter { app ->
            val matchesQuery = query.isBlank() ||
                    app.appLabel.contains(query, ignoreCase = true) ||
                    app.packageName.contains(query, ignoreCase = true)

            val matchesFilter = when (filter) {
                AppFilterType.ALL -> true
                AppFilterType.GAMES -> app.isGame
                AppFilterType.CONFIGURED -> app.configuredProfileId != null
            }

            matchesQuery && matchesFilter
        }

        PerAppUiState(
            perAppProfilesEnabled = settings.perAppProfilesEnabled,
            apps = filtered,
            availableProfiles = if (profiles.isNotEmpty()) profiles else BuiltInProfiles.ALL_BUILT_IN,
            searchQuery = query,
            filterType = filter,
            isLoading = _isLoading.value
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PerAppUiState(isLoading = true)
    )

    fun loadInstalledApps() {
        viewModelScope.launch(ioDispatcher) {
            _isLoading.value = true
            val pm = context.packageManager
            val packages = try {
                pm.getInstalledApplications(PackageManager.GET_META_DATA)
            } catch (_: Exception) {
                emptyList<ApplicationInfo>()
            }

            val appList = packages
                .filter { it.packageName != context.packageName }
                .map { appInfo ->
                    val label = try {
                        pm.getApplicationLabel(appInfo).toString()
                    } catch (_: Exception) {
                        appInfo.packageName
                    }
                    val isGame = gameClassifier.isApplicationInfoGame(appInfo)

                    AppItemUiModel(
                        packageName = appInfo.packageName,
                        appLabel = label,
                        isGame = isGame
                    )
                }
                .sortedBy { it.appLabel.lowercase() }

            _installedApps.value = appList
            _isLoading.value = false
        }
    }

    fun setInstalledAppsForTesting(apps: List<AppItemUiModel>) {
        _installedApps.value = apps
        _isLoading.value = false
    }

    fun togglePerAppProfilesEnabled(enabled: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            appSettingsRepository.setPerAppProfilesEnabled(enabled)
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilterType(type: AppFilterType) {
        _filterType.value = type
    }

    fun saveAppRule(
        packageName: String,
        profileId: String,
        isGame: Boolean,
        targetRefreshRate: Int?,
        dndEnabled: Boolean
    ) {
        viewModelScope.launch(ioDispatcher) {
            val entity = AppRuleEntity(
                packageName = packageName,
                profileId = profileId,
                isGame = isGame,
                targetRefreshRate = targetRefreshRate,
                dndEnabled = dndEnabled
            )
            appRuleRepository.upsertRule(entity)
        }
    }

    fun removeAppRule(packageName: String) {
        viewModelScope.launch(ioDispatcher) {
            appRuleRepository.deleteRule(packageName)
        }
    }

    fun toggleGame(packageName: String, isGame: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            appRuleRepository.setGame(packageName, isGame)
        }
    }
}
