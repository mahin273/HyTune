package com.mahin.hytune.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "freeze_group_packages",
    primaryKeys = ["group_id", "package_name"],
    foreignKeys = [
        ForeignKey(
            entity = FreezeGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["group_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["group_id"])]
)
data class FreezeGroupPackageCrossRef(
    @ColumnInfo(name = "group_id")
    val groupId: Long,
    @ColumnInfo(name = "package_name")
    val packageName: String
)
