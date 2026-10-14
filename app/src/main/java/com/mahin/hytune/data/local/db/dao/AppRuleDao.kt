package com.mahin.hytune.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.mahin.hytune.data.local.db.entity.AppRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppRuleDao {

    @Upsert
    suspend fun upsertAppRule(rule: AppRuleEntity)

    @Query("SELECT * FROM app_rules ORDER BY packageName ASC")
    fun getAllAppRules(): Flow<List<AppRuleEntity>>

    @Query("SELECT * FROM app_rules WHERE packageName = :packageName LIMIT 1")
    fun getRuleForPackage(packageName: String): Flow<AppRuleEntity?>

    @Query("SELECT * FROM app_rules WHERE packageName = :packageName LIMIT 1")
    suspend fun getRuleForPackageDirect(packageName: String): AppRuleEntity?

    @Delete
    suspend fun deleteAppRule(rule: AppRuleEntity)

    @Query("DELETE FROM app_rules WHERE packageName = :packageName")
    suspend fun deleteByPackageName(packageName: String): Int
}
