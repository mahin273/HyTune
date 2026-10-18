package com.mahin.hytune.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mahin.hytune.data.local.db.dao.AppRuleDao
import com.mahin.hytune.data.local.db.dao.ChargingSessionDao
import com.mahin.hytune.data.local.db.dao.FreezeGroupDao
import com.mahin.hytune.data.local.db.dao.ProfileDao
import com.mahin.hytune.data.local.db.entity.AppRuleEntity
import com.mahin.hytune.data.local.db.entity.ChargingSessionEntity
import com.mahin.hytune.data.local.db.entity.FreezeGroupEntity
import com.mahin.hytune.data.local.db.entity.FreezeGroupPackageCrossRef
import com.mahin.hytune.data.local.db.entity.ProfileEntity

@Database(
    entities = [
        ProfileEntity::class,
        AppRuleEntity::class,
        ChargingSessionEntity::class,
        FreezeGroupEntity::class,
        FreezeGroupPackageCrossRef::class
    ],
    version = 2,
    exportSchema = false
)
abstract class HyTuneDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun appRuleDao(): AppRuleDao
    abstract fun chargingSessionDao(): ChargingSessionDao
    abstract fun freezeGroupDao(): FreezeGroupDao

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
    }
}
