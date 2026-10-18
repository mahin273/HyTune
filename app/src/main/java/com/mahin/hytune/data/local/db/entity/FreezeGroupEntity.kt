package com.mahin.hytune.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "freeze_groups",
    indices = [Index(value = ["name"], unique = true)]
)
data class FreezeGroupEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String = "",
    @ColumnInfo(name = "auto_freeze_on_screen_off")
    val autoFreezeOnScreenOff: Boolean = false,
    @ColumnInfo(name = "auto_freeze_delay_seconds")
    val autoFreezeDelaySeconds: Int = 30,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long = System.currentTimeMillis()
)
