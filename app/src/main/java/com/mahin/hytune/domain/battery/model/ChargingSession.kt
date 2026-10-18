package com.mahin.hytune.domain.battery.model

import com.mahin.hytune.data.local.db.entity.ChargingSessionEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ChargingSession(
    val id: Long = 0,
    val startTimestampEpochMs: Long,
    val endTimestampEpochMs: Long? = null,
    val startPercent: Int,
    val endPercent: Int? = null,
    val avgPowerWatts: Float = 0f,
    val peakTemperatureC: Float = 0f
) {
    val durationMs: Long
        get() = (endTimestampEpochMs ?: System.currentTimeMillis()) - startTimestampEpochMs

    val percentGained: Int
        get() = (endPercent ?: startPercent) - startPercent

    val durationFormatted: String
        get() {
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val hours = minutes / 60
            val remMin = minutes % 60
            return if (hours > 0) "${hours}h ${remMin}m" else "${remMin}m"
        }

    val startTimeFormatted: String
        get() {
            val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            return sdf.format(Date(startTimestampEpochMs))
        }

    val avgPowerFormatted: String
        get() = String.format("%.1f W", avgPowerWatts)

    val peakTempFormatted: String
        get() = String.format("%.1f C", peakTemperatureC)

    companion object {
        fun fromEntity(entity: ChargingSessionEntity): ChargingSession {
            return ChargingSession(
                id = entity.id,
                startTimestampEpochMs = entity.startTimestampEpochMs,
                endTimestampEpochMs = entity.endTimestampEpochMs,
                startPercent = entity.startPercent,
                endPercent = entity.endPercent,
                avgPowerWatts = entity.avgPowerWatts,
                peakTemperatureC = entity.peakTemperatureC
            )
        }
    }
}
