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
import com.mahin.hytune.data.local.db.entity.AutomationRuleEntity
import com.mahin.hytune.domain.appmanager.AppOperationEngine
import com.mahin.hytune.domain.appmanager.AutoFreezeManager
import com.mahin.hytune.domain.appmanager.FreezeGroupRepository
import com.mahin.hytune.domain.appmanager.PackageSafetyGuard
import com.mahin.hytune.domain.automation.evaluator.RuleEvaluator
import com.mahin.hytune.domain.automation.model.RuleActionType
import com.mahin.hytune.domain.automation.model.RuleTriggerType
import com.mahin.hytune.domain.automation.model.SystemStateSnapshot
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
import com.mahin.hytune.domain.profile.BuiltInProfiles
import com.mahin.hytune.domain.profile.ProfileRepository
import com.mahin.hytune.domain.storage.StorageControlEngine
import com.mahin.hytune.domain.thermal.ThermalEngine
import com.mahin.hytune.domain.thermal.ThermalRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
class AutomationRulesEngineTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var db: HyTuneDatabase
    private lateinit var ruleDao: AutomationRuleDao
    private lateinit var fakeBackend: FakePrivilegeBackend
    private lateinit var profileRepository: ProfileRepository
    private lateinit var profileEngine: ProfileEngine
    private lateinit var autoFreezeManager: AutoFreezeManager
    private lateinit var chargeControlEngine: ChargeControlEngine
    private lateinit var displayRefreshRateController: DisplayRefreshRateController
    private lateinit var rulesEngine: AutomationRulesEngine

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

        fakeBackend = FakePrivilegeBackend()
        val rootRunner = object : RootShellRunner {
            override fun isRootAvailable(): Boolean = true
            override suspend fun runCommand(command: String) = fakeBackend.exec(command)
        }
        val shizukuRunner = object : ShizukuShellRunner {
            override fun isShizukuAvailable(): Boolean = false
            override suspend fun runCommand(command: String) = fakeBackend.exec(command)
        }
        val privilegeManager = PrivilegeManager(
            rootShellRunner = rootRunner,
            shizukuShellRunner = shizukuRunner,
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

        val tempFile = kotlin.io.path.createTempFile("hytune_test_settings", ".preferences_pb").toFile()
        val testDataStore = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
            produceFile = { tempFile }
        )
        val appSettingsRepo = AppSettingsRepository(testDataStore)

        profileRepository = ProfileRepository(
            profileDao = db.profileDao(),
            appSettingsRepository = appSettingsRepo,
            ioDispatcher = testDispatcher
        )
        profileRepository.seedBuiltInProfilesIfEmpty()

        val hardwareProbe = HardwareProbe(fakeBackend)
        val cpuEngine = CpuControlEngine(fakeBackend, hardwareProbe, testDispatcher)
        val gpuEngine = GpuControlEngine(fakeBackend, hardwareProbe, testDispatcher)
        val memoryEngine = MemoryControlEngine(fakeBackend, testDispatcher)
        val storageEngine = StorageControlEngine(fakeBackend, testDispatcher)

        profileEngine = ProfileEngine(
            cpuControlEngine = cpuEngine,
            gpuControlEngine = gpuEngine,
            memoryControlEngine = memoryEngine,
            storageControlEngine = storageEngine,
            profileRepository = profileRepository,
            ioDispatcher = testDispatcher
        )

        val safetyGuard = PackageSafetyGuard(context)
        val appOpEngine = AppOperationEngine(fakeBackend, safetyGuard, testDispatcher)
        val freezeRepo = FreezeGroupRepository(db.freezeGroupDao(), appOpEngine, safetyGuard, testDispatcher)
        autoFreezeManager = AutoFreezeManager(
            context = context,
            repository = freezeRepo,
            operationEngine = appOpEngine,
            safetyGuard = safetyGuard,
            ioDispatcher = testDispatcher
        )

        chargeControlEngine = ChargeControlEngine(fakeBackend, testDispatcher)
        displayRefreshRateController = DisplayRefreshRateController(privilegeManager)

        val batteryEngine = BatteryEngine(context, fakeBackend, testDispatcher)
        val chargingTracker = ChargingSessionTracker(context, db.chargingSessionDao(), batteryEngine, testDispatcher)
        val batteryRepo = BatteryRepository(batteryEngine, db.chargingSessionDao(), chargingTracker, testDispatcher)

        val thermalEngine = ThermalEngine(fakeBackend)
        val thermalRepo = ThermalRepository(thermalEngine, db.thermalThrottleDao(), testDispatcher)

        val foregroundDetector = ForegroundAppDetector(
            privilegeManager = privilegeManager,
            cgroupStrategy = CgroupForegroundStrategy(fakeBackend),
            dumpsysStrategy = DumpsysForegroundStrategy(fakeBackend),
            usageStatsStrategy = UsageStatsForegroundStrategy(context),
            ignoredPackagesFilter = IgnoredPackagesFilter(context),
            ioDispatcher = testDispatcher
        )

        val evaluator = RuleEvaluator()

        rulesEngine = AutomationRulesEngine(
            context = context,
            automationRuleDao = ruleDao,
            ruleEvaluator = evaluator,
            profileRepository = profileRepository,
            profileEngine = profileEngine,
            autoFreezeManager = autoFreezeManager,
            chargeControlEngine = chargeControlEngine,
            displayRefreshRateController = displayRefreshRateController,
            batteryRepository = batteryRepo,
            thermalRepository = thermalRepo,
            foregroundAppDetector = foregroundDetector,
            ioDispatcher = testDispatcher
        ).apply {
            testScopeOverride = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
        }
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        rulesEngine.stop()
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun evaluateState_matchingBatteryRule_triggersProfileAndUpdatesTimestamp() = runTest {
        val ruleEntity = AutomationRuleEntity(
            name = "Battery Saver 20%",
            isEnabled = true,
            priority = 50,
            triggerType = RuleTriggerType.BATTERY_PERCENT_BELOW.name,
            triggerValue = "20",
            actionType = RuleActionType.APPLY_PROFILE.name,
            actionValue = "powersave",
            cooldownSeconds = 30
        )
        val ruleId = ruleDao.insertRule(ruleEntity)

        val prevState = SystemStateSnapshot(batteryPercent = 25)
        val newState = SystemStateSnapshot(batteryPercent = 18)

        rulesEngine.evaluateState(newState)

        val executed = rulesEngine.lastExecutedRules.value
        assertEquals(1, executed.size)
        assertEquals(ruleId, executed.first().id)
        assertEquals("powersave", executed.first().actionValue)

        val updatedRule = ruleDao.getRuleById(ruleId)
        assertNotNull(updatedRule)
        assertTrue(updatedRule!!.lastTriggeredEpochMs > 0L)
    }

    @Test
    fun evaluateState_priorityConflict_highestPriorityWins() = runTest {
        // Low priority rule: Game -> Performance (prio 40)
        ruleDao.insertRule(
            AutomationRuleEntity(
                name = "Game Performance",
                isEnabled = true,
                priority = 40,
                triggerType = RuleTriggerType.FOREGROUND_APP_OPENED.name,
                triggerValue = "com.game.test",
                actionType = RuleActionType.APPLY_PROFILE.name,
                actionValue = "performance"
            )
        )

        // High priority safety rule: Temp >= 45 -> Powersave (prio 90)
        val highId = ruleDao.insertRule(
            AutomationRuleEntity(
                name = "Thermal Safeguard",
                isEnabled = true,
                priority = 90,
                triggerType = RuleTriggerType.TEMPERATURE_ABOVE.name,
                triggerValue = "45",
                actionType = RuleActionType.APPLY_PROFILE.name,
                actionValue = "powersave"
            )
        )

        val newState = SystemStateSnapshot(
            temperatureC = 47f,
            foregroundPackage = "com.game.test"
        )

        rulesEngine.evaluateState(newState)

        val executed = rulesEngine.lastExecutedRules.value
        assertEquals(1, executed.size)
        assertEquals(highId, executed.first().id)
        assertEquals("powersave", executed.first().actionValue)
    }
}
