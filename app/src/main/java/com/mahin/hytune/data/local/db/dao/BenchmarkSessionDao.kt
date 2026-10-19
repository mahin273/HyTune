package com.mahin.hytune.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mahin.hytune.data.local.db.entity.BenchmarkSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BenchmarkSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: BenchmarkSessionEntity): Long

    @Query("SELECT * FROM benchmark_sessions ORDER BY startTimeEpochMs DESC")
    fun getAllSessions(): Flow<List<BenchmarkSessionEntity>>

    @Query("SELECT * FROM benchmark_sessions WHERE packageName = :packageName ORDER BY startTimeEpochMs DESC")
    fun getSessionsForPackage(packageName: String): Flow<List<BenchmarkSessionEntity>>

    @Query("SELECT * FROM benchmark_sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: Long): BenchmarkSessionEntity?

    @Query("DELETE FROM benchmark_sessions WHERE id = :id")
    suspend fun deleteSessionById(id: Long)

    @Query("DELETE FROM benchmark_sessions")
    suspend fun clearAllSessions()
}
