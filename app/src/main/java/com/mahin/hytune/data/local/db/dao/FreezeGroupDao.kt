package com.mahin.hytune.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.mahin.hytune.data.local.db.entity.FreezeGroupEntity
import com.mahin.hytune.data.local.db.entity.FreezeGroupPackageCrossRef
import com.mahin.hytune.data.local.db.entity.FreezeGroupWithPackages
import kotlinx.coroutines.flow.Flow

@Dao
interface FreezeGroupDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: FreezeGroupEntity): Long

    @Update
    suspend fun updateGroup(group: FreezeGroupEntity)

    @Query("DELETE FROM freeze_groups WHERE id = :groupId")
    suspend fun deleteGroup(groupId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPackageCrossRef(crossRef: FreezeGroupPackageCrossRef)

    @Query("DELETE FROM freeze_group_packages WHERE group_id = :groupId AND package_name = :packageName")
    suspend fun deletePackageFromGroup(groupId: Long, packageName: String)

    @Query("DELETE FROM freeze_group_packages WHERE group_id = :groupId")
    suspend fun clearPackagesForGroup(groupId: Long)

    @Transaction
    @Query("SELECT * FROM freeze_groups ORDER BY name ASC")
    fun getAllGroupsWithPackages(): Flow<List<FreezeGroupWithPackages>>

    @Transaction
    @Query("SELECT * FROM freeze_groups WHERE id = :groupId")
    fun getGroupWithPackagesById(groupId: Long): Flow<FreezeGroupWithPackages?>

    @Transaction
    @Query("SELECT * FROM freeze_groups WHERE auto_freeze_on_screen_off = 1")
    suspend fun getAutoFreezeGroups(): List<FreezeGroupWithPackages>

    @Transaction
    @Query("SELECT * FROM freeze_groups WHERE id = :groupId")
    suspend fun getGroupWithPackagesByIdSync(groupId: Long): FreezeGroupWithPackages?

    @Transaction
    suspend fun setGroupPackages(groupId: Long, packageNames: List<String>) {
        clearPackagesForGroup(groupId)
        packageNames.forEach { pkg ->
            insertPackageCrossRef(FreezeGroupPackageCrossRef(groupId, pkg))
        }
    }

    @Transaction
    suspend fun saveGroupWithPackages(group: FreezeGroupEntity, packageNames: List<String>): Long {
        val groupId = if (group.id == 0L) {
            insertGroup(group)
        } else {
            updateGroup(group)
            group.id
        }
        setGroupPackages(groupId, packageNames)
        return groupId
    }
}
