package com.mahin.hytune.domain.battery

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mahin.hytune.core.privilege.Capability
import com.mahin.hytune.core.privilege.FakePrivilegeBackend
import com.mahin.hytune.core.privilege.PrivilegeTier
import com.mahin.hytune.data.local.db.HyTuneDatabase
import com.mahin.hytune.data.local.db.dao.ChargingSessionDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class ChargingSessionTrackerTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var context: Context
    private lateinit var db: HyTuneDatabase
    private lateinit var dao: ChargingSessionDao
    private lateinit var fakeBackend: FakePrivilegeBackend
    private lateinit var engine: BatteryEngine
    private lateinit var tracker: ChargingSessionTracker

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val directExecutor = Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(context, HyTuneDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
        dao = db.chargingSessionDao()

        fakeBackend = FakePrivilegeBackend(
            initialTier = PrivilegeTier.ROOT,
            initialCapabilities = Capability.entries.toSet()
        )
        engine = BatteryEngine(context, fakeBackend, testDispatcher)
        tracker = ChargingSessionTracker(
            context = context,
            chargingSessionDao = dao,
            batteryEngine = engine,
            ioDispatcher = testDispatcher
        ).apply {
            testScopeOverride = testScope
        }
    }

    @After
    fun tearDown() {
        tracker.stopMonitoring()
        tracker.testScopeOverride = null
        db.close()
    }

    @Test
    fun handlePowerConnected_andDisconnected_recordsCompleteSession() = runTest(testDispatcher) {
        val basePath = "/sys/class/power_supply/battery"
        fakeBackend.setFile("$basePath/capacity", "25")
        fakeBackend.setFile("$basePath/status", "Charging")
        fakeBackend.setFile("$basePath/voltage_now", "4000000") // 4.0V
        fakeBackend.setFile("$basePath/current_now", "2000000") // 2.0A -> 8W
        fakeBackend.setFile("$basePath/temp", "320") // 32.0C

        // Power connects at 25%
        tracker.handlePowerConnected()
        assertTrue(tracker.isCharging.value)

        val activeId = tracker.getActiveSessionId()
        assertNotNull(activeId)

        // Intermediate charging samples
        tracker.onSample(10.0f, 35.5f)
        tracker.onSample(12.0f, 37.0f)

        // Fast-forward battery to 75% when disconnected
        fakeBackend.setFile("$basePath/capacity", "75")
        fakeBackend.setFile("$basePath/status", "Discharging")
        fakeBackend.setFile("$basePath/current_now", "500000")
        fakeBackend.setFile("$basePath/temp", "36.0")

        tracker.handlePowerDisconnected()

        val sessions = dao.getAllSessions().first()
        assertEquals(1, sessions.size)

        val recorded = sessions.first()
        assertEquals(25, recorded.startPercent)
        assertEquals(75, recorded.endPercent)
        assertNotNull(recorded.endTimestampEpochMs)
        assertEquals(37.0f, recorded.peakTemperatureC, 0.1f)
        assertTrue(recorded.avgPowerWatts > 0f)
    }
}
