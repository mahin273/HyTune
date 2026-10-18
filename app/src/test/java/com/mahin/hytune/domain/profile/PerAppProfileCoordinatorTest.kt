package com.mahin.hytune.domain.profile

import android.content.Context
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
import com.mahin.hytune.data.local.db.entity.AppRuleEntity
import com.mahin.hytune.domain.cpu.CpuControlEngine
import com.mahin.hytune.domain.detection.ForegroundAppDetector
import com.mahin.hytune.domain.detection.GameClassifier
import com.mahin.hytune.domain.detection.IgnoredPackagesFilter
import com.mahin.hytune.domain.detection.model.ForegroundInfo
import com.mahin.hytune.domain.detection.strategy.CgroupForegroundStrategy
import com.mahin.hytune.domain.detection.strategy.DumpsysForegroundStrategy
import com.mahin.hytune.domain.detection.strategy.UsageStatsForegroundStrategy
import com.mahin.hytune.domain.display.DisplayRefreshRateController
import com.mahin.hytune.domain.engine.ProfileEngine
import com.mahin.hytune.domain.gpu.GpuControlEngine
import com.mahin.hytune.domain.memory.MemoryControlEngine
import com.mahin.hytune.domain.notification.DndController
import com.mahin.hytune.domain.probe.HardwareProbe
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
class PerAppProfileCoordinatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var db: HyTuneDatabase
    private lateinit var fakeBackend: FakePrivilegeBackend
    private lateinit var privilegeManager: PrivilegeManager
    private lateinit var appSettingsRepository: AppSettingsRepository
    private lateinit var profileRepository: ProfileRepository
    private lateinit var appRuleRepository: AppRuleRepository
    private lateinit var profileEngine: ProfileEngine
    private lateinit var gameClassifier: GameClassifier
    private lateinit var displayController: DisplayRefreshRateController
    private lateinit var dndController: DndController
    private lateinit var detector: ForegroundAppDetector
    private lateinit var coordinator: PerAppProfileCoordinator

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        fakeBackend = FakePrivilegeBackend()

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

        privilegeManager = PrivilegeManager(
            rootShellRunner = rootRunner,
            shizukuShellRunner = shizukuRunner,
            rootBackend = rootBackend,
            shizukuBackend = shizukuBackend,
            unprivilegedBackend = unprivilegedBackend
        )
        privilegeManager.refresh()

        val probe = HardwareProbe(fakeBackend)
        val cpuEngine = CpuControlEngine(fakeBackend, probe, testDispatcher)
        val gpuEngine = GpuControlEngine(fakeBackend, probe, testDispatcher)
        val memoryEngine = MemoryControlEngine(fakeBackend, testDispatcher)
        val storageEngine = StorageControlEngine(fakeBackend, testDispatcher)

        db = Room.inMemoryDatabaseBuilder(context, HyTuneDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val testDataStore = PreferenceDataStoreFactory.create(
            produceFile = { tempFolder.newFile("test_coordinator.preferences_pb") }
        )
        appSettingsRepository = AppSettingsRepository(testDataStore)
        profileRepository = ProfileRepository(db.profileDao(), appSettingsRepository, testDispatcher)
        appRuleRepository = AppRuleRepository(db.appRuleDao(), testDispatcher)

        profileEngine = ProfileEngine(
            cpuControlEngine = cpuEngine,
            gpuControlEngine = gpuEngine,
            memoryControlEngine = memoryEngine,
            storageControlEngine = storageEngine,
            profileRepository = profileRepository,
            ioDispatcher = testDispatcher
        )

        gameClassifier = GameClassifier(context, db.appRuleDao())
        displayController = DisplayRefreshRateController(privilegeManager)
        dndController = DndController(privilegeManager)

        val ignoredFilter = IgnoredPackagesFilter(context)
        val cgroupStrategy = CgroupForegroundStrategy(fakeBackend)
        val dumpsysStrategy = DumpsysForegroundStrategy(fakeBackend)
        val usageStatsStrategy = UsageStatsForegroundStrategy(context)
        detector = ForegroundAppDetector(
            privilegeManager = privilegeManager,
            cgroupStrategy = cgroupStrategy,
            dumpsysStrategy = dumpsysStrategy,
            usageStatsStrategy = usageStatsStrategy,
            ignoredPackagesFilter = ignoredFilter,
            ioDispatcher = testDispatcher
        )

        coordinator = PerAppProfileCoordinator(
            context = context,
            foregroundAppDetector = detector,
            profileRepository = profileRepository,
            profileEngine = profileEngine,
            appRuleRepository = appRuleRepository,
            appSettingsRepository = appSettingsRepository,
            gameClassifier = gameClassifier,
            displayController = displayController,
            dndController = dndController,
            ioDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun handleForegroundApp_appliesConfiguredRule() = runTest(testDispatcher) {
        appSettingsRepository.setPerAppProfilesEnabled(true)
        profileRepository.setActiveProfile("balance")

        appRuleRepository.upsertRule(
            AppRuleEntity(
                packageName = "com.mihoyo.genshin",
                profileId = "fast",
                isGame = true,
                targetRefreshRate = 120,
                dndEnabled = true
            )
        )

        coordinator.handleForegroundApp(
            ForegroundInfo(
                packageName = "com.mihoyo.genshin",
                isHomeLauncher = false
            )
        )

        val state = coordinator.activePerAppState.value
        assertNotNull(state)
        assertEquals("com.mihoyo.genshin", state!!.packageName)
        assertEquals("fast", state.profileId)
        assertEquals(120, state.targetRefreshRate)
        assertTrue(state.dndEnabled)
        assertEquals("balance", state.baseProfileId)

        val currentActiveProfileId = profileRepository.getActiveProfileId().first()
        assertEquals("fast", currentActiveProfileId)
    }

    @Test
    fun handleForegroundApp_appliesDefaultPerformanceForUnconfiguredGame() = runTest(testDispatcher) {
        appSettingsRepository.setPerAppProfilesEnabled(true)
        profileRepository.setActiveProfile("balance")

        // Explicitly mark in rule as game without custom profile
        appRuleRepository.setGame("com.ea.gp.fifamobile", isGame = true)

        coordinator.handleForegroundApp(
            ForegroundInfo(
                packageName = "com.ea.gp.fifamobile",
                isHomeLauncher = false
            )
        )

        val state = coordinator.activePerAppState.value
        assertNotNull(state)
        assertEquals("com.ea.gp.fifamobile", state!!.packageName)
        assertTrue(state.isGame)
        assertEquals("balance", state.baseProfileId)
    }

    @Test
    fun handleForegroundApp_revertsWhenHomeLauncherEntersForeground() = runTest(testDispatcher) {
        appSettingsRepository.setPerAppProfilesEnabled(true)
        profileRepository.setActiveProfile("balance")

        appRuleRepository.upsertRule(
            AppRuleEntity(
                packageName = "com.game.apex",
                profileId = "fast",
                isGame = true
            )
        )

        coordinator.handleForegroundApp(
            ForegroundInfo(
                packageName = "com.game.apex",
                isHomeLauncher = false
            )
        )
        assertNotNull(coordinator.activePerAppState.value)
        assertEquals("fast", profileRepository.getActiveProfileId().first())

        // User goes home
        coordinator.handleForegroundApp(
            ForegroundInfo(
                packageName = "com.google.android.apps.nexuslauncher",
                isHomeLauncher = true
            )
        )

        assertNull(coordinator.activePerAppState.value)
        assertEquals("balance", profileRepository.getActiveProfileId().first())
    }

    @Test
    fun handleForegroundApp_preservesOriginalBaseProfileAcrossGames() = runTest(testDispatcher) {
        appSettingsRepository.setPerAppProfilesEnabled(true)
        profileRepository.setActiveProfile("powersave")

        appRuleRepository.upsertRule(AppRuleEntity("com.game.one", "fast", isGame = true))
        appRuleRepository.upsertRule(AppRuleEntity("com.game.two", "balance", isGame = true))

        // 1. Enter Game 1
        coordinator.handleForegroundApp(ForegroundInfo("com.game.one", isHomeLauncher = false))
        assertEquals("powersave", coordinator.activePerAppState.value?.baseProfileId)
        assertEquals("fast", profileRepository.getActiveProfileId().first())

        // 2. Direct switch to Game 2
        coordinator.handleForegroundApp(ForegroundInfo("com.game.two", isHomeLauncher = false))
        // Base profile MUST still be powersave
        assertEquals("powersave", coordinator.activePerAppState.value?.baseProfileId)
        assertEquals("balance", profileRepository.getActiveProfileId().first())

        // 3. User goes home
        coordinator.handleForegroundApp(ForegroundInfo("com.launcher", isHomeLauncher = true))
        assertNull(coordinator.activePerAppState.value)
        assertEquals("powersave", profileRepository.getActiveProfileId().first())
    }

    @Test
    fun handleForegroundApp_ignoresSelfPackage() = runTest(testDispatcher) {
        appSettingsRepository.setPerAppProfilesEnabled(true)
        profileRepository.setActiveProfile("balance")

        coordinator.handleForegroundApp(
            ForegroundInfo(
                packageName = context.packageName,
                isHomeLauncher = false
            )
        )

        assertNull(coordinator.activePerAppState.value)
    }

    @Test
    fun handleForegroundApp_doesNothingWhenFeatureDisabled() = runTest(testDispatcher) {
        appSettingsRepository.setPerAppProfilesEnabled(false)
        profileRepository.setActiveProfile("balance")

        appRuleRepository.upsertRule(AppRuleEntity("com.game.test", "fast", isGame = true))

        coordinator.handleForegroundApp(
            ForegroundInfo(
                packageName = "com.game.test",
                isHomeLauncher = false
            )
        )

        assertNull(coordinator.activePerAppState.value)
        assertEquals("balance", profileRepository.getActiveProfileId().first())
    }
}
