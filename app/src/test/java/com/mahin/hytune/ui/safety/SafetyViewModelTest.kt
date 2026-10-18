package com.mahin.hytune.ui.safety

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
import com.mahin.hytune.domain.profile.ProfileRepository
import com.mahin.hytune.domain.safety.BaselineSnapshotStore
import com.mahin.hytune.domain.safety.BootloopCrashGuard
import com.mahin.hytune.domain.safety.SafetyManager
import com.mahin.hytune.domain.safety.SysfsPathWhitelist
import com.mahin.hytune.domain.storage.StorageControlEngine
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
class SafetyViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeBackend: FakePrivilegeBackend
    private lateinit var db: HyTuneDatabase
    private lateinit var appSettingsRepository: AppSettingsRepository
    private lateinit var safetyManager: SafetyManager
    private lateinit var viewModel: SafetyViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeBackend = FakePrivilegeBackend()
        val policy0 = "/sys/devices/system/cpu/cpufreq/policy0"
        fakeBackend.setFile("$policy0/scaling_available_governors", "schedutil performance\n")
        fakeBackend.setFile("$policy0/scaling_available_frequencies", "300000 1804800\n")
        fakeBackend.setFile("$policy0/scaling_governor", "schedutil\n")
        fakeBackend.setFile("$policy0/scaling_min_freq", "300000\n")
        fakeBackend.setFile("$policy0/scaling_max_freq", "1804800\n")
        fakeBackend.setFile("/sys/devices/system/cpu/cpu0/online", "1\n")
        fakeBackend.setFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq", "300000\n")

        fakeBackend.setFile("/proc/sys/vm/swappiness", "100\n")
        fakeBackend.setFile("/proc/sys/vm/vfs_cache_pressure", "100\n")

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

        val profileRepo = ProfileRepository(
            profileDao = db.profileDao(),
            appSettingsRepository = appSettingsRepository,
            ioDispatcher = testDispatcher
        )

        val profileEngine = ProfileEngine(
            cpuControlEngine = cpuEngine,
            gpuControlEngine = gpuEngine,
            memoryControlEngine = memoryEngine,
            storageControlEngine = storageEngine,
            profileRepository = profileRepo,
            ioDispatcher = testDispatcher
        )

        val whitelist = SysfsPathWhitelist()
        val crashGuard = BootloopCrashGuard(appSettingsRepository)
        val baselineStore = BaselineSnapshotStore(appSettingsRepository, profileEngine)

        safetyManager = SafetyManager(
            sysfsPathWhitelist = whitelist,
            bootloopCrashGuard = crashGuard,
            baselineSnapshotStore = baselineStore,
            profileEngine = profileEngine,
            profileRepository = profileRepo,
            appSettingsRepository = appSettingsRepository
        )

        viewModel = SafetyViewModel(safetyManager)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_reflectsDefaultSafetySettings() = runTest(testDispatcher) {
        val state = viewModel.safetyState.first()
        assertEquals(0, state.bootloopCrashCount)
        assertFalse(state.isSafeRecoveryModeActive)
        assertTrue(state.dryRunPreviewEnabled)
    }

    @Test
    fun resetCrashCounter_resetsCounterInSettings() = runTest(testDispatcher) {
        appSettingsRepository.setBootloopCrashCount(2)
        appSettingsRepository.setSafeRecoveryModeActive(true)

        viewModel.resetCrashCounter().join()

        val settings = appSettingsRepository.settings.first()
        assertEquals(0, settings.bootloopCrashCount)
        assertFalse(settings.safeRecoveryModeActive)
        assertNotNull(viewModel.statusMessage.value)
    }

    @Test
    fun setDryRunPreviewEnabled_updatesPreference() = runTest(testDispatcher) {
        viewModel.setDryRunPreviewEnabled(false).join()

        assertFalse(appSettingsRepository.settings.first().dryRunPreviewEnabled)
    }

    @Test
    fun panicRestore_executesRestoration() = runTest(testDispatcher) {
        viewModel.panicRestore().join()

        assertFalse(viewModel.isPanicRestoring.value)
        assertTrue(viewModel.statusMessage.value?.contains("successfully restored") == true)
    }
}
