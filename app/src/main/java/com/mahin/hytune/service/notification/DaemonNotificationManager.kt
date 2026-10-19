package com.mahin.hytune.service.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mahin.hytune.MainActivity
import com.mahin.hytune.service.HyTuneDaemonService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DaemonNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        const val CHANNEL_ID = "hytune_daemon_channel"
        const val CHANNEL_NAME = "HyTune Daemon"
        const val CHANNEL_DESCRIPTION = "Ongoing background daemon service notification showing active profile and system status"
        const val BOOT_CHANNEL_ID = "hytune_boot_channel"
        const val BOOT_CHANNEL_NAME = "HyTune Boot Apply"
        const val BOOT_CHANNEL_DESCRIPTION = "Notifications reporting boot apply status and safety guard events"
        const val NOTIFICATION_ID = 1001
        const val BOOT_NOTIFICATION_ID = 1002
        const val REQUEST_CODE_CONTENT = 101
        const val REQUEST_CODE_STOP = 102
    }

    init {
        createNotificationChannel()
    }

    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val daemonChannel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = CHANNEL_DESCRIPTION
                setShowBadge(false)
            }
            val bootChannel = NotificationChannel(
                BOOT_CHANNEL_ID,
                BOOT_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = BOOT_CHANNEL_DESCRIPTION
                setShowBadge(true)
            }
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannels(listOf(daemonChannel, bootChannel))
        }
    }

    fun buildForegroundNotification(
        activeProfileName: String,
        statusSummary: String? = null
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_CONTENT,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            context,
            REQUEST_CODE_STOP,
            Intent(context, HyTuneDaemonService::class.java).apply {
                action = HyTuneDaemonService.ACTION_STOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val detailText = if (!statusSummary.isNullOrBlank()) {
            "Profile: $activeProfileName | $statusSummary"
        } else {
            "Active Profile: $activeProfileName"
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("HyTune Daemon Active")
            .setContentText(detailText)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(contentIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopIntent
            )
            .build()
    }

    fun updateNotification(activeProfileName: String, statusSummary: String? = null) {
        try {
            val notification = buildForegroundNotification(activeProfileName, statusSummary)
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: Throwable) {
            // Suppress notification errors in test environments or when POST_NOTIFICATIONS is denied
        }
    }

    fun postBootNotification(title: String, message: String, isWarning: Boolean = false) {
        try {
            createNotificationChannel()
            val contentIntent = PendingIntent.getActivity(
                context,
                REQUEST_CODE_CONTENT,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val builder = NotificationCompat.Builder(context, BOOT_CHANNEL_ID)
                .setSmallIcon(if (isWarning) android.R.drawable.stat_notify_error else android.R.drawable.stat_notify_sync)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(if (isWarning) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)

            NotificationManagerCompat.from(context).notify(BOOT_NOTIFICATION_ID, builder.build())
        } catch (_: Throwable) {
            // Suppress notification errors in test environments or when POST_NOTIFICATIONS is denied
        }
    }
}
