package com.mahin.hytune.data.local.db

import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = com.mahin.hytune.HyTuneApp::class)
class HyTuneDatabaseMigrationTest {

    @Test
    fun testAllMigrations_from1To6_executeSuccessfully() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null) // In-memory database
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    // Create V1 baseline tables
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `profiles` (
                            `id` TEXT NOT NULL PRIMARY KEY,
                            `name` TEXT NOT NULL,
                            `isBuiltIn` INTEGER NOT NULL,
                            `cpuGovernor` TEXT,
                            `cpuMinFreqKHz` INTEGER,
                            `cpuMaxFreqKHz` INTEGER,
                            `gpuGovernor` TEXT,
                            `gpuMinFreqKHz` INTEGER,
                            `gpuMaxFreqKHz` INTEGER,
                            `zramEnabled` INTEGER,
                            `ioScheduler` TEXT,
                            `createdAtEpochMs` INTEGER NOT NULL,
                            `updatedAtEpochMs` INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `app_rules` (
                            `packageName` TEXT NOT NULL PRIMARY KEY,
                            `profileId` TEXT NOT NULL,
                            `isGame` INTEGER NOT NULL,
                            `targetRefreshRate` INTEGER,
                            `dndEnabled` INTEGER NOT NULL,
                            `bypassChargingEnabled` INTEGER NOT NULL,
                            `freezeOnScreenOff` INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `charging_sessions` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `startTimeEpochMs` INTEGER NOT NULL,
                            `endTimeEpochMs` INTEGER NOT NULL,
                            `startLevelPercent` INTEGER NOT NULL,
                            `endLevelPercent` INTEGER NOT NULL,
                            `energyMah` REAL NOT NULL,
                            `isFastCharge` INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(
                    db: androidx.sqlite.db.SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase

        // Execute MIGRATION_1_2
        HyTuneDatabase.MIGRATION_1_2.migrate(db)
        var cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='freeze_groups'")
        assertTrue("freeze_groups table should exist after MIGRATION_1_2", cursor.moveToFirst())
        cursor.close()

        // Execute MIGRATION_2_3
        HyTuneDatabase.MIGRATION_2_3.migrate(db)
        cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='thermal_throttle_events'")
        assertTrue("thermal_throttle_events table should exist after MIGRATION_2_3", cursor.moveToFirst())
        cursor.close()

        // Execute MIGRATION_3_4
        HyTuneDatabase.MIGRATION_3_4.migrate(db)
        cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='benchmark_sessions'")
        assertTrue("benchmark_sessions table should exist after MIGRATION_3_4", cursor.moveToFirst())
        cursor.close()

        // Execute MIGRATION_4_5
        HyTuneDatabase.MIGRATION_4_5.migrate(db)
        cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='automation_rules'")
        assertTrue("automation_rules table should exist after MIGRATION_4_5", cursor.moveToFirst())
        cursor.close()

        // Execute MIGRATION_5_6
        HyTuneDatabase.MIGRATION_5_6.migrate(db)
        cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='custom_scripts'")
        assertTrue("custom_scripts table should exist after MIGRATION_5_6", cursor.moveToFirst())
        cursor.close()

        // Insert and verify data can be read/written across migrated tables
        db.execSQL(
            """
            INSERT INTO custom_scripts (name, scriptContent, runAtBoot, runAsRoot, lastExecutedEpochMs, lastDurationMs, createdAtEpochMs)
            VALUES ('Migrated Script', 'uptime', 1, 0, 0, 0, 1000)
            """.trimIndent()
        )
        cursor = db.query("SELECT name, scriptContent FROM custom_scripts WHERE name='Migrated Script'")
        assertTrue(cursor.moveToFirst())
        assertEquals("Migrated Script", cursor.getString(0))
        assertEquals("uptime", cursor.getString(1))
        cursor.close()

        db.close()
    }
}
