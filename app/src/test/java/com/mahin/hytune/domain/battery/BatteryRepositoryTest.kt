package com.mahin.hytune.domain.battery

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mahin.hytune.core.privilege.Capability
import com.mahin.hytune.core.privilege.FakePrivilegeBackend
import com.mahin.hytune.core.privilege.PrivilegeTier
import com.mahin.hytune.data.local.db.HyTuneDatabase
import com.mahin.hytune.data.local.db.dao.ChargingSessionDao
import com.mahin.hytune.data.local.db.entity.ChargingSessionEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
class BatteryRepositoryTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var db: HyTuneDatabase
    private lateinit var dao: ChargingSessionDao
    private lateinit var fakeBackend: FakePrivilegeBackend
    private lateinit var engine: BatteryEngine
    private lateinit var tracker: ChargingSessionTracker
    private lateinit var repository: BatteryRepository

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
        tracker = ChargingSessionTracker(context, dao, engine, testDispatcher)
        repository = BatteryRepository(engine, dao, tracker, testDispatcher)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun getBatteryState_returnsSampledState() = runTest(testDispatcher) {
        val basePath = "/sys/class/power_supply/battery"
        fakeBackend.setFile("$basePath/capacity", "90")
        fakeBackend.setFile("$basePath/status", "Charging")
        fakeBackend.setFile("$basePath/voltage_now", "4200000")
        fakeBackend.setFile("$basePath/current_now", "1500000")

        val state = repository.getBatteryState()

        assertEquals(90, state.levelPercent)
        assertEquals(4200, state.voltageMv)
        assertEquals(1500, state.currentMa)
    }

    @Test
    fun getAllChargingSessions_and_clearHistory() = runTest(testDispatcher) {
        dao.insertSession(
            ChargingSessionEntity(
                startTimestampEpochMs = 1000L,
                endTimestampEpochMs = 2000L,
                startPercent = 20,
                endPercent = 80,
                avgPowerWatts = 15f,
                peakTemperatureC = 38f
            )
        )

        val sessions = repository.getAllChargingSessions().first()
        assertEquals(1, sessions.size)
        assertEquals(20, sessions[0].startPercent)
        assertEquals(80, sessions[0].endPercent)
        assertEquals(60, sessions[0].percentGained)

        repository.clearSessionHistory()
        val afterClear = repository.getAllChargingSessions().first()
        assertTrue(afterClear.isEmpty())
    }
}
