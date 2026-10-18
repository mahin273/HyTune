package com.mahin.hytune.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.mahin.hytune.data.local.datastore.AppSettingsRepository
import com.mahin.hytune.domain.detection.ForegroundAppDetector
import com.mahin.hytune.domain.profile.PerAppProfileCoordinator
import com.mahin.hytune.domain.profile.ProfileRepository
import com.mahin.hytune.domain.safety.SafetyManager
import com.mahin.hytune.service.model.DaemonState
import com.mahin.hytune.service.notification.DaemonNotificationManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class HyTuneDaemonService : Service() {

    companion object {
        const val ACTION_START = "com.mahin.hytune.action.START_DAEMON"
        const val ACTION_STOP = "com.mahin.hytune.action.STOP_DAEMON"
        const val ACTION_SWITCH_PROFILE = "com.mahin.hytune.action.SWITCH_PROFILE"
        const val EXTRA_PROFILE_ID = "com.mahin.hytune.extra.PROFILE_ID"
    }

    @Inject
    lateinit var daemonNotificationManager: DaemonNotificationManager

    @Inject
    lateinit var daemonController: DaemonController

    @Inject
    lateinit var appSettingsRepository: AppSettingsRepository

    @Inject
    lateinit var safetyManager: SafetyManager

    @Inject
    lateinit var profileRepository: ProfileRepository

    @Inject
    lateinit var foregroundAppDetector: ForegroundAppDetector

    @Inject
    lateinit var perAppProfileCoordinator: PerAppProfileCoordinator

    @Inject
    lateinit var autoFreezeManager: com.mahin.hytune.domain.appmanager.AutoFreezeManager

    @Inject
    lateinit var chargingSessionTracker: com.mahin.hytune.domain.battery.ChargingSessionTracker

    @Inject
    lateinit var chargeControlEngine: com.mahin.hytune.domain.battery.ChargeControlEngine

    @Inject
    lateinit var thermalRepository: com.mahin.hytune.domain.thermal.ThermalRepository

    @Inject
    lateinit var batteryRepository: com.mahin.hytune.domain.battery.BatteryRepository

    @Inject
    lateinit var floatingOverlayManager: com.mahin.hytune.domain.overlay.FloatingOverlayManager

    private var serviceScope: CoroutineScope? = null
    private var thermalMonitorJob: kotlinx.coroutines.Job? = null
    private var chargeGuardJob: kotlinx.coroutines.Job? = null

    override fun onCreate() {
        super.onCreate()
        serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        daemonNotificationManager.createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_START -> {
                startForegroundWithNotification()
                daemonController.updateState(DaemonState.RUNNING)
                autoFreezeManager.startMonitoring()
                chargingSessionTracker.startMonitoring()
                observeActiveProfileAndForegroundApp()
                startThermalAndChargeMonitoring()
                startOverlayIfConfigured()
            }
            ACTION_STOP -> {
                daemonController.updateState(DaemonState.STOPPING)
                autoFreezeManager.stopMonitoring()
                chargingSessionTracker.stopMonitoring()
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                daemonController.updateState(DaemonState.STOPPED)
            }
            ACTION_SWITCH_PROFILE -> {
                val profileId = intent?.getStringExtra(EXTRA_PROFILE_ID)
                if (!profileId.isNullOrBlank()) {
                    switchProfile(profileId)
                }
            }
        }

        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val initialNotification = daemonNotificationManager.buildForegroundNotification(
            activeProfileName = "Starting..."
        )

        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }

        ServiceCompat.startForeground(
            this,
            DaemonNotificationManager.NOTIFICATION_ID,
            initialNotification,
            foregroundType
        )
    }

    private fun observeActiveProfileAndForegroundApp() {
        serviceScope?.launch {
            kotlinx.coroutines.flow.combine(
                profileRepository.getActiveProfile(),
                foregroundAppDetector.currentForegroundApp
            ) { profile, app ->
                val summary = if (app != null && !app.isHomeLauncher) {
                    "App: ${app.packageName}"
                } else null
                daemonNotificationManager.updateNotification(
                    activeProfileName = profile.name,
                    statusSummary = summary
                )
            }.collectLatest {}
        }

        serviceScope?.launch {
            perAppProfileCoordinator.startObserving().collectLatest {}
        }
    }

    private fun startThermalAndChargeMonitoring() {
        thermalMonitorJob = serviceScope?.launch {
            var wasThrottling = false
            thermalRepository.observeThermalSnapshot(2000L).collectLatest { snapshot ->
                if (snapshot.isThrottling && !wasThrottling) {
                    wasThrottling = true
                    val hottest = snapshot.hottestZone
                    val cdevSummary = snapshot.coolingDevices
                        .filter { it.isThrottling }
                        .joinToString { "${it.name}:${it.curState}" }
                        .ifEmpty { "Trip point exceeded" }

                    thermalRepository.logThrottleEvent(
                        com.mahin.hytune.domain.thermal.model.ThermalThrottleEvent(
                            timestampEpochMs = System.currentTimeMillis(),
                            zoneName = hottest?.name ?: "Unknown",
                            temperatureC = hottest?.tempC ?: 0f,
                            coolingDeviceState = cdevSummary,
                            triggerReason = "Thermal throttle threshold exceeded"
                        )
                    )
                } else if (!snapshot.isThrottling) {
                    wasThrottling = false
                }
            }
        }

        chargeGuardJob = serviceScope?.launch {
            kotlinx.coroutines.flow.combine(
                appSettingsRepository.settings,
                batteryRepository.getBatteryStateFlow(2000L)
            ) { settings, battery ->
                if (settings.chargeLimitEnabled) {
                    if (battery.levelPercent >= settings.chargeLimitPercent) {
                        chargeControlEngine.setBypassCharging(true)
                    } else if (battery.levelPercent <= (settings.chargeLimitPercent - 3)) {
                        chargeControlEngine.setBypassCharging(false)
                    }
                }
            }.collectLatest {}
        }
    }

    private fun switchProfile(profileId: String) {
        serviceScope?.launch {
            val profile = profileRepository.getProfileById(profileId).getOrNull()
            if (profile != null) {
                safetyManager.applyProfileSafely(profile)
                profileRepository.setActiveProfile(profile.id)
            }
        }
    }

    private fun startOverlayIfConfigured() {
        serviceScope?.launch {
            val settings = appSettingsRepository.settings.first()
            if (settings.floatingMonitorEnabled && floatingOverlayManager.canDrawOverlays()) {
                floatingOverlayManager.showOverlay()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        floatingOverlayManager.hideOverlay()
        thermalMonitorJob?.cancel()
        thermalMonitorJob = null
        chargeGuardJob?.cancel()
        chargeGuardJob = null
        serviceScope?.cancel()
        serviceScope = null
        autoFreezeManager.stopMonitoring()
        chargingSessionTracker.stopMonitoring()
        daemonController.updateState(DaemonState.STOPPED)
        super.onDestroy()
    }
}
