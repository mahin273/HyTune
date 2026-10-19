package com.mahin.hytune.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mahin.hytune.data.local.db.dao.AppRuleDao
import com.mahin.hytune.data.local.db.dao.ChargingSessionDao
import com.mahin.hytune.data.local.db.dao.ProfileDao
import com.mahin.hytune.data.local.db.entity.AppRuleEntity
import com.mahin.hytune.data.local.db.entity.ChargingSessionEntity
import com.mahin.hytune.data.local.db.entity.ProfileEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class RoomDatabaseTest {

    private lateinit var db: HyTuneDatabase
    private lateinit var profileDao: ProfileDao
    private lateinit var appRuleDao: AppRuleDao
    private lateinit var chargingSessionDao: ChargingSessionDao
    private lateinit var thermalThrottleDao: com.mahin.hytune.data.local.db.dao.ThermalThrottleDao
    private lateinit var benchmarkSessionDao: com.mahin.hytune.data.local.db.dao.BenchmarkSessionDao
    private lateinit var automationRuleDao: com.mahin.hytune.data.local.db.dao.AutomationRuleDao
    private lateinit var customScriptDao: com.mahin.hytune.data.local.db.dao.CustomScriptDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, HyTuneDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        profileDao = db.profileDao()
        appRuleDao = db.appRuleDao()
        chargingSessionDao = db.chargingSessionDao()
        thermalThrottleDao = db.thermalThrottleDao()
        benchmarkSessionDao = db.benchmarkSessionDao()
        automationRuleDao = db.automationRuleDao()
        customScriptDao = db.customScriptDao()
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        db.close()
    }

    @Test
    fun profileDao_upsertAndGetById() = runTest {
        val profile = ProfileEntity(
            id = "balance",
            name = "Balance",
            isBuiltIn = true,
            cpuGovernor = "schedutil",
            cpuMinFreqKHz = 300000L,
            cpuMaxFreqKHz = 1804800L
        )

        profileDao.upsertProfile(profile)

        val retrieved = profileDao.getProfileById("balance")
        assertNotNull(retrieved)
        assertEquals("Balance", retrieved?.name)
        assertEquals("schedutil", retrieved?.cpuGovernor)
        assertTrue(retrieved?.isBuiltIn == true)
    }

    @Test
    fun profileDao_flowEmitsUpdatedList() = runTest {
        val p1 = ProfileEntity(id = "powersave", name = "Powersave", isBuiltIn = true)
        val p2 = ProfileEntity(id = "perf", name = "Performance", isBuiltIn = true)

        profileDao.upsertProfiles(listOf(p1, p2))

        val list = profileDao.getAllProfiles().first()
        assertEquals(2, list.size)

        val p3 = ProfileEntity(id = "custom_1", name = "Custom Gamer", isBuiltIn = false)
        profileDao.upsertProfile(p3)

        val updatedList = profileDao.getAllProfiles().first()
        assertEquals(3, updatedList.size)
    }

    @Test
    fun profileDao_deleteCustomProfilePreservesBuiltIn() = runTest {
        val builtIn = ProfileEntity(id = "fast", name = "Fast", isBuiltIn = true)
        val custom = ProfileEntity(id = "my_tune", name = "My Tune", isBuiltIn = false)

        profileDao.upsertProfiles(listOf(builtIn, custom))

        val deletedCount = profileDao.deleteCustomProfileById("fast")
        assertEquals(0, deletedCount)
        assertNotNull(profileDao.getProfileById("fast"))

        val customDeleted = profileDao.deleteCustomProfileById("my_tune")
        assertEquals(1, customDeleted)
        assertNull(profileDao.getProfileById("my_tune"))
    }

    @Test
    fun appRuleDao_upsertAndQueryByPackage() = runTest {
        val rule = AppRuleEntity(
            packageName = "com.supercell.clashofclans",
            profileId = "perf",
            isGame = true,
            targetRefreshRate = 90,
            dndEnabled = true,
            bypassChargingEnabled = true,
            freezeOnScreenOff = false
        )

        appRuleDao.upsertAppRule(rule)

        val retrieved = appRuleDao.getRuleForPackageDirect("com.supercell.clashofclans")
        assertNotNull(retrieved)
        assertEquals("perf", retrieved?.profileId)
        assertTrue(retrieved?.isGame == true)
        assertEquals(90, retrieved?.targetRefreshRate)

        val allRules = appRuleDao.getAllAppRules().first()
        assertEquals(1, allRules.size)
    }

    @Test
    fun chargingSessionDao_insertAndQueryRecentLimit() = runTest {
        val s1 = ChargingSessionEntity(
            startTimestampEpochMs = 1000L,
            endTimestampEpochMs = 2000L,
            startPercent = 20,
            endPercent = 80,
            avgPowerWatts = 18.5f,
            peakTemperatureC = 37.2f
        )
        val s2 = ChargingSessionEntity(
            startTimestampEpochMs = 3000L,
            endTimestampEpochMs = 4000L,
            startPercent = 80,
            endPercent = 100,
            avgPowerWatts = 10.2f,
            peakTemperatureC = 34.0f
        )

        chargingSessionDao.insertSession(s1)
        chargingSessionDao.insertSession(s2)

        val recent = chargingSessionDao.getRecentSessions(limit = 1).first()
        assertEquals(1, recent.size)
        assertEquals(3000L, recent.first().startTimestampEpochMs)
    }

    @Test
    fun thermalThrottleDao_insertAndQueryRecent() = runTest {
        val e1 = com.mahin.hytune.data.local.db.entity.ThermalThrottleEventEntity(
            timestampEpochMs = 1000L,
            zoneName = "cpu-1-0-usr",
            temperatureC = 82.5f,
            coolingDeviceState = "thermal-cpufreq-0:2",
            triggerReason = "CPU thermal throttle"
        )
        val e2 = com.mahin.hytune.data.local.db.entity.ThermalThrottleEventEntity(
            timestampEpochMs = 2000L,
            zoneName = "gpuss-0-usr",
            temperatureC = 78.0f,
            coolingDeviceState = "thermal-devfreq-0:1",
            triggerReason = "GPU thermal throttle"
        )

        thermalThrottleDao.insertEvent(e1)
        thermalThrottleDao.insertEvent(e2)

        val recent = thermalThrottleDao.getRecentEvents(limit = 1).first()
        assertEquals(1, recent.size)
        assertEquals(2000L, recent.first().timestampEpochMs)
        assertEquals("gpuss-0-usr", recent.first().zoneName)

        val all = thermalThrottleDao.getAllEvents().first()
        assertEquals(2, all.size)

        thermalThrottleDao.clearAllEvents()
        val cleared = thermalThrottleDao.getAllEvents().first()
        assertTrue(cleared.isEmpty())
    }

    @Test
    fun benchmarkSessionDao_insertQueryAndDelete() = runTest {
        val s1 = com.mahin.hytune.data.local.db.entity.BenchmarkSessionEntity(
            packageName = "com.miHoYo.GenshinImpact",
            appName = "Genshin Impact",
            startTimeEpochMs = 1000L,
            endTimeEpochMs = 61000L,
            durationSeconds = 60,
            avgFps = 58.4,
            onePercentLowFps = 42.1,
            pointOnePercentLowFps = 28.5,
            minFps = 22.0,
            maxFps = 61.2,
            totalFrames = 3500,
            jankCount = 42,
            jankPercentage = 1.2,
            stutterScore = 2.4,
            avgFrameTimeMs = 17.1,
            avgCpuLoadPercent = 45,
            avgGpuLoadPercent = 78,
            avgBatteryWatts = 5.2f,
            peakTemperatureC = 41.5f,
            histogramJson = "[]",
            telemetrySeriesJson = "[]"
        )
        val s2 = com.mahin.hytune.data.local.db.entity.BenchmarkSessionEntity(
            packageName = "com.activision.callofduty.shooter",
            appName = "Call of Duty Mobile",
            startTimeEpochMs = 70000L,
            endTimeEpochMs = 130000L,
            durationSeconds = 60,
            avgFps = 118.2,
            onePercentLowFps = 95.0,
            pointOnePercentLowFps = 72.0,
            minFps = 65.0,
            maxFps = 121.0,
            totalFrames = 7100,
            jankCount = 15,
            jankPercentage = 0.2,
            stutterScore = 0.5,
            avgFrameTimeMs = 8.4,
            avgCpuLoadPercent = 52,
            avgGpuLoadPercent = 88,
            avgBatteryWatts = 6.8f,
            peakTemperatureC = 43.0f,
            histogramJson = "[]",
            telemetrySeriesJson = "[]"
        )

        val id1 = benchmarkSessionDao.insertSession(s1)
        val id2 = benchmarkSessionDao.insertSession(s2)

        val allSessions = benchmarkSessionDao.getAllSessions().first()
        assertEquals(2, allSessions.size)
        // Ordered by startTimeEpochMs DESC -> s2 first
        assertEquals("com.activision.callofduty.shooter", allSessions[0].packageName)
        assertEquals("com.miHoYo.GenshinImpact", allSessions[1].packageName)

        val genshinSessions = benchmarkSessionDao.getSessionsForPackage("com.miHoYo.GenshinImpact").first()
        assertEquals(1, genshinSessions.size)
        assertEquals("Genshin Impact", genshinSessions.first().appName)

        val fetched = benchmarkSessionDao.getSessionById(id1)
        assertNotNull(fetched)
        assertEquals(58.4, fetched!!.avgFps, 0.01)

        benchmarkSessionDao.deleteSessionById(id1)
        val afterDelete = benchmarkSessionDao.getSessionById(id1)
        assertNull(afterDelete)

        benchmarkSessionDao.clearAllSessions()
        val emptyList = benchmarkSessionDao.getAllSessions().first()
        assertTrue(emptyList.isEmpty())
    }

    @Test
    fun automationRuleDao_insertUpdateQueryAndDelete() = runTest {
        val r1 = com.mahin.hytune.data.local.db.entity.AutomationRuleEntity(
            name = "Low Battery Saver",
            isEnabled = true,
            priority = 70,
            triggerType = "BATTERY_PERCENT_BELOW",
            triggerValue = "20",
            actionType = "APPLY_PROFILE",
            actionValue = "powersave",
            cooldownSeconds = 30
        )
        val r2 = com.mahin.hytune.data.local.db.entity.AutomationRuleEntity(
            name = "Thermal Emergency",
            isEnabled = true,
            priority = 90,
            triggerType = "TEMPERATURE_ABOVE",
            triggerValue = "45",
            actionType = "APPLY_PROFILE",
            actionValue = "powersave",
            cooldownSeconds = 60
        )

        val id1 = automationRuleDao.insertRule(r1)
        val id2 = automationRuleDao.insertRule(r2)

        val allRules = automationRuleDao.getAllRules().first()
        assertEquals(2, allRules.size)
        // Ordered by priority DESC -> r2 (90) first, r1 (70) second
        assertEquals("Thermal Emergency", allRules[0].name)
        assertEquals("Low Battery Saver", allRules[1].name)

        val enabledRules = automationRuleDao.getEnabledRules().first()
        assertEquals(2, enabledRules.size)

        automationRuleDao.updateEnabled(id1, false)
        val enabledAfterDisable = automationRuleDao.getEnabledRules().first()
        assertEquals(1, enabledAfterDisable.size)
        assertEquals("Thermal Emergency", enabledAfterDisable.first().name)

        val retrieved = automationRuleDao.getRuleById(id2)
        assertNotNull(retrieved)
        assertEquals(90, retrieved!!.priority)

        automationRuleDao.updateLastTriggered(id2, 123456789L)
        val afterTimestamp = automationRuleDao.getRuleById(id2)
        assertEquals(123456789L, afterTimestamp!!.lastTriggeredEpochMs)

        automationRuleDao.deleteRuleById(id2)
        assertNull(automationRuleDao.getRuleById(id2))

        automationRuleDao.clearAllRules()
        assertTrue(automationRuleDao.getAllRules().first().isEmpty())
    }

    @Test
    fun customScriptDao_insertUpdateQueryAndDelete() = runTest {
        val s1 = com.mahin.hytune.data.local.db.entity.CustomScriptEntity(
            name = "Compaction",
            scriptContent = "echo 1 > /sys/block/zram0/compact",
            runAtBoot = false,
            runAsRoot = true,
            lastExecutedEpochMs = 0,
            lastExitCode = null,
            lastOutput = null,
            lastDurationMs = 0,
            createdAtEpochMs = 1000L
        )
        val s2 = com.mahin.hytune.data.local.db.entity.CustomScriptEntity(
            name = "Drop Caches",
            scriptContent = "echo 3 > /proc/sys/vm/drop_caches",
            runAtBoot = true,
            runAsRoot = true,
            lastExecutedEpochMs = 0,
            lastExitCode = null,
            lastOutput = null,
            lastDurationMs = 0,
            createdAtEpochMs = 2000L
        )

        val id1 = customScriptDao.insertScript(s1)
        val id2 = customScriptDao.insertScript(s2)

        val allScripts = customScriptDao.getAllScripts().first()
        assertEquals(2, allScripts.size)

        val bootScripts = customScriptDao.getBootScripts()
        assertEquals(1, bootScripts.size)
        assertEquals("Drop Caches", bootScripts.first().name)

        val retrieved = customScriptDao.getScriptById(id1)
        assertNotNull(retrieved)
        assertEquals("Compaction", retrieved!!.name)

        customScriptDao.updateExecutionResult(
            id = id1,
            exitCode = 0,
            output = "Compaction done",
            durationMs = 45L,
            timestamp = 5000L
        )
        val afterExec = customScriptDao.getScriptById(id1)
        assertEquals(0, afterExec?.lastExitCode)
        assertEquals("Compaction done", afterExec?.lastOutput)
        assertEquals(45L, afterExec?.lastDurationMs)
        assertEquals(5000L, afterExec?.lastExecutedEpochMs)

        customScriptDao.updateRunAtBoot(id1, true)
        val bootAfterToggle = customScriptDao.getBootScripts()
        assertEquals(2, bootAfterToggle.size)

        customScriptDao.deleteScriptById(id2)
        assertNull(customScriptDao.getScriptById(id2))

        customScriptDao.clearAllScripts()
        assertTrue(customScriptDao.getAllScripts().first().isEmpty())
    }
}
