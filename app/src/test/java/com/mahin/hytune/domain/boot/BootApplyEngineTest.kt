package com.mahin.hytune.domain.boot

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mahin.hytune.core.privilege.CommandResult
import com.mahin.hytune.core.privilege.FakePrivilegeBackend
import com.mahin.hytune.data.local.datastore.AppSettingsRepository
import com.mahin.hytune.data.local.db.HyTuneDatabase
import com.mahin.hytune.data.local.db.entity.CustomScriptEntity
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
@Config(manifest = Config.NONE)
class BootApplyEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var db: HyTuneDatabase
    private lateinit var fakeBackend: FakePrivilegeBackend
    private lateinit var appSettingsRepository: AppSettingsRepository
    private lateinit var bootloopCrashGuard: BootloopCrashGuard
    private lateinit var profileEngine: ProfileEngine
    private lateinit var profileRepository: ProfileRepository
    private lateinit var scriptRepository: ScriptRepository
    private lateinit var notificationManager: DaemonNotificationManager
    private lateinit var bootApplyEngine: BootApplyEngine

    @Before
    fun setUp() {
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
            produceFile = { tempFolder.newFile("test_boot_settings.preferences_pb") }
        )
        appSettingsRepository = AppSettingsRepository(testDataStore)
        bootloopCrashGuard = BootloopCrashGuard(appSettingsRepository)

        val probe = HardwareProbe(fakeBackend)
        val cpuEngine = CpuControlEngine(fakeBackend, probe, testDispatcher)
        val gpuEngine = GpuControlEngine(fakeBackend, probe, testDispatcher)
        val memoryEngine = MemoryControlEngine(fakeBackend, testDispatcher)
        val storageEngine = StorageControlEngine(fakeBackend, testDispatcher)

        profileRepository = ProfileRepository(db.profileDao(), appSettingsRepository, testDispatcher)

        profileEngine = ProfileEngine(
            cpuControlEngine = cpuEngine,
            gpuControlEngine = gpuEngine,
            memoryControlEngine = memoryEngine,
            storageControlEngine = storageEngine,
            profileRepository = profileRepository,
            ioDispatcher = testDispatcher
        )

        val scriptExecutionEngine = ScriptExecutionEngine(fakeBackend, testDispatcher)
        scriptRepository = ScriptRepository(db.customScriptDao(), scriptExecutionEngine, testDispatcher)
        notificationManager = DaemonNotificationManager(context)

        bootApplyEngine = BootApplyEngine(
            appSettingsRepository = appSettingsRepository,
            bootloopCrashGuard = bootloopCrashGuard,
            profileEngine = profileEngine,
            scriptRepository = scriptRepository,
            notificationManager = notificationManager,
            ioDispatcher = testDispatcher
        )
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        db.close()
    }

    @Test
    fun whenApplyOnBootDisabled_abortsEarlyWithoutApplying() = runTest(testDispatcher) {
        appSettingsRepository.setApplyOnBoot(false)

        val result = bootApplyEngine.startBootApply(overrideDelayMs = 0L)

        assertTrue(result.success)
        assertEquals("DISABLED", result.skippedReason)
    }

    @Test
    fun whenSafeRecoveryModeActive_abortsApplyAndSetsWarning() = runTest(testDispatcher) {
        appSettingsRepository.setApplyOnBoot(true)
        appSettingsRepository.setBootloopCrashCount(2)

        val result = bootApplyEngine.startBootApply(overrideDelayMs = 0L)

        assertFalse(result.success)
        assertEquals("SAFE_RECOVERY_MODE", result.skippedReason)
        assertTrue(appSettingsRepository.settings.first().safeRecoveryModeActive)
        assertNotNull(appSettingsRepository.settings.first().lastBootApplyStatus)
    }

    @Test
    fun whenApplyOnBootEnabled_appliesProfileAndExecutesBootScripts() = runTest(testDispatcher) {
        appSettingsRepository.setApplyOnBoot(true)
        appSettingsRepository.setActiveProfileId("balance")

        db.customScriptDao().insertScript(
            CustomScriptEntity(
                name = "ZRAM Compact",
                scriptContent = "echo 1 > /sys/block/zram0/compact",
                runAtBoot = true
            )
        )

        fakeBackend.registerCommandHandler("echo 1 > /sys/block/zram0/compact") {
            CommandResult.success()
        }

        val result = bootApplyEngine.startBootApply(overrideDelayMs = 0L)

        assertTrue(result.success)
        assertEquals("balance", result.appliedProfileId)
        assertEquals(1, result.executedScriptCount)
        assertFalse(appSettingsRepository.settings.first().isApplyingSettings)
        assertTrue(appSettingsRepository.settings.first().lastBootApplyStatus!!.contains("Success"))
    }

    @Test
    fun whenScriptExecutionFails_capturesErrorAndReportsStatus() = runTest(testDispatcher) {
        appSettingsRepository.setApplyOnBoot(true)
        appSettingsRepository.setActiveProfileId("balance")

        db.customScriptDao().insertScript(
            CustomScriptEntity(
                name = "Failing Script",
                scriptContent = "fail_command",
                runAtBoot = true
            )
        )

        fakeBackend.registerCommandHandler("fail_command") {
            CommandResult.failure(exitCode = 1, errorLines = listOf("Execution error"))
        }

        val result = bootApplyEngine.startBootApply(overrideDelayMs = 0L)

        assertTrue(result.success)
        assertEquals(0, result.executedScriptCount)
        assertFalse(appSettingsRepository.settings.first().isApplyingSettings)
    }

    @Test
    fun testBootApplyNow_executesWithoutDelay() = runTest(testDispatcher) {
        appSettingsRepository.setApplyOnBoot(true)
        appSettingsRepository.setActiveProfileId("balance")

        val result = bootApplyEngine.testBootApplyNow()

        assertTrue(result.success)
        assertEquals("balance", result.appliedProfileId)
    }
}
