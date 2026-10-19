package com.mahin.hytune.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mahin.hytune.data.local.db.entity.CustomScriptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomScriptDao {

    @Query("SELECT * FROM custom_scripts ORDER BY id DESC")
    fun getAllScripts(): Flow<List<CustomScriptEntity>>

    @Query("SELECT * FROM custom_scripts WHERE id = :id")
    suspend fun getScriptById(id: Long): CustomScriptEntity?

    @Query("SELECT * FROM custom_scripts WHERE runAtBoot = 1 ORDER BY id ASC")
    suspend fun getBootScripts(): List<CustomScriptEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScript(script: CustomScriptEntity): Long

    @Update
    suspend fun updateScript(script: CustomScriptEntity)

    @Query("UPDATE custom_scripts SET lastExecutedEpochMs = :timestamp, lastExitCode = :exitCode, lastOutput = :output, lastDurationMs = :durationMs WHERE id = :id")
    suspend fun updateExecutionResult(id: Long, exitCode: Int, output: String, durationMs: Long, timestamp: Long)

    @Query("UPDATE custom_scripts SET runAtBoot = :runAtBoot WHERE id = :id")
    suspend fun updateRunAtBoot(id: Long, runAtBoot: Boolean)

    @Query("DELETE FROM custom_scripts WHERE id = :id")
    suspend fun deleteScriptById(id: Long)

    @Query("DELETE FROM custom_scripts")
    suspend fun clearAllScripts()
}
