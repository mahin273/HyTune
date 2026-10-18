package com.mahin.hytune.domain.detection.strategy

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import com.mahin.hytune.domain.detection.model.DetectionSource
import com.mahin.hytune.domain.detection.model.ForegroundInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class UsageStatsForegroundStrategy @Inject constructor(
    @ApplicationContext private val context: Context
) : ForegroundDetectionStrategy {

    companion object {
        private const val LOOKBACK_WINDOW_MS = 10_000L
    }

    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    override suspend fun detect(): ForegroundInfo? {
        if (!hasUsageStatsPermission()) return null

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null

        val now = System.currentTimeMillis()
        val events = try {
            usageStatsManager.queryEvents(now - LOOKBACK_WINDOW_MS, now)
        } catch (_: Exception) {
            null
        } ?: return null

        var latestPackageName: String? = null
        var latestClassName: String? = null
        var latestTimestamp = 0L
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                if (event.timeStamp >= latestTimestamp) {
                    latestPackageName = event.packageName
                    latestClassName = event.className
                    latestTimestamp = event.timeStamp
                }
            }
        }

        return latestPackageName?.let { pkg ->
            ForegroundInfo(
                packageName = pkg,
                className = latestClassName,
                source = DetectionSource.USAGE_STATS,
                timestampEpochMs = now
            )
        }
    }
}
