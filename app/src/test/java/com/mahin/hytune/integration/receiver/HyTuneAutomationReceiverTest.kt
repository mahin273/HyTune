package com.mahin.hytune.integration.receiver

import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mahin.hytune.core.privilege.CommandResult
import com.mahin.hytune.core.privilege.FakePrivilegeBackend
import com.mahin.hytune.core.privilege.PrivilegeBackend
import com.mahin.hytune.core.privilege.PrivilegeManager
import com.mahin.hytune.core.privilege.PrivilegeTier
import com.mahin.hytune.core.privilege.RootShellRunner
import com.mahin.hytune.core.privilege.ShizukuShellRunner
import com.mahin.hytune.data.local.datastore.AppSettingsRepository
import com.mahin.hytune.data.local.db.HyTuneDatabase
import com.mahin.hytune.domain.battery.BatteryEngine
import com.mahin.hytune.domain.battery.BatteryRepository
import com.mahin.hytune.domain.battery.ChargingSessionTracker
import com.mahin.hytune.domain.cpu.CpuControlEngine
import com.mahin.hytune.domain.detection.ForegroundAppDetector
import com.mahin.hytune.domain.detection.IgnoredPackagesFilter
import com.mahin.hytune.domain.detection.strategy.CgroupForegroundStrategy
import com.mahin.hytune.domain.detection.strategy.DumpsysForegroundStrategy
import com.mahin.hytune.domain.detection.strategy.UsageStatsForegroundStrategy
import com.mahin.hytune.domain.display.DisplayControlEngine
import com.mahin.hytune.domain.engine.ProfileEngine
import com.mahin.hytune.domain.fps.FpsSampler
import com.mahin.hytune.domain.fps.parser.GfxinfoFramestatsParser
import com.mahin.hytune.domain.fps.parser.SurfaceFlingerLatencyParser
import com.mahin.hytune.domain.gpu.GpuControlEngine
import com.mahin.hytune.domain.memory.MemoryControlEngine
import com.mahin.hytune.domain.overlay.FloatingOverlayManager
import com.mahin.hytune.domain.probe.HardwareProbe
import com.mahin.hytune.domain.profile.BuiltInProfiles
import com.mahin.hytune.domain.profile.ProfileRepository
import com.mahin.hytune.domain.storage.StorageControlEngine
import com.mahin.hytune.domain.thermal.ThermalEngine
import com.mahin.hytune.domain.thermal.ThermalRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
class HyTuneAutomationReceiverTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var db: HyTuneDatabase
    private lateinit var fakeBackend: FakePrivilegeBackend
    private lateinit var appSettingsRepository: AppSettingsRepository
    private lateinit var profileRepository: ProfileRepository
    private lateinit var profileEngine: ProfileEngine
    private lateinit var displayControlEngine: DisplayControlEngine
    private lateinit var floatingOverlayManager: FloatingOverlayManager
    private lateinit var receiver: HyTuneAutomationReceiver

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, HyTuneDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val datastoreFile = tempFolder.newFile("test_settings.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = kotlinx.coroutines.CoroutineScope(testDispatcher),
            produceFile = { datastoreFile }
        )
        appSettingsRepository = AppSettingsRepository(dataStore)

        fakeBackend = FakePrivilegeBackend()
        val policy0 = "/sys/devices/system/cpu/cpufreq/policy0"
        fakeBackend.setFile("$policy0/scaling_available_governors", "schedutil performance powersave\n")
        fakeBackend.setFile("$policy0/scaling_governor", "schedutil\n")
        fakeBackend.setFile("$policy0/scaling_available_frequencies", "300000 600000 1200000 1800000\n")
        fakeBackend.setFile("$policy0/scaling_min_freq", "300000\n")
        fakeBackend.setFile("$policy0/scaling_max_freq", "1800000\n")
        fakeBackend.setFile("$policy0/affected_cpus", "0 1 2 3\n")
        fakeBackend.setFile("/sys/devices/system/cpu/online", "0-3\n")
        fakeBackend.setFile("/sys/devices/system/cpu/cpu0/online", "1\n")
        fakeBackend.setFile("/sys/devices/system/cpu/cpu1/online", "1\n")
        fakeBackend.setFile("/sys/devices/system/cpu/cpu2/online", "1\n")
        fakeBackend.setFile("/sys/devices/system/cpu/cpu3/online", "1\n")

        val kgsl = "/sys/class/kgsl/kgsl-3d0"
        fakeBackend.setFile("$kgsl/devfreq/governor", "msm-adreno-tz\n")
        fakeBackend.setFile("$kgsl/devfreq/available_governors", "msm-adreno-tz performance powersave\n")
        fakeBackend.setFile("$kgsl/devfreq/available_frequencies", "200000000 400000000 600000000\n")
        fakeBackend.setFile("$kgsl/devfreq/min_freq", "200000000\n")
        fakeBackend.setFile("$kgsl/devfreq/max_freq", "600000000\n")

        fakeBackend.setFile("/proc/sys/vm/swappiness", "60\n")
        fakeBackend.setFile("/proc/sys/vm/vfs_cache_pressure", "100\n")
        fakeBackend.setFile("/proc/sys/vm/drop_caches", "0\n")

        val blockDev = "/sys/block/sda"
        fakeBackend.setFile("$blockDev/queue/scheduler", "[mq-deadline] none kyber\n")
        fakeBackend.setFile("$blockDev/queue/read_ahead_kb", "128\n")
        fakeBackend.setFile("$blockDev/queue/rotational", "0\n")
        fakeBackend.setFile("$blockDev/size", "250069680\n")

        profileRepository = ProfileRepository(db.profileDao(), appSettingsRepository, testDispatcher)

        val probe = HardwareProbe(fakeBackend)
        val cpuEngine = CpuControlEngine(fakeBackend, probe, testDispatcher)
        val gpuEngine = GpuControlEngine(fakeBackend, probe, testDispatcher)
        val memEngine = MemoryControlEngine(fakeBackend, testDispatcher)
        val storageEngine = StorageControlEngine(fakeBackend, testDispatcher)

        profileEngine = ProfileEngine(
            cpuControlEngine = cpuEngine,
            gpuControlEngine = gpuEngine,
            memoryControlEngine = memEngine,
            storageControlEngine = storageEngine,
            profileRepository = profileRepository,
            ioDispatcher = testDispatcher
        )

        displayControlEngine = DisplayControlEngine(fakeBackend, testDispatcher)

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

        floatingOverlayManager = FloatingOverlayManager(
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

        receiver = HyTuneAutomationReceiver().apply {
            this.profileRepository = this@HyTuneAutomationReceiverTest.profileRepository
            this.profileEngine = this@HyTuneAutomationReceiverTest.profileEngine
            this.displayControlEngine = this@HyTuneAutomationReceiverTest.displayControlEngine
            this.floatingOverlayManager = this@HyTuneAutomationReceiverTest.floatingOverlayManager
            this.ioDispatcher = testDispatcher
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun onReceive_applyProfile_switchesActiveProfile() = runTest(testDispatcher) {
        val intent = Intent(HyTuneAutomationReceiver.ACTION_APPLY_PROFILE).apply {
            putExtra(HyTuneAutomationReceiver.EXTRA_PROFILE_ID, BuiltInProfiles.PERFORMANCE.id)
        }

        receiver.onReceiveAsync(context, intent)?.join()
        advanceUntilIdle()

        val active = profileRepository.getActiveProfile().first()
        assertEquals(BuiltInProfiles.PERFORMANCE.id, active.id)
    }

    @Test
    fun onReceive_toggleOverlay_withoutPermission_handlesGracefully() = runTest(testDispatcher) {
        val intent = Intent(HyTuneAutomationReceiver.ACTION_TOGGLE_OVERLAY)
        receiver.onReceiveAsync(context, intent)?.join()
        advanceUntilIdle()
    }

    @Test
    fun onReceive_resetDisplay_executesDisplayReset() = runTest(testDispatcher) {
        val intent = Intent(HyTuneAutomationReceiver.ACTION_RESET_DISPLAY)
        receiver.onReceiveAsync(context, intent)?.join()
        advanceUntilIdle()

        val executed = fakeBackend.getExecutedCommands()
        assertTrue(executed.contains("wm size reset"))
        assertTrue(executed.contains("wm density reset"))
        assertTrue(executed.contains("settings delete system min_refresh_rate"))
        assertTrue(executed.contains("settings delete system peak_refresh_rate"))
    }
}
