package com.mahin.hytune.domain.engine

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mahin.hytune.core.privilege.CommandResult
import com.mahin.hytune.core.privilege.FakePrivilegeBackend
import com.mahin.hytune.data.local.datastore.AppSettingsRepository
import com.mahin.hytune.data.local.db.HyTuneDatabase
import com.mahin.hytune.domain.cpu.CpuControlEngine
import com.mahin.hytune.domain.gpu.GpuControlEngine
import com.mahin.hytune.domain.memory.MemoryControlEngine
import com.mahin.hytune.domain.probe.HardwareProbe
import com.mahin.hytune.domain.profile.BuiltInProfiles
import com.mahin.hytune.domain.profile.ProfileRepository
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
class ProfileEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeBackend: FakePrivilegeBackend
    private lateinit var db: HyTuneDatabase
    private lateinit var appSettingsRepository: AppSettingsRepository
    private lateinit var profileRepository: ProfileRepository

    private lateinit var cpuEngine: CpuControlEngine
    private lateinit var gpuEngine: GpuControlEngine
    private lateinit var memoryEngine: MemoryControlEngine
    private lateinit var storageEngine: StorageControlEngine
    private lateinit var profileEngine: ProfileEngine

    @Before
    fun setUp() {
        fakeBackend = FakePrivilegeBackend()

        // 1. Setup CPU sysfs
        val policy0 = "/sys/devices/system/cpu/cpufreq/policy0"
        fakeBackend.setFile("$policy0/scaling_available_governors", "schedutil powersave performance\n")
        fakeBackend.setFile("$policy0/scaling_available_frequencies", "300000 576000 844800 1113600 1401600 1804800\n")
        fakeBackend.setFile("$policy0/scaling_governor", "schedutil\n")
        fakeBackend.setFile("$policy0/scaling_min_freq", "300000\n")
        fakeBackend.setFile("$policy0/scaling_max_freq", "1804800\n")
        fakeBackend.setFile("$policy0/scaling_cur_freq", "844800\n")
        fakeBackend.setFile("$policy0/affected_cpus", "0 1 2 3\n")

        fakeBackend.setFile("/sys/devices/system/cpu/cpu0/online", "1\n")
        fakeBackend.setFile("/sys/devices/system/cpu/cpu1/online", "1\n")
        fakeBackend.setFile("/sys/devices/system/cpu/cpu2/online", "1\n")
        fakeBackend.setFile("/sys/devices/system/cpu/cpu3/online", "1\n")

        // 2. Setup GPU sysfs
        val kgsl = "/sys/class/kgsl/kgsl-3d0"
        fakeBackend.setFile("$kgsl/gpu_available_frequencies", "300000 450000 650000 800000\n")
        fakeBackend.setFile("$kgsl/devfreq/cur_freq", "450000\n")
        fakeBackend.setFile("$kgsl/devfreq/min_freq", "300000\n")
        fakeBackend.setFile("$kgsl/devfreq/max_freq", "800000\n")
        fakeBackend.setFile("$kgsl/devfreq/governor", "msm-adreno-tz\n")
        fakeBackend.setFile("$kgsl/devfreq/available_governors", "msm-adreno-tz performance powersave\n")

        // 3. Setup Memory sysfs
        fakeBackend.setFile("/proc/sys/vm/swappiness", "80\n")
        fakeBackend.setFile("/proc/sys/vm/vfs_cache_pressure", "100\n")
        fakeBackend.setFile("/proc/sys/vm/drop_caches", "0\n")

        // 4. Setup Storage sysfs
        fakeBackend.setFile("/sys/block/sda/queue/scheduler", "none [mq-deadline] kyber\n")
        fakeBackend.setFile("/sys/block/sda/queue/read_ahead_kb", "512\n")
        fakeBackend.setFile("/sys/block/sda/queue/rotational", "0\n")
        fakeBackend.setFile("/sys/block/sda/size", "250069680\n")

        // 5. Build Subsystems
        val hardwareProbe = HardwareProbe(fakeBackend)
        cpuEngine = CpuControlEngine(fakeBackend, hardwareProbe, testDispatcher)
        gpuEngine = GpuControlEngine(fakeBackend, hardwareProbe, testDispatcher)
        memoryEngine = MemoryControlEngine(fakeBackend, testDispatcher)
        storageEngine = StorageControlEngine(fakeBackend, testDispatcher)

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
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun captureCurrentState_snapshotsAllHardwareSubsystems() = runTest(testDispatcher) {
        val result = profileEngine.captureCurrentState()
        assertTrue(result.isSuccess)

        val snapshot = result.getOrThrow()
        assertEquals("schedutil", snapshot.cpuGovernors[0])
        assertEquals(300000L, snapshot.cpuMinFreqsKHz[0])
        assertEquals(1804800L, snapshot.cpuMaxFreqsKHz[0])
        assertEquals(true, snapshot.cpuCoreStates[0])

        assertEquals("msm-adreno-tz", snapshot.gpuGovernor)
        assertEquals(300000L, snapshot.gpuMinFreqKHz)
        assertEquals(800000L, snapshot.gpuMaxFreqKHz)

        assertEquals(80, snapshot.swappiness)
        assertEquals(100, snapshot.vfsCachePressure)

        assertEquals("mq-deadline", snapshot.storageSchedulers["sda"])
        assertEquals(512, snapshot.storageReadAheadKb["sda"])
    }

    @Test
    fun computeDiff_returnsEmptyDiff_whenStateMatchesProfile() = runTest(testDispatcher) {
        val diffResult = profileEngine.computeDiff(BuiltInProfiles.BALANCE)
        assertTrue(diffResult.isSuccess)

        val diff = diffResult.getOrThrow()
        assertFalse(diff.hasChanges)
    }

    @Test
    fun computeDiff_calculatesAccurateChanges_whenStateDiffers() = runTest(testDispatcher) {
        val diffResult = profileEngine.computeDiff(BuiltInProfiles.PERFORMANCE)
        assertTrue(diffResult.isSuccess)

        val diff = diffResult.getOrThrow()
        assertTrue(diff.hasChanges)

        val cpuDiff = diff.cpuDiffs.first()
        assertEquals("performance", cpuDiff.targetGovernor)
        assertEquals(844800L, cpuDiff.targetMinFreqKHz) // 40% of 6 steps -> index 2 (844,800)

        assertNotNull(diff.gpuDiff)
        assertEquals("performance", diff.gpuDiff?.targetGovernor)

        assertEquals(120, diff.memoryDiff?.targetSwappiness)
        assertEquals(80, diff.memoryDiff?.targetVfsCachePressure)

        val sdaDiff = diff.storageDiffs.first()
        assertEquals("none", sdaDiff.targetScheduler)
        assertEquals(1024, sdaDiff.targetReadAheadKb)
    }

    @Test
    fun applyProfile_executesOrderedWritesAndUpdatesActiveProfile() = runTest(testDispatcher) {
        profileRepository.seedBuiltInProfilesIfEmpty()

        val applyResult = profileEngine.applyProfile(BuiltInProfiles.PERFORMANCE)
        assertTrue(applyResult.isSuccess)

        val res = applyResult.getOrThrow()
        assertTrue(res.isSuccess)
        assertEquals("performance", res.profileId)
        assertTrue(res.verifiedChangesCount > 0)

        // Verify writes in fake sysfs
        val policy0 = "/sys/devices/system/cpu/cpufreq/policy0"
        assertEquals("performance", fakeBackend.getFile("$policy0/scaling_governor"))
        assertEquals("844800", fakeBackend.getFile("$policy0/scaling_min_freq"))
        assertEquals("1804800", fakeBackend.getFile("$policy0/scaling_max_freq"))

        val kgsl = "/sys/class/kgsl/kgsl-3d0"
        assertEquals("performance", fakeBackend.getFile("$kgsl/devfreq/governor"))

        assertEquals("120", fakeBackend.getFile("/proc/sys/vm/swappiness"))
        assertEquals("80", fakeBackend.getFile("/proc/sys/vm/vfs_cache_pressure"))

        assertEquals("none", fakeBackend.getFile("/sys/block/sda/queue/scheduler"))
        assertEquals("1024", fakeBackend.getFile("/sys/block/sda/queue/read_ahead_kb"))

        // Verify active profile in DataStore
        assertEquals("performance", profileRepository.getActiveProfileId().first())
    }

    @Test
    fun applyProfile_preActions_dropsCachesAndRunsTrim_whenConfigured() = runTest(testDispatcher) {
        fakeBackend.registerCommandHandler("fstrim") {
            CommandResult.success(listOf("/data: 1000000 bytes trimmed"))
        }

        val applyResult = profileEngine.applyProfile(BuiltInProfiles.FAST)
        assertTrue(applyResult.isSuccess)

        // Drop caches written
        assertEquals("3", fakeBackend.getFile("/proc/sys/vm/drop_caches"))

        // TRIM executed
        val history = fakeBackend.getExecutedCommands()
        assertTrue(history.contains("fstrim -v /data"))
    }

    @Test
    fun applyProfile_rollsBackToInitialState_whenSubsystemWriteFails() = runTest(testDispatcher) {
        // Intentionally make GPU governor write fail
        fakeBackend.setReadOnly("/sys/class/kgsl/kgsl-3d0/devfreq/governor", true)

        val applyResult = profileEngine.applyProfile(BuiltInProfiles.PERFORMANCE, rollbackOnFailure = true)
        assertTrue(applyResult.isFailure)

        // Verify CPU governor and frequencies were rolled back to initial state
        val policy0 = "/sys/devices/system/cpu/cpufreq/policy0"
        assertEquals("schedutil", fakeBackend.getFile("$policy0/scaling_governor"))
        assertEquals("300000", fakeBackend.getFile("$policy0/scaling_min_freq"))
        assertEquals("1804800", fakeBackend.getFile("$policy0/scaling_max_freq"))

        // VM rolled back
        assertEquals("80", fakeBackend.getFile("/proc/sys/vm/swappiness"))
        assertEquals("100", fakeBackend.getFile("/proc/sys/vm/vfs_cache_pressure"))
    }

    @Test
    fun applyProfileById_resolvesAndAppliesProfileCorrectly() = runTest(testDispatcher) {
        profileRepository.seedBuiltInProfilesIfEmpty()

        val applyRes = profileEngine.applyProfileById("powersave")
        assertTrue(applyRes.isSuccess)

        val res = applyRes.getOrThrow()
        assertEquals("powersave", res.profileId)

        val policy0 = "/sys/devices/system/cpu/cpufreq/policy0"
        assertEquals("powersave", fakeBackend.getFile("$policy0/scaling_governor"))
        assertEquals("40", fakeBackend.getFile("/proc/sys/vm/swappiness"))
        assertEquals("128", fakeBackend.getFile("/sys/block/sda/queue/read_ahead_kb"))
    }
}
