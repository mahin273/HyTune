package com.mahin.hytune.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mahin.hytune.data.local.db.dao.AppRuleDao
import com.mahin.hytune.data.local.db.dao.BenchmarkSessionDao
import com.mahin.hytune.data.local.db.dao.ChargingSessionDao
import com.mahin.hytune.data.local.db.dao.FreezeGroupDao
import com.mahin.hytune.data.local.db.dao.ProfileDao
import com.mahin.hytune.data.local.db.dao.ThermalThrottleDao
import com.mahin.hytune.data.local.db.entity.AppRuleEntity
import com.mahin.hytune.data.local.db.entity.BenchmarkSessionEntity
import com.mahin.hytune.data.local.db.entity.ChargingSessionEntity
import com.mahin.hytune.data.local.db.entity.FreezeGroupEntity
import com.mahin.hytune.data.local.db.entity.FreezeGroupPackageCrossRef
import com.mahin.hytune.data.local.db.entity.ProfileEntity
import com.mahin.hytune.data.local.db.entity.ThermalThrottleEventEntity

@Database(
    entities = [
        ProfileEntity::class,
        AppRuleEntity::class,
        ChargingSessionEntity::class,
        FreezeGroupEntity::class,
        FreezeGroupPackageCrossRef::class,
        ThermalThrottleEventEntity::class,
        BenchmarkSessionEntity::class,
        com.mahin.hytune.data.local.db.entity.AutomationRuleEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class HyTuneDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun appRuleDao(): AppRuleDao
    abstract fun chargingSessionDao(): ChargingSessionDao
    abstract fun freezeGroupDao(): FreezeGroupDao
    abstract fun thermalThrottleDao(): ThermalThrottleDao
    abstract fun benchmarkSessionDao(): BenchmarkSessionDao
    abstract fun automationRuleDao(): com.mahin.hytune.data.local.db.dao.AutomationRuleDao

    companion object {
        const val DATABASE_NAME = "hytune.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `freeze_groups` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `auto_freeze_on_screen_off` INTEGER NOT NULL,
                        `auto_freeze_delay_seconds` INTEGER NOT NULL,
                        `created_at_epoch_ms` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_freeze_groups_name` ON `freeze_groups` (`name`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `freeze_group_packages` (
                        `group_id` INTEGER NOT NULL,
                        `package_name` TEXT NOT NULL,
                        PRIMARY KEY(`group_id`, `package_name`),
                        FOREIGN KEY(`group_id`) REFERENCES `freeze_groups`(`id`) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_freeze_group_packages_group_id` ON `freeze_group_packages` (`group_id`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `thermal_throttle_events` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `timestampEpochMs` INTEGER NOT NULL,
                        `zoneName` TEXT NOT NULL,
                        `temperatureC` REAL NOT NULL,
                        `coolingDeviceState` TEXT NOT NULL,
                        `triggerReason` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_thermal_throttle_events_timestampEpochMs` ON `thermal_throttle_events` (`timestampEpochMs`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `benchmark_sessions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `packageName` TEXT NOT NULL,
                        `appName` TEXT NOT NULL,
                        `startTimeEpochMs` INTEGER NOT NULL,
                        `endTimeEpochMs` INTEGER NOT NULL,
                        `durationSeconds` INTEGER NOT NULL,
                        `avgFps` REAL NOT NULL,
                        `onePercentLowFps` REAL NOT NULL,
                        `pointOnePercentLowFps` REAL NOT NULL,
                        `minFps` REAL NOT NULL,
                        `maxFps` REAL NOT NULL,
                        `totalFrames` INTEGER NOT NULL,
                        `jankCount` INTEGER NOT NULL,
                        `jankPercentage` REAL NOT NULL,
                        `stutterScore` REAL NOT NULL,
                        `avgFrameTimeMs` REAL NOT NULL,
                        `avgCpuLoadPercent` INTEGER NOT NULL,
                        `avgGpuLoadPercent` INTEGER NOT NULL,
                        `avgBatteryWatts` REAL NOT NULL,
                        `peakTemperatureC` REAL NOT NULL,
                        `histogramJson` TEXT NOT NULL,
                        `telemetrySeriesJson` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_benchmark_sessions_startTimeEpochMs` ON `benchmark_sessions` (`startTimeEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_benchmark_sessions_packageName` ON `benchmark_sessions` (`packageName`)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `automation_rules` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `isEnabled` INTEGER NOT NULL,
                        `priority` INTEGER NOT NULL,
                        `triggerType` TEXT NOT NULL,
                        `triggerValue` TEXT NOT NULL,
                        `conditionJson` TEXT NOT NULL,
                        `actionType` TEXT NOT NULL,
                        `actionValue` TEXT NOT NULL,
                        `cooldownSeconds` INTEGER NOT NULL,
                        `lastTriggeredEpochMs` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_automation_rules_isEnabled` ON `automation_rules` (`isEnabled`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_automation_rules_priority` ON `automation_rules` (`priority`)")
            }
        }
    }
}
