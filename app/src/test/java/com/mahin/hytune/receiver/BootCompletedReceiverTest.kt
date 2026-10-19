package com.mahin.hytune.receiver

import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mahin.hytune.core.privilege.CommandResult
import com.mahin.hytune.core.privilege.FakePrivilegeBackend
import com.mahin.hytune.data.local.datastore.AppSettingsRepository
import com.mahin.hytune.data.local.db.HyTuneDatabase
import com.mahin.hytune.domain.boot.BootApplyEngine
import com.mahin.hytune.domain.cpu.CpuControlEngine
import com.mahin.hytune.domain.engine.ProfileEngine
import com.mahin.hytune.domain.gpu.GpuControlEngine
import com.mahin.hytune.domain.memory.MemoryControlEngine
import com.mahin.hytune.domain.probe.HardwareProbe
import com.mahin.hytune.domain.profile.ProfileRepository
import com.mahin.hytune.domain.safety.BootloopCrashGuard
import com.mahin.hytune.domain.script.ScriptRepository
import com.mahin.hytune.domain.script.engine.ScriptExecutionEngine
import com.mahin.hytune.domain.storage.StorageControlEngine
import com.mahin.hytune.service.notification.DaemonNotificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = com.mahin.hytune.HyTuneApp::class)
class BootCompletedReceiverTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var db: HyTuneDatabase
    private lateinit var fakeBackend: FakePrivilegeBackend
    private lateinit var appSettingsRepository: AppSettingsRepository
    private lateinit var receiver: BootCompletedReceiver

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, HyTuneDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        fakeBackend = FakePrivilegeBackend()

        // Setup CPU sysfs node
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

        // Setup GPU
        val kgsl = "/sys/class/kgsl/kgsl-3d0"
        fakeBackend.setFile("$kgsl/devfreq/governor", "msm-adreno-tz\n")
        fakeBackend.setFile("$kgsl/devfreq/available_governors", "msm-adreno-tz performance powersave\n")
        fakeBackend.setFile("$kgsl/devfreq/available_frequencies", "200000000 400000000 600000000\n")
        fakeBackend.setFile("$kgsl/devfreq/min_freq", "200000000\n")
        fakeBackend.setFile("$kgsl/devfreq/max_freq", "600000000\n")

        // Setup VM
        fakeBackend.setFile("/proc/sys/vm/swappiness", "60\n")
        fakeBackend.setFile("/proc/sys/vm/vfs_cache_pressure", "100\n")
        fakeBackend.setFile("/proc/sys/vm/drop_caches", "0\n")

        // Setup Storage
        val blockDev = "/sys/block/sda"
        fakeBackend.setFile("$blockDev/queue/scheduler", "[mq-deadline] none kyber\n")
        fakeBackend.setFile("$blockDev/queue/read_ahead_kb", "128\n")
        fakeBackend.setFile("$blockDev/queue/rotational", "0\n")
        fakeBackend.setFile("$blockDev/size", "250069680\n")

        val testDataStore = PreferenceDataStoreFactory.create(
            produceFile = { tempFolder.newFile("test_receiver_settings.preferences_pb") }
        )
        appSettingsRepository = AppSettingsRepository(testDataStore)
        val bootloopCrashGuard = BootloopCrashGuard(appSettingsRepository)

        val probe = HardwareProbe(fakeBackend)
        val cpuEngine = CpuControlEngine(fakeBackend, probe, testDispatcher)
        val gpuEngine = GpuControlEngine(fakeBackend, probe, testDispatcher)
        val memoryEngine = MemoryControlEngine(fakeBackend, testDispatcher)
        val storageEngine = StorageControlEngine(fakeBackend, testDispatcher)

        val profileRepository = ProfileRepository(db.profileDao(), appSettingsRepository, testDispatcher)

        val profileEngine = ProfileEngine(
            cpuControlEngine = cpuEngine,
            gpuControlEngine = gpuEngine,
            memoryControlEngine = memoryEngine,
            storageControlEngine = storageEngine,
            profileRepository = profileRepository,
            ioDispatcher = testDispatcher
        )

        val scriptExecutionEngine = ScriptExecutionEngine(fakeBackend, testDispatcher)
        val scriptRepository = ScriptRepository(db.customScriptDao(), scriptExecutionEngine, testDispatcher)
        val notificationManager = DaemonNotificationManager(context)

        val bootApplyEngine = BootApplyEngine(
            appSettingsRepository = appSettingsRepository,
            bootloopCrashGuard = bootloopCrashGuard,
            profileEngine = profileEngine,
            scriptRepository = scriptRepository,
            notificationManager = notificationManager,
            ioDispatcher = testDispatcher
        )

        receiver = BootCompletedReceiver().apply {
            this.bootApplyEngine = bootApplyEngine
            this.coroutineDispatcher = testDispatcher
        }
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun onReceive_unrelatedAction_ignored() = runTest(testDispatcher) {
        appSettingsRepository.setApplyOnBoot(true)
        val intent = Intent(Intent.ACTION_SCREEN_ON)

        val job = receiver.handleBootIntent(intent)
        job?.join()

        assertNull(appSettingsRepository.settings.first().lastBootApplyStatus)
    }

    @Test
    fun onReceive_bootCompleted_triggersBootApply() = runTest(testDispatcher) {
        appSettingsRepository.setApplyOnBoot(true)
        appSettingsRepository.setBootApplyDelaySeconds(0)
        val intent = Intent(Intent.ACTION_BOOT_COMPLETED)

        val job = receiver.handleBootIntent(intent)
        job?.join()
        advanceUntilIdle()

        assertNotNull(appSettingsRepository.settings.first().lastBootApplyStatus)
    }

    @Test
    fun onReceive_quickbootPowerOn_triggersBootApply() = runTest(testDispatcher) {
        appSettingsRepository.setApplyOnBoot(true)
        appSettingsRepository.setBootApplyDelaySeconds(0)
        val intent = Intent("android.intent.action.QUICKBOOT_POWERON")

        val job = receiver.handleBootIntent(intent)
        job?.join()
        advanceUntilIdle()

        assertNotNull(appSettingsRepository.settings.first().lastBootApplyStatus)
    }
}
