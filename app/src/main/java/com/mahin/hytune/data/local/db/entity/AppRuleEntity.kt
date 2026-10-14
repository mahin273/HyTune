package com.mahin.hytune.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_rules")
data class AppRuleEntity(
    @PrimaryKey
    val packageName: String,
    val profileId: String,
    val isGame: Boolean = false,
    val targetRefreshRate: Int? = null,
    val dndEnabled: Boolean = false,
    val bypassChargingEnabled: Boolean = false,
    val freezeOnScreenOff: Boolean = false
)
