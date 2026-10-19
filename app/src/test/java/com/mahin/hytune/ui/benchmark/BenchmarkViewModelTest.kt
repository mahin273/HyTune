package com.mahin.hytune.ui.benchmark

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mahin.hytune.data.local.db.HyTuneDatabase
import com.mahin.hytune.domain.benchmark.BenchmarkRecorderEngine
import com.mahin.hytune.domain.benchmark.BenchmarkRepository
import com.mahin.hytune.domain.benchmark.analytics.BenchmarkAnalyticsCalculator
import com.mahin.hytune.domain.benchmark.analytics.CsvExportFormatter
import com.mahin.hytune.domain.benchmark.model.BenchmarkSessionReport
import com.mahin.hytune.domain.detection.ForegroundAppDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.Executor

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class BenchmarkViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var db: HyTuneDatabase
    private lateinit var foregroundDetector: ForegroundAppDetector
    private lateinit var repository: BenchmarkRepository
    private lateinit var viewModel: BenchmarkViewModel

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

        val fakeBackend = com.mahin.hytune.core.privilege.FakePrivilegeBackend()
        val rootRunner = object : com.mahin.hytune.core.privilege.RootShellRunner {
            override fun isRootAvailable(): Boolean = true
            override suspend fun runCommand(command: String) = fakeBackend.exec(command)
        }
        val shizukuRunner = object : com.mahin.hytune.core.privilege.ShizukuShellRunner {
            override fun isShizukuAvailable(): Boolean = false
            override suspend fun runCommand(command: String) = fakeBackend.exec(command)
        }
        val privilegeManager = com.mahin.hytune.core.privilege.PrivilegeManager(
            rootShellRunner = rootRunner,
            shizukuShellRunner = shizukuRunner,
            rootBackend = object : com.mahin.hytune.core.privilege.PrivilegeBackend by fakeBackend {
                override val tier = com.mahin.hytune.core.privilege.PrivilegeTier.ROOT
            },
            shizukuBackend = object : com.mahin.hytune.core.privilege.PrivilegeBackend by fakeBackend {
                override val tier = com.mahin.hytune.core.privilege.PrivilegeTier.SHIZUKU
            },
            unprivilegedBackend = object : com.mahin.hytune.core.privilege.PrivilegeBackend by fakeBackend {
                override val tier = com.mahin.hytune.core.privilege.PrivilegeTier.NONE
            }
        )

        foregroundDetector = ForegroundAppDetector(
            privilegeManager = privilegeManager,
            cgroupStrategy = com.mahin.hytune.domain.detection.strategy.CgroupForegroundStrategy(fakeBackend),
            dumpsysStrategy = com.mahin.hytune.domain.detection.strategy.DumpsysForegroundStrategy(fakeBackend),
            usageStatsStrategy = com.mahin.hytune.domain.detection.strategy.UsageStatsForegroundStrategy(context),
            ignoredPackagesFilter = com.mahin.hytune.domain.detection.IgnoredPackagesFilter(context),
            ioDispatcher = testDispatcher
        )

        val fpsSampler = com.mahin.hytune.domain.fps.FpsSampler(
            privilegeManager = privilegeManager,
            foregroundAppDetector = foregroundDetector,
            surfaceFlingerParser = com.mahin.hytune.domain.fps.parser.SurfaceFlingerLatencyParser(),
            gfxinfoParser = com.mahin.hytune.domain.fps.parser.GfxinfoFramestatsParser(),
            ioDispatcher = testDispatcher
        ).apply {
            testScopeOverride = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
        }
        val batteryEngine = com.mahin.hytune.domain.battery.BatteryEngine(
            context = context,
            privilegeBackend = fakeBackend,
            ioDispatcher = testDispatcher
        )
        val chargingTracker = com.mahin.hytune.domain.battery.ChargingSessionTracker(
            context = context,
            chargingSessionDao = db.chargingSessionDao(),
            batteryEngine = batteryEngine,
            ioDispatcher = testDispatcher
        )
        val batteryRepo = com.mahin.hytune.domain.battery.BatteryRepository(
            batteryEngine = batteryEngine,
            chargingSessionDao = db.chargingSessionDao(),
            sessionTracker = chargingTracker,
            ioDispatcher = testDispatcher
        )
        val thermalRepo = com.mahin.hytune.domain.thermal.ThermalRepository(
            thermalEngine = com.mahin.hytune.domain.thermal.ThermalEngine(fakeBackend),
            thermalThrottleDao = db.thermalThrottleDao(),
            ioDispatcher = testDispatcher
        )
        val cpuEngine = com.mahin.hytune.domain.cpu.CpuControlEngine(
            privilegeBackend = fakeBackend,
            hardwareProbe = com.mahin.hytune.domain.probe.HardwareProbe(fakeBackend),
            ioDispatcher = testDispatcher
        )
        val gpuEngine = com.mahin.hytune.domain.gpu.GpuControlEngine(
            privilegeBackend = fakeBackend,
            hardwareProbe = com.mahin.hytune.domain.probe.HardwareProbe(fakeBackend),
            ioDispatcher = testDispatcher
        )
        val analyticsCalculator = BenchmarkAnalyticsCalculator()

        val recorderEngine = BenchmarkRecorderEngine(
            context = context,
            fpsSampler = fpsSampler,
            batteryRepository = batteryRepo,
            thermalRepository = thermalRepo,
            cpuControlEngine = cpuEngine,
            gpuControlEngine = gpuEngine,
            analyticsCalculator = analyticsCalculator,
            ioDispatcher = testDispatcher
        ).apply {
            testScopeOverride = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
        }

        repository = BenchmarkRepository(
            sessionDao = db.benchmarkSessionDao(),
            recorderEngine = recorderEngine,
            csvFormatter = CsvExportFormatter(),
            ioDispatcher = testDispatcher
        )

        viewModel = BenchmarkViewModel(
            benchmarkRepository = repository,
            foregroundAppDetector = foregroundDetector
        )
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        viewModel.cancelRecording()
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_hasDefaultValues() = runTest {
        val state = viewModel.uiState.value
        assertFalse(state.isRecording)
        assertEquals(0, state.durationSeconds)
        assertNull(state.liveSample)
        assertEquals("", state.activePackageName)
        assertTrue(state.sessions.isEmpty())
        assertNull(state.exportedCsvContent)
    }

    @Test
    fun startRecording_updatesRecordingState() = runTest {
        viewModel.startRecording("com.game.test", "Test Game")
        val state = viewModel.uiState.value
        assertTrue(state.isRecording)
        assertEquals("com.game.test", state.activePackageName)

        viewModel.cancelRecording()
        val afterCancel = viewModel.uiState.value
        assertFalse(afterCancel.isRecording)
    }

    @Test
    fun deleteAndClearSessions_reflectsInUiState() = runTest {
        val report = BenchmarkSessionReport(
            id = 0L,
            packageName = "com.test",
            appName = "Test",
            startTimeEpochMs = 1000L,
            endTimeEpochMs = 61000L,
            durationSeconds = 60,
            avgFps = 60.0,
            onePercentLowFps = 50.0,
            pointOnePercentLowFps = 35.0,
            minFps = 30.0,
            maxFps = 60.0,
            totalFrames = 3600,
            jankCount = 0,
            jankPercentage = 0.0,
            stutterScore = 0.0,
            avgFrameTimeMs = 16.6,
            avgCpuLoadPercent = 30,
            avgGpuLoadPercent = 40,
            avgBatteryWatts = 3.5f,
            peakTemperatureC = 36.0f,
            histogram = emptyList(),
            telemetrySeries = emptyList()
        )
        val id = repository.saveSession(report)

        val updatedSessions = repository.getAllSessions().first()
        assertEquals(1, updatedSessions.size)

        viewModel.exportSessionCsv(id)
        // Check export
        val exported = repository.exportSessionCsv(id)
        assertNotNull(exported)

        viewModel.deleteSession(id)
        val afterDelete = repository.getAllSessions().first()
        assertTrue(afterDelete.isEmpty())

        viewModel.clearExportedCsv()
        assertNull(viewModel.uiState.value.exportedCsvContent)
    }
}
