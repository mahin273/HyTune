package com.mahin.hytune.ui.battery

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mahin.hytune.core.privilege.Capability
import com.mahin.hytune.core.privilege.FakePrivilegeBackend
import com.mahin.hytune.core.privilege.PrivilegeTier
import com.mahin.hytune.data.local.db.HyTuneDatabase
import com.mahin.hytune.data.local.db.dao.ChargingSessionDao
import com.mahin.hytune.data.local.db.entity.ChargingSessionEntity
import com.mahin.hytune.domain.battery.BatteryEngine
import com.mahin.hytune.domain.battery.BatteryRepository
import com.mahin.hytune.domain.battery.ChargingSessionTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
class BatteryViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var db: HyTuneDatabase
    private lateinit var dao: ChargingSessionDao
    private lateinit var fakeBackend: FakePrivilegeBackend
    private lateinit var engine: BatteryEngine
    private lateinit var tracker: ChargingSessionTracker
    private lateinit var repository: BatteryRepository
    private lateinit var viewModel: BatteryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
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
        val basePath = "/sys/class/power_supply/battery"
        fakeBackend.setFile("$basePath/capacity", "80")
        fakeBackend.setFile("$basePath/status", "Charging")
        fakeBackend.setFile("$basePath/voltage_now", "4100000")
        fakeBackend.setFile("$basePath/current_now", "2000000")

        engine = BatteryEngine(context, fakeBackend, testDispatcher)
        tracker = ChargingSessionTracker(context, dao, engine, testDispatcher)
        repository = BatteryRepository(engine, dao, tracker, testDispatcher)
        val chargeEngine = com.mahin.hytune.domain.battery.ChargeControlEngine(fakeBackend, testDispatcher)
        val thermalDao = db.thermalThrottleDao()
        val thermalEngine = com.mahin.hytune.domain.thermal.ThermalEngine(fakeBackend, testDispatcher)
        val thermalRepo = com.mahin.hytune.domain.thermal.ThermalRepository(thermalEngine, thermalDao, testDispatcher)
        viewModel = BatteryViewModel(
            repository = repository,
            chargeControlEngine = chargeEngine,
            thermalRepository = thermalRepo,
            appSettingsRepository = null
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun uiState_collectsBatteryStateAndSessions() = runTest(testDispatcher) {
        dao.insertSession(
            ChargingSessionEntity(
                startTimestampEpochMs = 1000L,
                endTimestampEpochMs = 2000L,
                startPercent = 30,
                endPercent = 80,
                avgPowerWatts = 18f,
                peakTemperatureC = 39f
            )
        )

        val state = viewModel.uiState.first { it.sessions.isNotEmpty() }
        assertEquals(80, state.batteryState.levelPercent)
        assertEquals(1, state.sessions.size)
        assertEquals(30, state.sessions[0].startPercent)
        assertEquals(80, state.sessions[0].endPercent)
    }

    @Test
    fun clearSessionHistory_clearsSessionsAndSetsMessage() = runTest(testDispatcher) {
        dao.insertSession(
            ChargingSessionEntity(
                startTimestampEpochMs = 1000L,
                endTimestampEpochMs = 2000L,
                startPercent = 30,
                endPercent = 80,
                avgPowerWatts = 18f,
                peakTemperatureC = 39f
            )
        )

        viewModel.clearSessionHistory()
        val state = viewModel.uiState.first { it.userMessage != null }
        assertNotNull(state.userMessage)
        assertTrue(state.userMessage!!.contains("cleared"))
        assertTrue(state.sessions.isEmpty())
    }

    @Test
    fun setBypassCharging_whenRoot_updatesMessage() = runTest(testDispatcher) {
        val node = "/sys/class/power_supply/battery/charging_enabled"
        fakeBackend.setFile(node, "1\n")

        viewModel.setBypassCharging(true)
        val state = viewModel.uiState.first { it.userMessage != null }
        assertTrue(state.userMessage!!.contains("enabled"))
        assertEquals("0", fakeBackend.getFile(node))
    }

    @Test
    fun setChargeCurrentLimit_updatesLimitNodeAndMessage() = runTest(testDispatcher) {
        val limitNode = "/sys/class/power_supply/battery/constant_charge_current_max"
        fakeBackend.setFile(limitNode, "2000000\n")

        viewModel.setChargeCurrentLimit(1500)
        val state = viewModel.uiState.first { it.userMessage != null }
        assertTrue(state.userMessage!!.contains("1500 mA"))
        assertEquals("1500000", fakeBackend.getFile(limitNode))
    }

    @Test
    fun toggleOverlay_whenNoPermission_setsUserMessage() = runTest(testDispatcher) {
        viewModel.toggleOverlay(true)
        val state = viewModel.uiState.first { it.userMessage != null }
        assertNotNull(state.userMessage)
    }
}
