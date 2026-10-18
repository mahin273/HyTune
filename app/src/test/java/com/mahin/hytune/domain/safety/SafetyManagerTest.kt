package com.mahin.hytune.domain.safety

import android.content.Context
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
import com.mahin.hytune.domain.profile.BuiltInProfiles
import com.mahin.hytune.domain.profile.ProfileRepository
import com.mahin.hytune.domain.safety.model.RiskLevel
import com.mahin.hytune.domain.storage.StorageControlEngine
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SafetyManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeBackend: FakePrivilegeBackend
    private lateinit var db: HyTuneDatabase
    private lateinit var appSettingsRepository: AppSettingsRepository
    private lateinit var profileRepository: ProfileRepository
    private lateinit var profileEngine: ProfileEngine
    private lateinit var whitelist: SysfsPathWhitelist
    private lateinit var crashGuard: BootloopCrashGuard
    private lateinit var baselineStore: BaselineSnapshotStore
    private lateinit var safetyManager: SafetyManager

    @Before
    fun setUp() {
        fakeBackend = FakePrivilegeBackend()

        // Setup CPU
        val policy0 = "/sys/devices/system/cpu/cpufreq/policy0"
        fakeBackend.setFile("$policy0/scaling_available_governors", "schedutil performance powersave\n")
        fakeBackend.setFile("$policy0/scaling_available_frequencies", "300000 710400 1804800\n")
        fakeBackend.setFile("$policy0/scaling_governor", "schedutil\n")
        fakeBackend.setFile("$policy0/scaling_min_freq", "300000\n")
        fakeBackend.setFile("$policy0/scaling_max_freq", "1804800\n")
        fakeBackend.setFile("/sys/devices/system/cpu/cpu0/online", "1\n")
        fakeBackend.setFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq", "710400\n")

        // Setup GPU
        fakeBackend.setFile("/sys/class/kgsl/kgsl-3d0/devfreq/available_governors", "msm-adreno-tz powersave performance\n")
        fakeBackend.setFile("/sys/class/kgsl/kgsl-3d0/devfreq/available_frequencies", "300000000 500000000 800000000\n")
        fakeBackend.setFile("/sys/class/kgsl/kgsl-3d0/devfreq/governor", "msm-adreno-tz\n")
        fakeBackend.setFile("/sys/class/kgsl/kgsl-3d0/devfreq/min_freq", "300000000\n")
        fakeBackend.setFile("/sys/class/kgsl/kgsl-3d0/devfreq/max_freq", "800000000\n")

        // Setup VM
        fakeBackend.setFile("/proc/sys/vm/swappiness", "100\n")
        fakeBackend.setFile("/proc/sys/vm/vfs_cache_pressure", "100\n")

        // Setup Storage
        fakeBackend.setFile("/sys/block/sda/queue/scheduler", "[mq-deadline] none\n")
        fakeBackend.setFile("/sys/block/sda/queue/read_ahead_kb", "512\n")

        val probe = HardwareProbe(fakeBackend)
        val cpuEngine = CpuControlEngine(fakeBackend, probe, testDispatcher)
        val gpuEngine = GpuControlEngine(fakeBackend, probe, testDispatcher)
        val memoryEngine = MemoryControlEngine(fakeBackend, testDispatcher)
        val storageEngine = StorageControlEngine(fakeBackend, testDispatcher)

        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HyTuneDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val testDataStore = PreferenceDataStoreFactory.create(
            produceFile = { tempFolder.newFile("test_settings.preferences_pb") }
        )
        appSettingsRepository = AppSettingsRepository(testDataStore)

        profileRepository = ProfileRepository(
            profileDao = db.profileDao(),
            appSettingsRepository = appSettingsRepository,
            ioDispatcher = testDispatcher
        )

        profileEngine = ProfileEngine(
            cpuControlEngine = cpuEngine,
            gpuControlEngine = gpuEngine,
            memoryControlEngine = memoryEngine,
            storageControlEngine = storageEngine,
            profileRepository = profileRepository,
            ioDispatcher = testDispatcher
        )

        whitelist = SysfsPathWhitelist()
        crashGuard = BootloopCrashGuard(appSettingsRepository)
        baselineStore = BaselineSnapshotStore(appSettingsRepository, profileEngine)

        safetyManager = SafetyManager(
            sysfsPathWhitelist = whitelist,
            bootloopCrashGuard = crashGuard,
            baselineSnapshotStore = baselineStore,
            profileEngine = profileEngine,
            profileRepository = profileRepository,
            appSettingsRepository = appSettingsRepository
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun prepareDryRun_transformsProfileDiffIntoProposedChanges() = runTest(testDispatcher) {
        val changes = safetyManager.prepareDryRun(BuiltInProfiles.PERFORMANCE).getOrThrow()
        assertNotNull(changes)
        assertTrue(changes.isNotEmpty())

        val cpuGovChange = changes.find { it.parameter == "Governor" && it.subsystem.contains("CPU") }
        assertNotNull(cpuGovChange)
        assertEquals("performance", cpuGovChange?.targetValue)
        assertEquals(RiskLevel.MEDIUM, cpuGovChange?.riskLevel)
        assertTrue(cpuGovChange?.isWhitelisted == true)
    }

    @Test
    fun prepareDryRun_flagsTrimAsHighRisk() = runTest(testDispatcher) {
        val changes = safetyManager.prepareDryRun(BuiltInProfiles.FAST).getOrThrow()
        val trimChange = changes.find { it.parameter.contains("FSTRIM") }
        assertNotNull(trimChange)
        assertEquals(RiskLevel.HIGH, trimChange?.riskLevel)
    }

    @Test
    fun applyProfileSafely_armsCrashGuardAndAppliesProfile() = runTest(testDispatcher) {
        val result = safetyManager.applyProfileSafely(BuiltInProfiles.POWERSAVE).getOrThrow()
        assertNotNull(result)
        assertEquals("powersave", result.profileId)

        // Baseline should now be captured
        assertTrue(baselineStore.hasBaselineSnapshot())

        // System should not be left in isApplying state
        val settings = appSettingsRepository.settings.first()
        assertFalse(settings.isApplyingSettings)
        assertTrue(settings.lastApplyTimestampEpochMs > 0L)
    }

    @Test
    fun applyProfileSafely_blocksCustomProfilesWhenSafeRecoveryModeIsActive() = runTest(testDispatcher) {
        appSettingsRepository.setSafeRecoveryModeActive(true)

        val failure = safetyManager.applyProfileSafely(BuiltInProfiles.PERFORMANCE)
        assertTrue(failure.isFailure)
        assertTrue(failure.exceptionOrNull()?.message?.contains("Safe Recovery Mode") == true)
    }

    @Test
    fun panicRestore_revertsHardwareToFactoryBaselineAndResetsCrashCounter() = runTest(testDispatcher) {
        // Initial baseline captured with stock values (governor = schedutil, swappiness = 100)
        baselineStore.saveBaselineIfNotPresent().getOrThrow()

        // Apply performance profile
        safetyManager.applyProfileSafely(BuiltInProfiles.PERFORMANCE).getOrThrow()
        assertEquals("performance", fakeBackend.readFile("/sys/devices/system/cpu/cpufreq/policy0/scaling_governor").getOrThrow().trim())

        // Set crash count
        appSettingsRepository.setBootloopCrashCount(2)
        appSettingsRepository.setSafeRecoveryModeActive(true)

        // Trigger Panic Restore
        val restoreResult = safetyManager.panicRestore()
        assertTrue(restoreResult.isSuccess)

        // Verify hardware returned to baseline
        assertEquals("schedutil", fakeBackend.readFile("/sys/devices/system/cpu/cpufreq/policy0/scaling_governor").getOrThrow().trim())
        assertEquals("100", fakeBackend.readFile("/proc/sys/vm/swappiness").getOrThrow().trim())

        // Verify active profile reset and crash counter cleared
        assertEquals("balance", appSettingsRepository.settings.first().activeProfileId)
        assertEquals(0, appSettingsRepository.settings.first().bootloopCrashCount)
        assertFalse(appSettingsRepository.settings.first().safeRecoveryModeActive)
    }

    @Test
    fun safetyStateFlow_emitsCurrentSafetySettings() = runTest(testDispatcher) {
        val state = safetyManager.safetyState.first()
        assertEquals(0, state.bootloopCrashCount)
        assertFalse(state.isSafeRecoveryModeActive)
        assertTrue(state.dryRunPreviewEnabled)
    }
}
