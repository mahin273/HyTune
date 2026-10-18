package com.mahin.hytune.domain.profile

import android.content.Context
import com.mahin.hytune.data.local.datastore.AppSettingsRepository
import com.mahin.hytune.domain.detection.ForegroundAppDetector
import com.mahin.hytune.domain.detection.GameClassifier
import com.mahin.hytune.domain.detection.model.ForegroundInfo
import com.mahin.hytune.domain.display.DisplayRefreshRateController
import com.mahin.hytune.domain.engine.ProfileEngine
import com.mahin.hytune.domain.notification.DndController
import com.mahin.hytune.domain.profile.model.PerAppState
import com.mahin.hytune.domain.profile.model.TuningProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PerAppProfileCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val foregroundAppDetector: ForegroundAppDetector,
    private val profileRepository: ProfileRepository,
    private val profileEngine: ProfileEngine,
    private val appRuleRepository: AppRuleRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val gameClassifier: GameClassifier,
    private val displayController: DisplayRefreshRateController,
    private val dndController: DndController,
    private val chargeControlEngine: com.mahin.hytune.domain.battery.ChargeControlEngine? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val _activePerAppState = MutableStateFlow<PerAppState?>(null)
    val activePerAppState: StateFlow<PerAppState?> = _activePerAppState.asStateFlow()

    private var baseProfileId: String? = null
    private var originalPeakRefreshRate: Float? = null
    private var originalMinRefreshRate: Float? = null
    private var originalZenMode: String? = null
    private var activePackageName: String? = null
    private var perAppBypassActive: Boolean = false

    suspend fun handleForegroundApp(info: ForegroundInfo): Unit = withContext(ioDispatcher) {
        val packageName = info.packageName

        if (info.isHomeLauncher) {
            if (_activePerAppState.value != null) {
                revertState()
            }
            return@withContext
        }

        if (packageName == context.packageName) {
            return@withContext
        }

        if (packageName == activePackageName) {
            return@withContext
        }

        val settings = appSettingsRepository.settings.first()
        if (!settings.perAppProfilesEnabled) {
            if (_activePerAppState.value != null) {
                revertState()
            }
            return@withContext
        }

        val rule = appRuleRepository.getRuleDirect(packageName)
        if (rule != null) {
            applyPerAppRule(
                packageName = packageName,
                profileId = rule.profileId,
                isGame = rule.isGame,
                refreshRate = rule.targetRefreshRate,
                dnd = rule.dndEnabled,
                bypassCharging = rule.bypassChargingEnabled
            )
        } else if (gameClassifier.isGame(packageName)) {
            applyPerAppRule(
                packageName = packageName,
                profileId = BuiltInProfiles.PERFORMANCE.id,
                isGame = true,
                refreshRate = null,
                dnd = false,
                bypassCharging = false
            )
        } else {
            if (_activePerAppState.value != null) {
                revertState()
            }
        }
    }

    private suspend fun applyPerAppRule(
        packageName: String,
        profileId: String,
        isGame: Boolean,
        refreshRate: Int?,
        dnd: Boolean,
        bypassCharging: Boolean = false
    ) {
        if (baseProfileId == null) {
            baseProfileId = profileRepository.getActiveProfileId().first()
            originalPeakRefreshRate = displayController.getCurrentPeakRefreshRate()
            originalMinRefreshRate = displayController.getCurrentMinRefreshRate()
            originalZenMode = dndController.getCurrentZenMode()
        }

        val profile = findProfile(profileId)
        if (profile != null) {
            profileEngine.applyProfile(profile)
        } else {
            profileRepository.setActiveProfile(profileId)
        }

        if (refreshRate != null) {
            displayController.setRefreshRate(refreshRate)
        } else if (originalPeakRefreshRate != null) {
            displayController.restoreRefreshRate(originalPeakRefreshRate, originalMinRefreshRate)
        }

        if (dnd) {
            dndController.enableDnd()
        } else if (originalZenMode != null) {
            dndController.restoreDnd(originalZenMode)
        }

        if (bypassCharging) {
            chargeControlEngine?.setBypassCharging(true)
            perAppBypassActive = true
        } else if (perAppBypassActive) {
            chargeControlEngine?.setBypassCharging(false)
            perAppBypassActive = false
        }

        activePackageName = packageName
        _activePerAppState.value = PerAppState(
            packageName = packageName,
            profileId = profileId,
            isGame = isGame,
            targetRefreshRate = refreshRate,
            dndEnabled = dnd,
            baseProfileId = baseProfileId ?: "balance"
        )
    }

    suspend fun revertState(): Unit = withContext(ioDispatcher) {
        val base = baseProfileId
        if (base != null) {
            val baseProfile = findProfile(base)
            if (baseProfile != null) {
                profileEngine.applyProfile(baseProfile)
            } else {
                profileRepository.setActiveProfile(base)
            }
        }

        displayController.restoreRefreshRate(originalPeakRefreshRate, originalMinRefreshRate)
        dndController.restoreDnd(originalZenMode)

        if (perAppBypassActive) {
            chargeControlEngine?.setBypassCharging(false)
            perAppBypassActive = false
        }

        baseProfileId = null
        originalPeakRefreshRate = null
        originalMinRefreshRate = null
        originalZenMode = null
        activePackageName = null
        _activePerAppState.value = null
    }

    fun startObserving(): Flow<PerAppState?> = flow {
        foregroundAppDetector.startDetectionStream().collect { info ->
            handleForegroundApp(info)
            emit(_activePerAppState.value)
        }
    }.flowOn(ioDispatcher)

    private suspend fun findProfile(profileId: String): TuningProfile? {
        val all = profileRepository.getAllProfiles().first()
        return all.find { it.id.equals(profileId, ignoreCase = true) }
            ?: BuiltInProfiles.findById(profileId)
    }
}
