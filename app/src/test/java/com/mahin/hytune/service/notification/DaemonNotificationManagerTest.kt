package com.mahin.hytune.service.notification

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DaemonNotificationManagerTest {

    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager
    private lateinit var daemonNotificationManager: DaemonNotificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        daemonNotificationManager = DaemonNotificationManager(context)
    }

    @Test
    fun createNotificationChannel_createsLowImportanceChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = notificationManager.getNotificationChannel(DaemonNotificationManager.CHANNEL_ID)
            assertNotNull(channel)
            assertEquals("HyTune Daemon", channel.name.toString())
            assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
        }
    }

    @Test
    fun buildForegroundNotification_containsRequiredAttributesAndActions() {
        val notification = daemonNotificationManager.buildForegroundNotification(
            activeProfileName = "Performance",
            statusSummary = "Thermal 42C"
        )

        assertNotNull(notification)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            assertEquals(DaemonNotificationManager.CHANNEL_ID, notification.channelId)
        }
        assertTrue((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0)
        assertNotNull(notification.contentIntent)
        assertNotNull(notification.actions)
        assertTrue(notification.actions.isNotEmpty())
        assertEquals("Stop", notification.actions[0].title.toString())
        assertEquals("Next Profile", notification.actions[1].title.toString())
    }

    @Test
    fun updateNotification_executesWithoutError() {
        daemonNotificationManager.updateNotification(
            activeProfileName = "Powersave",
            statusSummary = "Idle"
        )
    }
}
