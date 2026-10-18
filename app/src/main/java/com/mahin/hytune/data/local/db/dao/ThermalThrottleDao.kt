package com.mahin.hytune.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mahin.hytune.data.local.db.entity.ThermalThrottleEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ThermalThrottleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: ThermalThrottleEventEntity): Long

    @Query("SELECT * FROM thermal_throttle_events ORDER BY timestampEpochMs DESC")
    fun getAllEvents(): Flow<List<ThermalThrottleEventEntity>>

    @Query("SELECT * FROM thermal_throttle_events ORDER BY timestampEpochMs DESC LIMIT :limit")
    fun getRecentEvents(limit: Int): Flow<List<ThermalThrottleEventEntity>>

    @Query("DELETE FROM thermal_throttle_events")
    suspend fun clearAllEvents()
}
