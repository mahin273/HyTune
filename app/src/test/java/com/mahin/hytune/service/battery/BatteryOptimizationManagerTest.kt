package com.mahin.hytune.service.battery

import android.content.Context
import android.content.Intent
import android.provider.Settings
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
class BatteryOptimizationManagerTest {

    private lateinit var context: Context
    private lateinit var batteryOptimizationManager: BatteryOptimizationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        batteryOptimizationManager = BatteryOptimizationManager(context)
    }

    @Test
    fun isIgnoringBatteryOptimizations_returnsValidBoolean() {
        // Under Robolectric, powerManager provides default value
        val isIgnored = batteryOptimizationManager.isIgnoringBatteryOptimizations()
        assertNotNull(isIgnored)
    }

    @Test
    fun createRequestIgnoreBatteryOptimizationsIntent_buildsProperIntent() {
        val intent = batteryOptimizationManager.createRequestIgnoreBatteryOptimizationsIntent()
        assertEquals(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, intent.action)
        assertEquals("package:${context.packageName}", intent.dataString)
        assertTrue((intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
    }

    @Test
    fun createBatteryOptimizationSettingsIntent_buildsProperIntent() {
        val intent = batteryOptimizationManager.createBatteryOptimizationSettingsIntent()
        assertEquals(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, intent.action)
        assertTrue((intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
    }
}
