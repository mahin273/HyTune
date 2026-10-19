package com.mahin.hytune.domain.overlay

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mahin.hytune.core.privilege.FakePrivilegeBackend
import com.mahin.hytune.data.local.datastore.AppSettingsRepository
import com.mahin.hytune.data.local.db.HyTuneDatabase
import com.mahin.hytune.core.privilege.CommandResult
import com.mahin.hytune.core.privilege.PrivilegeBackend
import com.mahin.hytune.core.privilege.PrivilegeManager
import com.mahin.hytune.core.privilege.PrivilegeTier
import com.mahin.hytune.core.privilege.RootShellRunner
import com.mahin.hytune.core.privilege.ShizukuShellRunner
import com.mahin.hytune.domain.battery.BatteryEngine
import com.mahin.hytune.domain.battery.BatteryRepository
import com.mahin.hytune.domain.battery.ChargingSessionTracker
import com.mahin.hytune.domain.cpu.CpuControlEngine
import com.mahin.hytune.domain.detection.ForegroundAppDetector
import com.mahin.hytune.domain.detection.IgnoredPackagesFilter
import com.mahin.hytune.domain.detection.strategy.CgroupForegroundStrategy
import com.mahin.hytune.domain.detection.strategy.DumpsysForegroundStrategy
import com.mahin.hytune.domain.detection.strategy.UsageStatsForegroundStrategy
import com.mahin.hytune.domain.fps.FpsSampler
import com.mahin.hytune.domain.fps.parser.GfxinfoFramestatsParser
import com.mahin.hytune.domain.fps.parser.SurfaceFlingerLatencyParser
import com.mahin.hytune.domain.gpu.GpuControlEngine
import com.mahin.hytune.domain.memory.MemoryControlEngine
import com.mahin.hytune.domain.probe.HardwareProbe
import com.mahin.hytune.domain.thermal.ThermalEngine
import com.mahin.hytune.domain.thermal.ThermalRepository
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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class FloatingOverlayManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var fakeBackend: FakePrivilegeBackend
    private lateinit var db: HyTuneDatabase
    private lateinit var appSettingsRepository: AppSettingsRepository
    private lateinit var overlayManager: FloatingOverlayManager

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

        val dataStoreFile = tempFolder.newFile("test_settings.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create { dataStoreFile }
        appSettingsRepository = AppSettingsRepository(dataStore)

        fakeBackend = FakePrivilegeBackend()
        val hardwareProbe = HardwareProbe(fakeBackend)
        val cpuEngine = CpuControlEngine(fakeBackend, hardwareProbe, testDispatcher)
        val gpuEngine = GpuControlEngine(fakeBackend, hardwareProbe, testDispatcher)
        val memEngine = MemoryControlEngine(fakeBackend, testDispatcher)
        val batteryEngine = BatteryEngine(context, fakeBackend, testDispatcher)
        val sessionDao = db.chargingSessionDao()
        val sessionTracker = ChargingSessionTracker(context, sessionDao, batteryEngine, testDispatcher)
        val batteryRepo = BatteryRepository(batteryEngine, sessionDao, sessionTracker, testDispatcher)
        val thermalDao = db.thermalThrottleDao()
        val thermalEngine = ThermalEngine(fakeBackend, testDispatcher)
        val thermalRepo = ThermalRepository(thermalEngine, thermalDao, testDispatcher)

        val rootRunner = object : RootShellRunner {
            override fun isRootAvailable(): Boolean = true
            override suspend fun runCommand(command: String): CommandResult = fakeBackend.exec(command)
        }
        val shizukuRunner = object : ShizukuShellRunner {
            override fun isShizukuAvailable(): Boolean = false
            override suspend fun runCommand(command: String): CommandResult = fakeBackend.exec(command)
        }
        val rootBackend = object : PrivilegeBackend by fakeBackend {
            override val tier = PrivilegeTier.ROOT
        }
        val shizukuBackend = object : PrivilegeBackend by fakeBackend {
            override val tier = PrivilegeTier.SHIZUKU
        }
        val unprivilegedBackend = object : PrivilegeBackend by fakeBackend {
            override val tier = PrivilegeTier.NONE
        }
        val privilegeManager = PrivilegeManager(
            rootShellRunner = rootRunner,
            shizukuShellRunner = shizukuRunner,
            rootBackend = rootBackend,
            shizukuBackend = shizukuBackend,
            unprivilegedBackend = unprivilegedBackend
        )
        privilegeManager.refresh()

        val ignoredFilter = IgnoredPackagesFilter(context)
        val cgroupStrategy = CgroupForegroundStrategy(fakeBackend)
        val dumpsysStrategy = DumpsysForegroundStrategy(fakeBackend)
        val usageStatsStrategy = UsageStatsForegroundStrategy(context)
        val foregroundDetector = ForegroundAppDetector(
            privilegeManager = privilegeManager,
            cgroupStrategy = cgroupStrategy,
            dumpsysStrategy = dumpsysStrategy,
            usageStatsStrategy = usageStatsStrategy,
            ignoredPackagesFilter = ignoredFilter,
            ioDispatcher = testDispatcher
        )
        val sfParser = SurfaceFlingerLatencyParser()
        val gfxParser = GfxinfoFramestatsParser()
        val fpsSampler = FpsSampler(
            privilegeManager = privilegeManager,
            foregroundAppDetector = foregroundDetector,
            surfaceFlingerParser = sfParser,
            gfxinfoParser = gfxParser,
            ioDispatcher = testDispatcher
        )

        overlayManager = FloatingOverlayManager(
            context = context,
            appSettingsRepository = appSettingsRepository,
            cpuControlEngine = cpuEngine,
            gpuControlEngine = gpuEngine,
            memoryControlEngine = memEngine,
            batteryRepository = batteryRepo,
            thermalRepository = thermalRepo,
            fpsSampler = fpsSampler,
            mainDispatcher = testDispatcher,
            ioDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun sampleTelemetry_aggregatesEngineMetricsWithoutThrowing() = runTest(testDispatcher) {
        overlayManager.sampleTelemetry()
        val telemetry = overlayManager.telemetry.value
        assertNotNull(telemetry)
        assertTrue(telemetry.cpuLoadPercent >= 0)
    }

    @Test
    fun toggleCompactMode_flipsCompactState() = runTest(testDispatcher) {
        testScheduler.advanceUntilIdle()
        val initialCompact = overlayManager.config.first().isCompact
        overlayManager.toggleCompactMode()
        testScheduler.advanceUntilIdle()
        val updatedCompact = appSettingsRepository.settings.first().overlayCompactMode
        assertEquals(!initialCompact, updatedCompact)
    }

    @Test
    fun configFlow_syncsWithAppSettingsRepository() = runTest(testDispatcher) {
        appSettingsRepository.setOverlayOpacity(0.6f)
        val currentOpacity = appSettingsRepository.settings.first().overlayOpacity
        assertEquals(0.6f, currentOpacity, 0.01f)
    }
}
