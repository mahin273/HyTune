package com.mahin.hytune.ui.automation

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mahin.hytune.core.privilege.FakePrivilegeBackend
import com.mahin.hytune.core.privilege.PrivilegeBackend
import com.mahin.hytune.core.privilege.PrivilegeManager
import com.mahin.hytune.core.privilege.PrivilegeTier
import com.mahin.hytune.core.privilege.RootShellRunner
import com.mahin.hytune.core.privilege.ShizukuShellRunner
import com.mahin.hytune.data.local.datastore.AppSettingsRepository
import com.mahin.hytune.data.local.db.HyTuneDatabase
import com.mahin.hytune.domain.appmanager.AppOperationEngine
import com.mahin.hytune.domain.appmanager.AutoFreezeManager
import com.mahin.hytune.domain.appmanager.FreezeGroupRepository
import com.mahin.hytune.domain.appmanager.PackageSafetyGuard
import com.mahin.hytune.domain.automation.AutomationRepository
import com.mahin.hytune.domain.automation.AutomationRulesEngine
import com.mahin.hytune.domain.automation.evaluator.RuleEvaluator
import com.mahin.hytune.domain.automation.model.AutomationRule
import com.mahin.hytune.domain.automation.model.RuleActionType
import com.mahin.hytune.domain.automation.model.RuleTriggerType
import com.mahin.hytune.domain.battery.BatteryEngine
import com.mahin.hytune.domain.battery.BatteryRepository
import com.mahin.hytune.domain.battery.ChargeControlEngine
import com.mahin.hytune.domain.battery.ChargingSessionTracker
import com.mahin.hytune.domain.cpu.CpuControlEngine
import com.mahin.hytune.domain.detection.ForegroundAppDetector
import com.mahin.hytune.domain.detection.IgnoredPackagesFilter
import com.mahin.hytune.domain.detection.strategy.CgroupForegroundStrategy
import com.mahin.hytune.domain.detection.strategy.DumpsysForegroundStrategy
import com.mahin.hytune.domain.detection.strategy.UsageStatsForegroundStrategy
import com.mahin.hytune.domain.display.DisplayRefreshRateController
import com.mahin.hytune.domain.engine.ProfileEngine
import com.mahin.hytune.domain.gpu.GpuControlEngine
import com.mahin.hytune.domain.memory.MemoryControlEngine
import com.mahin.hytune.domain.probe.HardwareProbe
import com.mahin.hytune.domain.profile.ProfileRepository
import com.mahin.hytune.domain.storage.StorageControlEngine
import com.mahin.hytune.domain.thermal.ThermalEngine
import com.mahin.hytune.domain.thermal.ThermalRepository
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
class AutomationViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var db: HyTuneDatabase
    private lateinit var repository: AutomationRepository
    private lateinit var profileRepository: ProfileRepository
    private lateinit var viewModel: AutomationViewModel

    @Before
    fun setUp() = runTest {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        val directExecutor = Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(context, HyTuneDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()

        val fakeBackend = FakePrivilegeBackend()
        val privilegeManager = PrivilegeManager(
            rootShellRunner = object : RootShellRunner {
                override fun isRootAvailable(): Boolean = true
                override suspend fun runCommand(command: String) = fakeBackend.exec(command)
            },
            shizukuShellRunner = object : ShizukuShellRunner {
                override fun isShizukuAvailable(): Boolean = false
                override suspend fun runCommand(command: String) = fakeBackend.exec(command)
            },
            rootBackend = object : PrivilegeBackend by fakeBackend {
                override val tier = PrivilegeTier.ROOT
            },
            shizukuBackend = object : PrivilegeBackend by fakeBackend {
                override val tier = PrivilegeTier.SHIZUKU
            },
            unprivilegedBackend = object : PrivilegeBackend by fakeBackend {
                override val tier = PrivilegeTier.NONE
            }
        )

        val tempFile = kotlin.io.path.createTempFile("hytune_test_vm", ".preferences_pb").toFile()
        val testDataStore = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
            produceFile = { tempFile }
        )
        profileRepository = ProfileRepository(db.profileDao(), AppSettingsRepository(testDataStore), testDispatcher)
        profileRepository.seedBuiltInProfilesIfEmpty()

        val probe = HardwareProbe(fakeBackend)
        val profileEngine = ProfileEngine(
            CpuControlEngine(fakeBackend, probe, testDispatcher),
            GpuControlEngine(fakeBackend, probe, testDispatcher),
            MemoryControlEngine(fakeBackend, testDispatcher),
            StorageControlEngine(fakeBackend, testDispatcher),
            profileRepository,
            testDispatcher
        )

        val safetyGuard = PackageSafetyGuard(context)
        val appOpEngine = AppOperationEngine(fakeBackend, safetyGuard, testDispatcher)
        val freezeRepo = FreezeGroupRepository(db.freezeGroupDao(), appOpEngine, safetyGuard, testDispatcher)
        val autoFreeze = AutoFreezeManager(
            context,
            freezeRepo,
            appOpEngine,
            safetyGuard,
            testDispatcher
        )

        val batteryEngine = BatteryEngine(context, fakeBackend, testDispatcher)
        val batteryRepo = BatteryRepository(
            batteryEngine,
            db.chargingSessionDao(),
            ChargingSessionTracker(context, db.chargingSessionDao(), batteryEngine, testDispatcher),
            testDispatcher
        )

        val thermalRepo = ThermalRepository(ThermalEngine(fakeBackend), db.thermalThrottleDao(), testDispatcher)

        val foregroundDetector = ForegroundAppDetector(
            privilegeManager,
            CgroupForegroundStrategy(fakeBackend),
            DumpsysForegroundStrategy(fakeBackend),
            UsageStatsForegroundStrategy(context),
            IgnoredPackagesFilter(context),
            testDispatcher
        )

        val rulesEngine = AutomationRulesEngine(
            context = context,
            automationRuleDao = db.automationRuleDao(),
            ruleEvaluator = RuleEvaluator(),
            profileRepository = profileRepository,
            profileEngine = profileEngine,
            autoFreezeManager = autoFreeze,
            chargeControlEngine = ChargeControlEngine(fakeBackend, testDispatcher),
            displayRefreshRateController = DisplayRefreshRateController(privilegeManager),
            batteryRepository = batteryRepo,
            thermalRepository = thermalRepo,
            foregroundAppDetector = foregroundDetector,
            ioDispatcher = testDispatcher
        ).apply {
            testScopeOverride = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
        }

        repository = AutomationRepository(db.automationRuleDao(), rulesEngine, testDispatcher)
        viewModel = AutomationViewModel(repository, profileRepository)
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        if (::repository.isInitialized) {
            repository.stopEngine()
        }
        if (::db.isInitialized) {
            db.close()
        }
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_hasDefaultValues() = runTest {
        val state = viewModel.uiState.value
        assertFalse(state.isEngineRunning)
        assertTrue(state.rules.isEmpty())
        assertTrue(state.lastExecutedRules.isEmpty())
    }

    @Test
    fun saveToggleAndDeleteRule_updatesUiState() = runTest {
        val rule = AutomationRule(
            name = "Screen Off Freeze",
            isEnabled = true,
            priority = 60,
            triggerType = RuleTriggerType.SCREEN_OFF,
            actionType = RuleActionType.FREEZE_BACKGROUND_APPS
        )

        viewModel.saveRule(rule)
        val rulesAfterSave = repository.getAllRules().first()
        assertEquals(1, rulesAfterSave.size)
        val savedId = rulesAfterSave.first().id

        viewModel.toggleRuleEnabled(savedId, false)
        val rulesAfterToggle = repository.getAllRules().first()
        assertFalse(rulesAfterToggle.first().isEnabled)

        viewModel.deleteRule(savedId)
        val rulesAfterDelete = repository.getAllRules().first()
        assertTrue(rulesAfterDelete.isEmpty())
    }

    @Test
    fun toggleEngine_updatesRunningState() = runTest {
        viewModel.toggleEngine(true)
        assertTrue(viewModel.uiState.value.isEngineRunning)

        viewModel.toggleEngine(false)
        assertFalse(viewModel.uiState.value.isEngineRunning)
    }
}
