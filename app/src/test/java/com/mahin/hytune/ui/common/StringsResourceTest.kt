package com.mahin.hytune.ui.common

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mahin.hytune.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StringsResourceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun testCoreStringsResolve() {
        assertEquals("HyTune", context.getString(R.string.app_name))
        assertEquals("Dashboard", context.getString(R.string.nav_dashboard))
        assertEquals("CPU", context.getString(R.string.nav_cpu))
        assertEquals("GPU", context.getString(R.string.nav_gpu))
        assertEquals("Battery", context.getString(R.string.nav_battery))
        assertEquals("Diagnostics", context.getString(R.string.nav_diagnostics))
    }

    @Test
    fun testFormattedStringsResolve() {
        val formattedProfile = context.getString(R.string.profile_applied_success, "Performance")
        assertEquals("Successfully applied profile Performance", formattedProfile)

        val countdown = context.getString(R.string.action_revert_countdown, 10)
        assertEquals("Reverting in 10 seconds", countdown)

        val navTab = context.getString(R.string.a11y_nav_tab, "Dashboard")
        assertEquals("Dashboard tab", navTab)

        val metricAnnouncement = context.getString(R.string.a11y_metric_announcement, "CPU Load", "45%")
        assertEquals("CPU Load: 45%", metricAnnouncement)
    }
}
