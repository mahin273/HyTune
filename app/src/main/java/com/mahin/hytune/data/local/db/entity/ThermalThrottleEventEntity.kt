package com.mahin.hytune.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mahin.hytune.domain.thermal.model.ThermalThrottleEvent

@Entity(
    tableName = "thermal_throttle_events",
    indices = [Index(value = ["timestampEpochMs"])]
)
data class ThermalThrottleEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestampEpochMs: Long,
    val zoneName: String,
    val temperatureC: Float,
    val coolingDeviceState: String,
    val triggerReason: String
) {
    fun toDomain(): ThermalThrottleEvent = ThermalThrottleEvent(
        id = id,
        timestampEpochMs = timestampEpochMs,
        zoneName = zoneName,
        temperatureC = temperatureC,
        coolingDeviceState = coolingDeviceState,
        triggerReason = triggerReason
    )
}

fun ThermalThrottleEvent.toEntity(): ThermalThrottleEventEntity = ThermalThrottleEventEntity(
    id = id,
    timestampEpochMs = timestampEpochMs,
    zoneName = zoneName,
    temperatureC = temperatureC,
    coolingDeviceState = coolingDeviceState,
    triggerReason = triggerReason
)
