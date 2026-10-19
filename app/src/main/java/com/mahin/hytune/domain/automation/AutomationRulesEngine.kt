package com.mahin.hytune.domain.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.mahin.hytune.data.local.db.dao.AutomationRuleDao
import com.mahin.hytune.domain.appmanager.AutoFreezeManager
import com.mahin.hytune.domain.automation.evaluator.RuleEvaluator
import com.mahin.hytune.domain.automation.model.AutomationRule
import com.mahin.hytune.domain.automation.model.RuleActionType
import com.mahin.hytune.domain.automation.model.SystemStateSnapshot
import com.mahin.hytune.domain.battery.BatteryRepository
import com.mahin.hytune.domain.battery.ChargeControlEngine
import com.mahin.hytune.domain.detection.ForegroundAppDetector
import com.mahin.hytune.domain.display.DisplayRefreshRateController
import com.mahin.hytune.domain.engine.ProfileEngine
import com.mahin.hytune.domain.profile.ProfileRepository
import com.mahin.hytune.domain.thermal.ThermalRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomationRulesEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val automationRuleDao: AutomationRuleDao,
    private val ruleEvaluator: RuleEvaluator,
    private val profileRepository: ProfileRepository,
    private val profileEngine: ProfileEngine,
    private val autoFreezeManager: AutoFreezeManager,
    private val chargeControlEngine: ChargeControlEngine,
    private val displayRefreshRateController: DisplayRefreshRateController,
    private val batteryRepository: BatteryRepository,
    private val thermalRepository: ThermalRepository,
    private val foregroundAppDetector: ForegroundAppDetector,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    var testScopeOverride: CoroutineScope? = null
    private val defaultScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val scope: CoroutineScope get() = testScopeOverride ?: defaultScope

    private var monitorJobs = mutableListOf<Job>()
    private var isReceiverRegistered = false

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private var previousState = SystemStateSnapshot()

    private val _currentSystemState = MutableStateFlow(SystemStateSnapshot())
    val currentSystemState: StateFlow<SystemStateSnapshot> = _currentSystemState.asStateFlow()

    private val _lastExecutedRules = MutableStateFlow<List<AutomationRule>>(emptyList())
    val lastExecutedRules: StateFlow<List<AutomationRule>> = _lastExecutedRules.asStateFlow()

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> onScreenStateChanged(screenOn = true)
                Intent.ACTION_SCREEN_OFF -> onScreenStateChanged(screenOn = false)
            }
        }
    }

    fun start() {
        if (_isRunning.value) return
        _isRunning.value = true

        registerScreenReceiver()

        // 1. Observe Battery state
        val batteryJob = scope.launch {
            batteryRepository.getBatteryStateFlow(pollIntervalMs = 2000L).collectLatest { batteryState ->
                val updated = _currentSystemState.value.copy(
                    batteryPercent = batteryState.levelPercent,
                    isCharging = batteryState.isCharging,
                    timestampEpochMs = System.currentTimeMillis()
                )
                evaluateState(updated)
            }
        }
        monitorJobs.add(batteryJob)

        // 2. Observe Thermal state
        val thermalJob = scope.launch {
            thermalRepository.observeThermalSnapshot(intervalMs = 2000L).collectLatest { thermalSnapshot ->
                val peakTemp = thermalSnapshot.hottestZone?.tempC ?: 0f
                val updated = _currentSystemState.value.copy(
                    temperatureC = peakTemp,
                    timestampEpochMs = System.currentTimeMillis()
                )
                evaluateState(updated)
            }
        }
        monitorJobs.add(thermalJob)

        // 3. Observe Foreground App changes
        val foregroundJob = scope.launch {
            foregroundAppDetector.currentForegroundApp.collectLatest { foregroundInfo ->
                val pkg = foregroundInfo?.packageName.orEmpty()
                val updated = _currentSystemState.value.copy(
                    foregroundPackage = pkg,
                    timestampEpochMs = System.currentTimeMillis()
                )
                evaluateState(updated)
            }
        }
        monitorJobs.add(foregroundJob)
    }

    fun stop() {
        _isRunning.value = false
        monitorJobs.forEach { it.cancel() }
        monitorJobs.clear()
        unregisterScreenReceiver()
    }

    fun onScreenStateChanged(screenOn: Boolean) {
        val updated = _currentSystemState.value.copy(
            screenOn = screenOn,
            timestampEpochMs = System.currentTimeMillis()
        )
        scope.launch {
            evaluateState(updated)
        }
    }

    suspend fun evaluateState(newState: SystemStateSnapshot) = withContext(ioDispatcher) {
        val rules = automationRuleDao.getEnabledRulesSync().map { it.toDomain() }
        if (rules.isEmpty()) {
            previousState = newState
            _currentSystemState.value = newState
            return@withContext
        }

        val winningRules = ruleEvaluator.evaluateWinningRules(
            rules = rules,
            prevState = previousState,
            currState = newState
        )

        for (rule in winningRules) {
            executeRuleAction(rule)
            automationRuleDao.updateLastTriggered(rule.id, System.currentTimeMillis())
        }

        if (winningRules.isNotEmpty()) {
            _lastExecutedRules.value = winningRules
        }

        previousState = newState
        _currentSystemState.value = newState
    }

    private suspend fun executeRuleAction(rule: AutomationRule) {
        when (rule.actionType) {
            RuleActionType.APPLY_PROFILE -> {
                val profileId = rule.actionValue.trim()
                if (profileId.isNotEmpty()) {
                    val profile = profileRepository.getProfileById(profileId).getOrNull()
                    if (profile != null) {
                        profileEngine.applyProfile(profile)
                    }
                }
            }
            RuleActionType.FREEZE_BACKGROUND_APPS -> {
                autoFreezeManager.handleScreenOff(overrideDelaySeconds = 0)
            }
            RuleActionType.SET_CHARGE_CURRENT_LIMIT -> {
                val limitMa = rule.actionValue.toIntOrNull() ?: 1500
                chargeControlEngine.setChargeCurrentLimit(limitMa)
            }
            RuleActionType.SET_REFRESH_RATE -> {
                val hz = rule.actionValue.toIntOrNull() ?: 60
                displayRefreshRateController.setRefreshRate(hz)
            }
        }
    }

    private fun registerScreenReceiver() {
        if (!isReceiverRegistered) {
            try {
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_SCREEN_OFF)
                }
                context.registerReceiver(screenReceiver, filter)
                isReceiverRegistered = true
            } catch (_: Exception) {
                // Ignore in testing environments if broadcast not supported
            }
        }
    }

    private fun unregisterScreenReceiver() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(screenReceiver)
            } catch (_: Exception) {
                // Ignore
            }
            isReceiverRegistered = false
        }
    }
}
