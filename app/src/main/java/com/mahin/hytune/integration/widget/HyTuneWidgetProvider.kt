package com.mahin.hytune.integration.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.mahin.hytune.MainActivity
import com.mahin.hytune.R
import com.mahin.hytune.domain.battery.BatteryRepository
import com.mahin.hytune.domain.engine.ProfileEngine
import com.mahin.hytune.domain.profile.BuiltInProfiles
import com.mahin.hytune.domain.profile.ProfileRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HyTuneWidgetProvider : AppWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun profileRepository(): ProfileRepository
        fun profileEngine(): ProfileEngine
        fun batteryRepository(): BatteryRepository
    }

    companion object {
        const val ACTION_WIDGET_CYCLE_PROFILE = "com.mahin.hytune.action.WIDGET_CYCLE_PROFILE"

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            profileName: String = "Balance",
            batteryTempText: String = "Battery: Normal"
        ) {
            try {
                val views = RemoteViews(context.packageName, R.layout.widget_hytune)

                views.setTextViewText(R.id.widget_profile_name, profileName)
                views.setTextViewText(R.id.widget_telemetry_info, batteryTempText)

                // Open HyTune Activity
                val openAppIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val openPendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    openAppIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_btn_open, openPendingIntent)

                // Cycle Profile Action
                val cycleIntent = Intent(context, HyTuneWidgetProvider::class.java).apply {
                    action = ACTION_WIDGET_CYCLE_PROFILE
                }
                val cyclePendingIntent = PendingIntent.getBroadcast(
                    context,
                    1,
                    cycleIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_btn_cycle, cyclePendingIntent)

                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (_: Throwable) {
                // Ignore layout inflation or manager errors in test/detached environments
            }
        }

        fun updateAllWidgets(
            context: Context,
            dispatcher: CoroutineDispatcher = Dispatchers.IO
        ): Job? {
            val appWidgetManager = try {
                AppWidgetManager.getInstance(context)
            } catch (_: Throwable) {
                return null
            }
            val componentName = ComponentName(context, HyTuneWidgetProvider::class.java)
            val appWidgetIds = try {
                appWidgetManager.getAppWidgetIds(componentName)
            } catch (_: Throwable) {
                intArrayOf()
            }
            if (appWidgetIds.isEmpty()) return null

            val scope = CoroutineScope(dispatcher + CoroutineExceptionHandler { _, _ -> })
            return scope.launch {
                try {
                    val entryPoint = EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        WidgetEntryPoint::class.java
                    )
                    val activeProfile = entryPoint.profileRepository().getActiveProfile().first()
                    val batteryState = try {
                        entryPoint.batteryRepository().getBatteryState()
                    } catch (_: Throwable) {
                        null
                    }
                    val batteryText = if (batteryState != null) {
                        "Battery: ${batteryState.temperatureFormatted} | ${batteryState.levelPercent}%"
                    } else {
                        "Battery: Monitored"
                    }

                    for (appWidgetId in appWidgetIds) {
                        updateAppWidget(
                            context = context,
                            appWidgetManager = appWidgetManager,
                            appWidgetId = appWidgetId,
                            profileName = activeProfile.name,
                            batteryTempText = batteryText
                        )
                    }
                } catch (_: Throwable) {
                    // Fallback to basic view if DI or db is temporarily unavailable
                    for (appWidgetId in appWidgetIds) {
                        updateAppWidget(context, appWidgetManager, appWidgetId)
                    }
                }
            }
        }
    }

    var coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = try {
            goAsync()
        } catch (_: Throwable) {
            null
        }
        val scope = CoroutineScope(coroutineDispatcher + CoroutineExceptionHandler { _, _ -> })

        scope.launch {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    WidgetEntryPoint::class.java
                )
                val activeProfile = entryPoint.profileRepository().getActiveProfile().first()
                val batteryState = try {
                    entryPoint.batteryRepository().getBatteryState()
                } catch (_: Throwable) {
                    null
                }
                val batteryText = if (batteryState != null) {
                    "Battery: ${batteryState.temperatureFormatted} | ${batteryState.levelPercent}%"
                } else {
                    "Battery: Monitored"
                }

                for (appWidgetId in appWidgetIds) {
                    updateAppWidget(
                        context = context,
                        appWidgetManager = appWidgetManager,
                        appWidgetId = appWidgetId,
                        profileName = activeProfile.name,
                        batteryTempText = batteryText
                    )
                }
            } catch (_: Throwable) {
                for (appWidgetId in appWidgetIds) {
                    updateAppWidget(context, appWidgetManager, appWidgetId)
                }
            } finally {
                pendingResult?.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_WIDGET_CYCLE_PROFILE) {
            val pendingResult = try {
                goAsync()
            } catch (_: Throwable) {
                null
            }
            val scope = CoroutineScope(coroutineDispatcher + CoroutineExceptionHandler { _, _ -> })

            scope.launch {
                try {
                    val entryPoint = EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        WidgetEntryPoint::class.java
                    )
                    val active = entryPoint.profileRepository().getActiveProfile().first()
                    val rotatingList = listOf(
                        BuiltInProfiles.BALANCE,
                        BuiltInProfiles.PERFORMANCE,
                        BuiltInProfiles.FAST,
                        BuiltInProfiles.POWERSAVE
                    )
                    val currentIndex = rotatingList.indexOfFirst { it.id.equals(active.id, ignoreCase = true) }
                    val nextIndex = if (currentIndex >= 0) (currentIndex + 1) % rotatingList.size else 0
                    val nextProfile = rotatingList[nextIndex]

                    entryPoint.profileEngine().applyProfile(nextProfile)
                    entryPoint.profileRepository().setActiveProfile(nextProfile.id)

                    updateAllWidgets(context, coroutineDispatcher)
                } catch (_: Throwable) {
                    // Ignore transient background update failures
                } finally {
                    pendingResult?.finish()
                }
            }
            return
        }

        super.onReceive(context, intent)
    }
}
