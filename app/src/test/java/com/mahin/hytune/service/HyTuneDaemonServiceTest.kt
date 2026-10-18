package com.mahin.hytune.service

import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mahin.hytune.core.privilege.FakePrivilegeBackend
import com.mahin.hytune.data.local.datastore.AppSettingsRepository
import com.mahin.hytune.data.local.db.HyTuneDatabase
import com.mahin.hytune.domain.cpu.CpuControlEngine
import com.mahin.hytune.domain.engine.ProfileEngine
import com.mahin.hytune.domain.gpu.GpuControlEngine
import com.mahin.hytune.domain.memory.MemoryControlEngine
import com.mahin.hytune.domain.probe.HardwareProbe
import com.mahin.hytune.domain.profile.ProfileRepository
import com.mahin.hytune.domain.safety.BaselineSnapshotStore
import com.mahin.hytune.domain.safety.BootloopCrashGuard
import com.mahin.hytune.domain.safety.SafetyManager
import com.mahin.hytune.domain.safety.SysfsPathWhitelist
import com.mahin.hytune.domain.storage.StorageControlEngine
import com.mahin.hytune.service.battery.BatteryOptimizationManager
import com.mahin.hytune.service.model.DaemonState
import com.mahin.hytune.service.notification.DaemonNotificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = com.mahin.hytune.HyTuneApp::class)
class HyTuneDaemonServiceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var db: HyTuneDatabase
    private lateinit var appSettingsRepository: AppSettingsRepository
    private lateinit var daemonNotificationManager: DaemonNotificationManager
    private lateinit var daemonController: DaemonController
    private lateinit var safetyManager: SafetyManager
    private lateinit var profileRepository: ProfileRepository
    private lateinit var serviceController: ServiceController<HyTuneDaemonService>
    private lateinit var service: HyTuneDaemonService

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()

        val fakeBackend = FakePrivilegeBackend()
        val probe = HardwareProbe(fakeBackend)
        val cpuEngine = CpuControlEngine(fakeBackend, probe, testDispatcher)
        val gpuEngine = GpuControlEngine(fakeBackend, probe, testDispatcher)
        val memoryEngine = MemoryControlEngine(fakeBackend, testDispatcher)
        val storageEngine = StorageControlEngine(fakeBackend, testDispatcher)

        db = Room.inMemoryDatabaseBuilder(context, HyTuneDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val testDataStore = PreferenceDataStoreFactory.create(
            produceFile = { tempFolder.newFile("test_daemon_svc.preferences_pb") }
        )
        appSettingsRepository = AppSettingsRepository(testDataStore)

        profileRepository = ProfileRepository(
            profileDao = db.profileDao(),
            appSettingsRepository = appSettingsRepository,
            ioDispatcher = testDispatcher
        )

        val profileEngine = ProfileEngine(
            cpuControlEngine = cpuEngine,
            gpuControlEngine = gpuEngine,
            memoryControlEngine = memoryEngine,
            storageControlEngine = storageEngine,
            profileRepository = profileRepository,
            ioDispatcher = testDispatcher
        )

        safetyManager = SafetyManager(
            sysfsPathWhitelist = SysfsPathWhitelist(),
            bootloopCrashGuard = BootloopCrashGuard(appSettingsRepository),
            baselineSnapshotStore = BaselineSnapshotStore(appSettingsRepository, profileEngine),
            profileEngine = profileEngine,
            profileRepository = profileRepository,
            appSettingsRepository = appSettingsRepository
        )

        daemonNotificationManager = DaemonNotificationManager(context)
        val batteryOptimizationManager = BatteryOptimizationManager(context)
        daemonController = DaemonController(
            context = context,
            appSettingsRepository = appSettingsRepository,
            batteryOptimizationManager = batteryOptimizationManager
        )

        serviceController = Robolectric.buildService(HyTuneDaemonService::class.java)
        service = serviceController.get()
        service.daemonNotificationManager = daemonNotificationManager
        service.daemonController = daemonController
        service.appSettingsRepository = appSettingsRepository
        service.safetyManager = safetyManager
        service.profileRepository = profileRepository
    }

    @After
    fun tearDown() {
        try {
            serviceController.destroy()
        } catch (_: Throwable) {
        }
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun startCommand_withActionStart_transitionsToRunning() {
        serviceController.create()

        val startIntent = Intent(context, HyTuneDaemonService::class.java).apply {
            action = HyTuneDaemonService.ACTION_START
        }
        service.onStartCommand(startIntent, 0, 1)

        assertEquals(DaemonState.RUNNING, service.daemonController.daemonState.value)
    }

    @Test
    fun startCommand_withActionStop_transitionsToStopped() {
        serviceController.create()

        val startIntent = Intent(context, HyTuneDaemonService::class.java).apply {
            action = HyTuneDaemonService.ACTION_START
        }
        service.onStartCommand(startIntent, 0, 1)
        assertEquals(DaemonState.RUNNING, service.daemonController.daemonState.value)

        val stopIntent = Intent(context, HyTuneDaemonService::class.java).apply {
            action = HyTuneDaemonService.ACTION_STOP
        }
        service.onStartCommand(stopIntent, 0, 2)
        assertEquals(DaemonState.STOPPED, service.daemonController.daemonState.value)
    }

    @Test
    fun serviceDestroy_transitionsStateToStopped() {
        serviceController.create()
        val startIntent = Intent(context, HyTuneDaemonService::class.java).apply {
            action = HyTuneDaemonService.ACTION_START
        }
        service.onStartCommand(startIntent, 0, 1)
        assertEquals(DaemonState.RUNNING, service.daemonController.daemonState.value)

        serviceController.destroy()
        assertEquals(DaemonState.STOPPED, service.daemonController.daemonState.value)
    }
}
