package com.mahin.hytune.domain.diagnostics.engine

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.mahin.hytune.domain.diagnostics.model.SensorDiagnosticInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SensorDiagnosticsEngine @Inject constructor(
    private val sensorManager: SensorManager
) {

    private val targetSensorTypes = listOf(
        Sensor.TYPE_ACCELEROMETER to "Accelerometer",
        Sensor.TYPE_GYROSCOPE to "Gyroscope",
        Sensor.TYPE_MAGNETIC_FIELD to "Magnetometer",
        Sensor.TYPE_LIGHT to "Ambient Light",
        Sensor.TYPE_PROXIMITY to "Proximity",
        Sensor.TYPE_PRESSURE to "Barometer",
        Sensor.TYPE_STEP_COUNTER to "Step Counter"
    )

    fun getMonitoredSensors(): List<SensorDiagnosticInfo> {
        return targetSensorTypes.map { (type, typeName) ->
            val sensor = sensorManager.getDefaultSensor(type)
            if (sensor != null) {
                SensorDiagnosticInfo(
                    type = type,
                    typeName = typeName,
                    name = sensor.name,
                    vendor = sensor.vendor,
                    version = sensor.version,
                    powerMa = sensor.power,
                    resolution = sensor.resolution,
                    maxRange = sensor.maximumRange,
                    isPresent = true
                )
            } else {
                SensorDiagnosticInfo(
                    type = type,
                    typeName = typeName,
                    name = "Unavailable",
                    vendor = "None",
                    version = 0,
                    powerMa = 0f,
                    resolution = 0f,
                    maxRange = 0f,
                    isPresent = false
                )
            }
        }
    }

    fun observeSensorData(
        sensorType: Int,
        samplingPeriodUs: Int = SensorManager.SENSOR_DELAY_NORMAL
    ): Flow<FloatArray> = callbackFlow {
        val sensor = sensorManager.getDefaultSensor(sensorType)
        if (sensor == null) {
            close()
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let {
                    trySend(it.values.clone())
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                // Not needed for telemetry readout
            }
        }

        val registered = sensorManager.registerListener(listener, sensor, samplingPeriodUs)
        if (!registered) {
            close()
            return@callbackFlow
        }

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }
}
