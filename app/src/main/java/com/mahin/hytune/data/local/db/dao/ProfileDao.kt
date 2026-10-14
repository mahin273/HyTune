package com.mahin.hytune.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.mahin.hytune.data.local.db.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    @Upsert
    suspend fun upsertProfile(profile: ProfileEntity)

    @Upsert
    suspend fun upsertProfiles(profiles: List<ProfileEntity>)

    @Query("SELECT * FROM profiles ORDER BY isBuiltIn DESC, name ASC")
    fun getAllProfiles(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE id = :id LIMIT 1")
    suspend fun getProfileById(id: String): ProfileEntity?

    @Query("SELECT * FROM profiles WHERE isBuiltIn = 1")
    suspend fun getBuiltInProfiles(): List<ProfileEntity>

    @Delete
    suspend fun deleteProfile(profile: ProfileEntity)

    @Query("DELETE FROM profiles WHERE id = :id AND isBuiltIn = 0")
    suspend fun deleteCustomProfileById(id: String): Int
}
