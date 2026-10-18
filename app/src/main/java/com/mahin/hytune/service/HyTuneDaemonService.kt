package com.mahin.hytune.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.mahin.hytune.data.local.datastore.AppSettingsRepository
import com.mahin.hytune.domain.detection.ForegroundAppDetector
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

    private var serviceScope: CoroutineScope? = null

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
                observeActiveProfileAndForegroundApp()
            }
            ACTION_STOP -> {
                daemonController.updateState(DaemonState.STOPPING)
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
            foregroundAppDetector.startDetectionStream().collectLatest {}
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope?.cancel()
        serviceScope = null
        daemonController.updateState(DaemonState.STOPPED)
        super.onDestroy()
    }
}
