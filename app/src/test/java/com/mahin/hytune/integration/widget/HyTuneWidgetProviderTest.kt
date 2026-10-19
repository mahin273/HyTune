package com.mahin.hytune.integration.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mahin.hytune.R
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = com.mahin.hytune.HyTuneApp::class)
class HyTuneWidgetProviderTest {

    private lateinit var context: Context
    private lateinit var appWidgetManager: AppWidgetManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        appWidgetManager = AppWidgetManager.getInstance(context)
    }

    @Test
    fun updateAppWidget_createsRemoteViewsWithoutCrash() {
        assertNotNull(appWidgetManager)

        val shadowAppWidgetManager = Shadows.shadowOf(appWidgetManager)
        val widgetId = shadowAppWidgetManager.createWidget(
            HyTuneWidgetProvider::class.java,
            R.layout.widget_hytune
        )

        HyTuneWidgetProvider.updateAppWidget(
            context = context,
            appWidgetManager = appWidgetManager,
            appWidgetId = widgetId,
            profileName = "Performance",
            batteryTempText = "Battery: 34 C | Good"
        )

        val view = shadowAppWidgetManager.getViewFor(widgetId)
        assertNotNull(view)
    }
}
