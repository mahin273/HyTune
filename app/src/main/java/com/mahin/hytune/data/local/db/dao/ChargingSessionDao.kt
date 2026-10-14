package com.mahin.hytune.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mahin.hytune.data.local.db.entity.ChargingSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChargingSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChargingSessionEntity): Long

    @Update
    suspend fun updateSession(session: ChargingSessionEntity)

    @Query("SELECT * FROM charging_sessions ORDER BY startTimestampEpochMs DESC")
    fun getAllSessions(): Flow<List<ChargingSessionEntity>>

    @Query("SELECT * FROM charging_sessions ORDER BY startTimestampEpochMs DESC LIMIT :limit")
    fun getRecentSessions(limit: Int): Flow<List<ChargingSessionEntity>>

    @Query("SELECT * FROM charging_sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: Long): ChargingSessionEntity?

    @Query("DELETE FROM charging_sessions")
    suspend fun clearAllSessions()
}
