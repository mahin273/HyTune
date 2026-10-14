package com.mahin.hytune.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.mahin.hytune.data.local.db.dao.AppRuleDao
import com.mahin.hytune.data.local.db.dao.ChargingSessionDao
import com.mahin.hytune.data.local.db.dao.ProfileDao
import com.mahin.hytune.data.local.db.entity.AppRuleEntity
import com.mahin.hytune.data.local.db.entity.ChargingSessionEntity
import com.mahin.hytune.data.local.db.entity.ProfileEntity

@Database(
    entities = [
        ProfileEntity::class,
        AppRuleEntity::class,
        ChargingSessionEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class HyTuneDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun appRuleDao(): AppRuleDao
    abstract fun chargingSessionDao(): ChargingSessionDao

    companion object {
        const val DATABASE_NAME = "hytune.db"
    }
}
