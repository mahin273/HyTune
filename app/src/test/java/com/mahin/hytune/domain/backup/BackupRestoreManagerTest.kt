package com.mahin.hytune.domain.backup

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mahin.hytune.data.local.datastore.AppSettingsRepository
import com.mahin.hytune.data.local.db.HyTuneDatabase
import com.mahin.hytune.data.local.db.entity.AppRuleEntity
import com.mahin.hytune.data.local.db.entity.AutomationRuleEntity
import com.mahin.hytune.data.local.db.entity.CustomScriptEntity
import com.mahin.hytune.data.local.db.entity.FreezeGroupEntity
import com.mahin.hytune.data.local.db.entity.ProfileEntity
import com.mahin.hytune.domain.backup.model.ConflictStrategy
import com.mahin.hytune.domain.backup.model.RestoreFailureReason
import com.mahin.hytune.domain.backup.model.RestoreResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = com.mahin.hytune.HyTuneApp::class)
class BackupRestoreManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var database: HyTuneDatabase
    private lateinit var appSettingsRepository: AppSettingsRepository
    private lateinit var backupRestoreManager: BackupRestoreManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, HyTuneDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val dataStoreFile = tempFolder.newFile("test_backup_settings.preferences_pb")
        val testDataStore = PreferenceDataStoreFactory.create(
            produceFile = { dataStoreFile }
        )
        appSettingsRepository = AppSettingsRepository(testDataStore)

        backupRestoreManager = BackupRestoreManager(
            database = database,
            profileDao = database.profileDao(),
            automationRuleDao = database.automationRuleDao(),
            customScriptDao = database.customScriptDao(),
            appRuleDao = database.appRuleDao(),
            freezeGroupDao = database.freezeGroupDao(),
            appSettingsRepository = appSettingsRepository,
            ioDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun createBackup_producesValidJson_withMatchingSha256Checksum() = runTest(testDispatcher) {
        database.profileDao().upsertProfile(
            ProfileEntity(
                id = "custom_battery",
                name = "Eco Battery",
                isBuiltIn = false,
                cpuGovernor = "powersave",
                cpuMinFreqKHz = 300000L,
                cpuMaxFreqKHz = 1200000L
            )
        )
        database.automationRuleDao().insertRule(
            AutomationRuleEntity(
                id = 1L,
                name = "Low Battery Power Save",
                isEnabled = true,
                priority = 80,
                triggerType = "BATTERY_PERCENT_BELOW",
                triggerValue = "20",
                conditionJson = "[]",
                actionType = "APPLY_PROFILE",
                actionValue = "powersave"
            )
        )
        database.customScriptDao().insertScript(
            CustomScriptEntity(
                id = 1L,
                name = "Clear PageCache",
                scriptContent = "echo 3 > /proc/sys/vm/drop_caches",
                runAtBoot = false,
                runAsRoot = true
            )
        )
        database.appRuleDao().upsertAppRule(
            AppRuleEntity(
                packageName = "com.test.game",
                profileId = "performance",
                isGame = true
            )
        )
        database.freezeGroupDao().saveGroupWithPackages(
            FreezeGroupEntity(id = 1L, name = "Social Media", description = "Freeze when screen is off"),
            listOf("com.facebook.katana", "com.instagram.android")
        )
        appSettingsRepository.setTemperatureUnitCelsius(false)
        appSettingsRepository.setChargeLimitPercent(75)

        val backupJson = backupRestoreManager.createBackup(
            appVersionName = "2.1.0",
            deviceModel = "Pixel 8 Pro"
        )

        assertTrue(backupJson.isNotBlank())
        val validation = backupRestoreManager.validateBackup(backupJson)
        assertTrue(validation.isSuccess)

        val bundle = validation.getOrThrow()
        assertEquals(1, bundle.header.schemaVersion)
        assertEquals("2.1.0", bundle.header.appVersionName)
        assertEquals("Pixel 8 Pro", bundle.header.deviceModel)
        assertTrue(bundle.header.checksumSha256.isNotBlank())
        assertEquals(1, bundle.payload.profiles.size)
        assertEquals("custom_battery", bundle.payload.profiles[0].id)
        assertEquals(1, bundle.payload.automationRules.size)
        assertEquals(1, bundle.payload.customScripts.size)
        assertEquals(1, bundle.payload.appRules.size)
        assertEquals(1, bundle.payload.freezeGroups.size)
        assertEquals(listOf("com.facebook.katana", "com.instagram.android"), bundle.payload.freezeGroups[0].packageNames)
        assertEquals(false, bundle.payload.settings.temperatureUnitCelsius)
        assertEquals(75, bundle.payload.settings.chargeLimitPercent)
    }

    @Test
    fun validateBackup_detectsCorruptedPayloadChecksum() = runTest(testDispatcher) {
        database.profileDao().upsertProfile(
            ProfileEntity(id = "p1", name = "Original Profile")
        )
        val validJson = backupRestoreManager.createBackup()

        // Tamper with content inside the JSON without updating the checksum
        val tamperedJson = validJson.replace("Original Profile", "Tampered Profile")

        val result = backupRestoreManager.validateBackup(tamperedJson)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
    }

    @Test
    fun validateBackup_detectsUnsupportedFutureVersion() {
        val futureJson = """
            {
              "header": {
                "schemaVersion": 99,
                "exportedAtEpochMs": 1729000000000,
                "appVersionName": "9.0.0",
                "deviceModel": "Pixel 15",
                "checksumSha256": "abcdef"
              },
              "payload": {
                "profiles": [],
                "automationRules": [],
                "customScripts": [],
                "appRules": [],
                "freezeGroups": [],
                "settings": {
                  "activeProfileId": "balance"
                }
              }
            }
        """.trimIndent()

        val result = backupRestoreManager.validateBackup(futureJson)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun validateBackup_handlesMalformedJson() {
        val result = backupRestoreManager.validateBackup("not a json payload")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun restoreBackup_conflictStrategyOverwrite_replacesExistingEntities() = runTest(testDispatcher) {
        database.profileDao().upsertProfile(
            ProfileEntity(id = "perf_tweak", name = "Initial Gaming", cpuGovernor = "schedutil")
        )

        val backupJson = backupRestoreManager.createBackup()
        val modifiedBackup = backupJson.replace("Initial Gaming", "Extreme Gaming")
        // Since we modified string, we re-create with Extreme Gaming to have valid checksum:
        database.profileDao().upsertProfile(
            ProfileEntity(id = "perf_tweak", name = "Extreme Gaming", cpuGovernor = "performance")
        )
        val validUpdatedBackup = backupRestoreManager.createBackup()

        // Reset local database back to initial
        database.profileDao().upsertProfile(
            ProfileEntity(id = "perf_tweak", name = "Initial Gaming", cpuGovernor = "schedutil")
        )

        val restoreResult = backupRestoreManager.restoreBackup(validUpdatedBackup, ConflictStrategy.OVERWRITE)
        assertTrue(restoreResult is RestoreResult.Success)

        val restoredProfile = database.profileDao().getProfileById("perf_tweak")
        assertNotNull(restoredProfile)
        assertEquals("Extreme Gaming", restoredProfile?.name)
        assertEquals("performance", restoredProfile?.cpuGovernor)
    }

    @Test
    fun restoreBackup_conflictStrategySkipExisting_preservesLocalEntities() = runTest(testDispatcher) {
        database.profileDao().upsertProfile(
            ProfileEntity(id = "perf_tweak", name = "Local Priority", cpuGovernor = "powersave")
        )
        val backupJson = backupRestoreManager.createBackup()

        // Change local entity
        database.profileDao().upsertProfile(
            ProfileEntity(id = "perf_tweak", name = "Local Keep", cpuGovernor = "schedutil")
        )

        // Restore backup with SKIP_EXISTING
        val restoreResult = backupRestoreManager.restoreBackup(backupJson, ConflictStrategy.SKIP_EXISTING)
        assertTrue(restoreResult is RestoreResult.Success)

        val currentProfile = database.profileDao().getProfileById("perf_tweak")
        assertEquals("Local Keep", currentProfile?.name)
        assertEquals("schedutil", currentProfile?.cpuGovernor)
    }

    @Test
    fun restoreBackup_conflictStrategyRename_createsNewProfileWithRestoredSuffix() = runTest(testDispatcher) {
        database.profileDao().upsertProfile(
            ProfileEntity(id = "custom_1", name = "My Profile", isBuiltIn = false, cpuGovernor = "powersave")
        )
        val backupJson = backupRestoreManager.createBackup()

        // Keep existing custom_1 in DB and restore with RENAME_ON_CONFLICT
        val restoreResult = backupRestoreManager.restoreBackup(backupJson, ConflictStrategy.RENAME_ON_CONFLICT)
        assertTrue(restoreResult is RestoreResult.Success)

        val allProfiles = database.profileDao().getAllProfiles().first()
        assertEquals(2, allProfiles.size)
        assertTrue(allProfiles.any { it.name == "My Profile" && it.id == "custom_1" })
        assertTrue(allProfiles.any { it.name == "My Profile (Restored)" && it.id != "custom_1" })
    }

    @Test
    fun restoreBackup_appliesPreferencesCleanly() = runTest(testDispatcher) {
        appSettingsRepository.setTemperatureUnitCelsius(false)
        appSettingsRepository.setChargeLimitPercent(65)
        appSettingsRepository.setApplyOnBoot(true)

        val backupJson = backupRestoreManager.createBackup()

        // Reset settings
        appSettingsRepository.setTemperatureUnitCelsius(true)
        appSettingsRepository.setChargeLimitPercent(80)
        appSettingsRepository.setApplyOnBoot(false)

        val restoreResult = backupRestoreManager.restoreBackup(backupJson, ConflictStrategy.OVERWRITE)
        assertTrue(restoreResult is RestoreResult.Success)

        val settings = appSettingsRepository.settings.first()
        assertEquals(false, settings.temperatureUnitCelsius)
        assertEquals(65, settings.chargeLimitPercent)
        assertEquals(true, settings.applyOnBoot)
    }

    @Test
    fun restoreBackup_failsWithInvalidChecksum() = runTest(testDispatcher) {
        val validJson = backupRestoreManager.createBackup()
        val corruptedJson = validJson.replace("\"applyOnBoot\": false", "\"applyOnBoot\": true")

        val result = backupRestoreManager.restoreBackup(corruptedJson, ConflictStrategy.OVERWRITE)
        assertTrue(result is RestoreResult.Failure)
        assertEquals(RestoreFailureReason.INVALID_CHECKSUM, (result as RestoreResult.Failure).reason)
    }
}
