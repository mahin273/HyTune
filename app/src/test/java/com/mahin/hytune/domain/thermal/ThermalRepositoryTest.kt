package com.mahin.hytune.domain.thermal

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mahin.hytune.core.privilege.Capability
import com.mahin.hytune.core.privilege.FakePrivilegeBackend
import com.mahin.hytune.core.privilege.PrivilegeTier
import com.mahin.hytune.data.local.db.HyTuneDatabase
import com.mahin.hytune.data.local.db.dao.ThermalThrottleDao
import com.mahin.hytune.domain.thermal.model.ThermalThrottleEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
@Config(manifest = Config.NONE)
class ThermalRepositoryTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var db: HyTuneDatabase
    private lateinit var dao: ThermalThrottleDao
    private lateinit var fakeBackend: FakePrivilegeBackend
    private lateinit var engine: ThermalEngine
    private lateinit var repository: ThermalRepository

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
        dao = db.thermalThrottleDao()

        fakeBackend = FakePrivilegeBackend(
            initialTier = PrivilegeTier.ROOT,
            initialCapabilities = Capability.entries.toSet()
        )
        fakeBackend.setFile("/sys/class/thermal/thermal_zone0/type", "cpu-0\n")
        fakeBackend.setFile("/sys/class/thermal/thermal_zone0/temp", "42000\n")

        engine = ThermalEngine(fakeBackend, testDispatcher)
        repository = ThermalRepository(engine, dao, testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun sampleSnapshot_returnsParsedSnapshot() = runTest(testDispatcher) {
        val snapshot = repository.sampleSnapshot()
        assertEquals(1, snapshot.zones.size)
        assertEquals(42.0f, snapshot.zones.first().tempC, 0.01f)
    }

    @Test
    fun observeThermalSnapshot_emitsPeriodicSnapshots() = runTest(testDispatcher) {
        val snapshot = repository.observeThermalSnapshot(100L).take(1).first()
        assertEquals(1, snapshot.zones.size)
        assertEquals("cpu-0", snapshot.zones.first().name)
    }

    @Test
    fun logThrottleEvent_and_observeRecentThrottleEvents() = runTest(testDispatcher) {
        val event = ThermalThrottleEvent(
            timestampEpochMs = 5000L,
            zoneName = "cpu-prime",
            temperatureC = 85.0f,
            coolingDeviceState = "thermal-cpufreq-0:3",
            triggerReason = "Heavy benchmark throttle"
        )

        repository.logThrottleEvent(event)

        val list = repository.observeRecentThrottleEvents(10).first()
        assertEquals(1, list.size)
        assertEquals("cpu-prime", list.first().zoneName)
        assertEquals(85.0f, list.first().temperatureC, 0.01f)

        repository.clearThrottleHistory()
        val cleared = repository.observeRecentThrottleEvents(10).first()
        assertTrue(cleared.isEmpty())
    }
}
