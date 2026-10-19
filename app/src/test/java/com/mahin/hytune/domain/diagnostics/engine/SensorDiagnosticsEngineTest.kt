package com.mahin.hytune.domain.diagnostics.engine

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
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
@Config(application = com.mahin.hytune.HyTuneApp::class)
class SensorDiagnosticsEngineTest {

    private lateinit var context: Context
    private lateinit var sensorManager: SensorManager
    private lateinit var engine: SensorDiagnosticsEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        engine = SensorDiagnosticsEngine(sensorManager)
    }

    @Test
    fun getMonitoredSensors_returnsConfiguredTargetSensors() {
        val sensors = engine.getMonitoredSensors()
        assertNotNull(sensors)
        assertEquals(7, sensors.size)

        val names = sensors.map { it.typeName }
        assertTrue(names.contains("Accelerometer"))
        assertTrue(names.contains("Gyroscope"))
        assertTrue(names.contains("Magnetometer"))
        assertTrue(names.contains("Ambient Light"))
        assertTrue(names.contains("Proximity"))
        assertTrue(names.contains("Barometer"))
        assertTrue(names.contains("Step Counter"))
    }
}
