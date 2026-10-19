package com.mahin.hytune.domain.benchmark

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mahin.hytune.data.local.db.HyTuneDatabase
import com.mahin.hytune.data.local.db.dao.BenchmarkSessionDao
import com.mahin.hytune.domain.benchmark.analytics.BenchmarkAnalyticsCalculator
import com.mahin.hytune.domain.benchmark.analytics.CsvExportFormatter
import com.mahin.hytune.domain.benchmark.model.BenchmarkSample
import com.mahin.hytune.domain.benchmark.model.BenchmarkSessionReport
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
class BenchmarkRepositoryTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var db: HyTuneDatabase
    private lateinit var sessionDao: BenchmarkSessionDao
    private lateinit var csvFormatter: CsvExportFormatter
    private lateinit var analyticsCalculator: BenchmarkAnalyticsCalculator
    private lateinit var recorderEngine: BenchmarkRecorderEngine
    private lateinit var repository: BenchmarkRepository

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
        sessionDao = db.benchmarkSessionDao()
        csvFormatter = CsvExportFormatter()
        analyticsCalculator = BenchmarkAnalyticsCalculator()

        // Create a minimal fake/mock setup for RecorderEngine dependencies
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
        val foregroundDetector = com.mahin.hytune.domain.detection.ForegroundAppDetector(
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

        recorderEngine = BenchmarkRecorderEngine(
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
            sessionDao = sessionDao,
            recorderEngine = recorderEngine,
            csvFormatter = csvFormatter,
            ioDispatcher = testDispatcher
        )
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        repository.cancelRecording()
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun saveSession_insertsAndAssignsGeneratedId() = runTest {
        val report = createSampleReport(0L, "com.pubg.imobile", "BGMI", 1000L)
        val id = repository.saveSession(report)
        assertTrue(id > 0L)

        val retrieved = repository.getSessionById(id)
        assertNotNull(retrieved)
        assertEquals("com.pubg.imobile", retrieved!!.packageName)
        assertEquals("BGMI", retrieved.appName)
        assertEquals(59.2, retrieved.avgFps, 0.01)
    }

    @Test
    fun getAllSessions_returnsDescendingByStartTime() = runTest {
        val r1 = createSampleReport(0L, "pkg1", "App 1", 1000L)
        val r2 = createSampleReport(0L, "pkg2", "App 2", 5000L)

        repository.saveSession(r1)
        repository.saveSession(r2)

        val list = repository.getAllSessions().first()
        assertEquals(2, list.size)
        assertEquals("pkg2", list[0].packageName)
        assertEquals("pkg1", list[1].packageName)
    }

    @Test
    fun getSessionsForPackage_filtersCorrectly() = runTest {
        val r1 = createSampleReport(0L, "com.game.a", "Game A", 1000L)
        val r2 = createSampleReport(0L, "com.game.b", "Game B", 2000L)

        repository.saveSession(r1)
        repository.saveSession(r2)

        val gameASessions = repository.getSessionsForPackage("com.game.a").first()
        assertEquals(1, gameASessions.size)
        assertEquals("Game A", gameASessions.first().appName)
    }

    @Test
    fun deleteSession_removesCorrectItem() = runTest {
        val id = repository.saveSession(createSampleReport(0L, "pkg", "App", 1000L))
        assertNotNull(repository.getSessionById(id))

        repository.deleteSession(id)
        assertNull(repository.getSessionById(id))
    }

    @Test
    fun clearAllSessions_wipesAllRecords() = runTest {
        repository.saveSession(createSampleReport(0L, "pkg1", "App 1", 1000L))
        repository.saveSession(createSampleReport(0L, "pkg2", "App 2", 2000L))

        assertEquals(2, repository.getAllSessions().first().size)

        repository.clearAllSessions()
        assertTrue(repository.getAllSessions().first().isEmpty())
    }

    @Test
    fun exportSessionCsv_formatsPersistedSession() = runTest {
        val sample = BenchmarkSample(
            timestampEpochMs = 1000L,
            relativeTimeSec = 1,
            fps = 60.0,
            frameTimeMs = 16.6,
            cpuLoadPercent = 40,
            cpuFreqMhz = 2400,
            gpuLoadPercent = 50,
            gpuFreqMhz = 500,
            batteryPowerWatts = 4.0f,
            temperatureC = 37.5f,
            ramUsedMb = 2800
        )
        val report = createSampleReport(0L, "com.test.csv", "CSV Test", 1000L).copy(
            telemetrySeries = listOf(sample)
        )
        val id = repository.saveSession(report)

        val csv = repository.exportSessionCsv(id)
        assertNotNull(csv)
        assertTrue(csv!!.contains("# HyTune Benchmark Report"))
        assertTrue(csv.contains("CSV Test (com.test.csv)"))
        assertTrue(csv.contains("1000,1,60.0,16.60,40,2400,50,500,4.00,37.5,2800"))
    }

    @Test
    fun startAndCancelRecording_updatesStateFlows() = runTest {
        repository.startRecording("com.game.test", "Game Test")
        assertTrue(repository.isRecording.value)
        assertEquals("com.game.test", repository.activePackageName.value)

        repository.cancelRecording()
        assertTrue(!repository.isRecording.value)
        assertEquals("", repository.activePackageName.value)
    }

    private fun createSampleReport(id: Long, pkg: String, name: String, startTime: Long) = BenchmarkSessionReport(
        id = id,
        packageName = pkg,
        appName = name,
        startTimeEpochMs = startTime,
        endTimeEpochMs = startTime + 60000L,
        durationSeconds = 60,
        avgFps = 59.2,
        onePercentLowFps = 48.0,
        pointOnePercentLowFps = 32.0,
        minFps = 25.0,
        maxFps = 60.5,
        totalFrames = 3550,
        jankCount = 20,
        jankPercentage = 0.5,
        stutterScore = 0.5,
        avgFrameTimeMs = 16.9,
        avgCpuLoadPercent = 42,
        avgGpuLoadPercent = 65,
        avgBatteryWatts = 4.8f,
        peakTemperatureC = 40.2f,
        histogram = emptyList(),
        telemetrySeries = emptyList()
    )
}
