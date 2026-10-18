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
}
