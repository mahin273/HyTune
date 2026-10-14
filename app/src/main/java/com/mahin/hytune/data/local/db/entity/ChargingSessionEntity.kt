package com.mahin.hytune.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "charging_sessions")
data class ChargingSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTimestampEpochMs: Long,
    val endTimestampEpochMs: Long? = null,
    val startPercent: Int,
    val endPercent: Int? = null,
    val avgPowerWatts: Float = 0f,
    val peakTemperatureC: Float = 0f
)
