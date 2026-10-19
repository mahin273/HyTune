package com.mahin.hytune.domain.automation

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
import com.mahin.hytune.data.local.db.dao.AutomationRuleDao
import com.mahin.hytune.domain.appmanager.AppOperationEngine
import com.mahin.hytune.domain.appmanager.AutoFreezeManager
import com.mahin.hytune.domain.appmanager.FreezeGroupRepository
import com.mahin.hytune.domain.appmanager.PackageSafetyGuard
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
class AutomationRepositoryTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var db: HyTuneDatabase
    private lateinit var ruleDao: AutomationRuleDao
    private lateinit var rulesEngine: AutomationRulesEngine
    private lateinit var repository: AutomationRepository

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
        ruleDao = db.automationRuleDao()

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

        val tempFile = kotlin.io.path.createTempFile("hytune_test_repo", ".preferences_pb").toFile()
        val testDataStore = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
            produceFile = { tempFile }
        )
        val profileRepo = ProfileRepository(db.profileDao(), AppSettingsRepository(testDataStore), testDispatcher)
        profileRepo.seedBuiltInProfilesIfEmpty()

        val probe = HardwareProbe(fakeBackend)
        val profileEngine = ProfileEngine(
            CpuControlEngine(fakeBackend, probe, testDispatcher),
            GpuControlEngine(fakeBackend, probe, testDispatcher),
            MemoryControlEngine(fakeBackend, testDispatcher),
            StorageControlEngine(fakeBackend, testDispatcher),
            profileRepo,
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

        rulesEngine = AutomationRulesEngine(
            context = context,
            automationRuleDao = ruleDao,
            ruleEvaluator = RuleEvaluator(),
            profileRepository = profileRepo,
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

        repository = AutomationRepository(ruleDao, rulesEngine, testDispatcher)
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        rulesEngine.stop()
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun insertAndGetAllRules_returnsDomainRules() = runTest {
        val rule = AutomationRule(
            name = "Test Rule",
            isEnabled = true,
            priority = 60,
            triggerType = RuleTriggerType.CHARGER_CONNECTED,
            actionType = RuleActionType.APPLY_PROFILE,
            actionValue = "performance"
        )
        val id = repository.insertRule(rule)
        assertTrue(id > 0)

        val all = repository.getAllRules().first()
        assertEquals(1, all.size)
        assertEquals("Test Rule", all.first().name)
        assertEquals(RuleTriggerType.CHARGER_CONNECTED, all.first().triggerType)

        val retrieved = repository.getRuleById(id)
        assertNotNull(retrieved)

        repository.setRuleEnabled(id, false)
        val enabled = repository.getEnabledRules().first()
        assertTrue(enabled.isEmpty())

        repository.deleteRule(id)
        assertNull(repository.getRuleById(id))
    }

    @Test
    fun startAndStopEngine_updatesIsRunningState() = runTest {
        repository.startEngine()
        assertTrue(repository.isEngineRunning.value)

        repository.stopEngine()
        assertTrue(!repository.isEngineRunning.value)
    }
}
