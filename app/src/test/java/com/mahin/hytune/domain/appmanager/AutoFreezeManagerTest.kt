package com.mahin.hytune.domain.appmanager

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mahin.hytune.core.privilege.Capability
import com.mahin.hytune.core.privilege.FakePrivilegeBackend
import com.mahin.hytune.core.privilege.PrivilegeTier
import com.mahin.hytune.data.local.db.HyTuneDatabase
import com.mahin.hytune.domain.appmanager.model.FreezeGroup
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class AutoFreezeManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var context: Context
    private lateinit var db: HyTuneDatabase
    private lateinit var fakeBackend: FakePrivilegeBackend
    private lateinit var safetyGuard: PackageSafetyGuard
    private lateinit var operationEngine: AppOperationEngine
    private lateinit var repository: FreezeGroupRepository
    private lateinit var autoFreezeManager: AutoFreezeManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val directExecutor = java.util.concurrent.Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(context, HyTuneDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()

        fakeBackend = FakePrivilegeBackend(
            initialTier = PrivilegeTier.ROOT,
            initialCapabilities = Capability.entries.toSet()
        )
        safetyGuard = PackageSafetyGuard(context)
        operationEngine = AppOperationEngine(
            privilegeBackend = fakeBackend,
            safetyGuard = safetyGuard,
            ioDispatcher = testDispatcher
        )
        repository = FreezeGroupRepository(
            dao = db.freezeGroupDao(),
            operationEngine = operationEngine,
            safetyGuard = safetyGuard,
            ioDispatcher = testDispatcher
        )

        autoFreezeManager = AutoFreezeManager(
            context = context,
            repository = repository,
            operationEngine = operationEngine,
            safetyGuard = safetyGuard,
            ioDispatcher = testDispatcher
        ).apply {
            testScopeOverride = testScope
        }
    }

    @After
    fun tearDown() {
        autoFreezeManager.stopMonitoring()
        autoFreezeManager.testScopeOverride = null
        db.close()
    }

    @Test
    fun handleScreenOff_withAutoFreezeGroups_freezesPackagesAfterDelay() = testScope.runTest {
        repository.saveGroup(
            FreezeGroup(
                name = "Auto Social",
                autoFreezeOnScreenOff = true,
                autoFreezeDelaySeconds = 10,
                packages = listOf("com.social.app")
            )
        )

        autoFreezeManager.handleScreenOff(overrideDelaySeconds = 10)
        testScheduler.advanceTimeBy(100) // Before delay expires
        assertTrue(fakeBackend.getExecutedCommands().isEmpty())

        testScheduler.advanceTimeBy(15000) // Advance past 10s delay
        testScheduler.advanceUntilIdle()

        assertTrue(fakeBackend.getExecutedCommands().contains("pm disable-user --user 0 com.social.app"))
        assertFalse(autoFreezeManager.isAutoFreezePending.value)
    }

    @Test
    fun handleScreenOn_cancelsPendingDebounceJob() = testScope.runTest {
        repository.saveGroup(
            FreezeGroup(
                name = "Auto Group",
                autoFreezeOnScreenOff = true,
                autoFreezeDelaySeconds = 15,
                packages = listOf("com.test.app")
            )
        )

        autoFreezeManager.handleScreenOff(overrideDelaySeconds = 15)
        testScheduler.advanceTimeBy(5000) // Screen turned off for 5s

        // User turns screen back ON
        autoFreezeManager.handleScreenOn()

        testScheduler.advanceTimeBy(20000) // Advance past original deadline
        testScheduler.advanceUntilIdle()
        // Verify no command was executed
        assertTrue(fakeBackend.getExecutedCommands().isEmpty())
        assertFalse(autoFreezeManager.isAutoFreezePending.value)
    }

    @Test
    fun handleScreenOff_withNoAutoGroups_doesNothing() = testScope.runTest {
        repository.saveGroup(
            FreezeGroup(
                name = "Manual Group",
                autoFreezeOnScreenOff = false,
                packages = listOf("com.manual.app")
            )
        )

        autoFreezeManager.handleScreenOff()
        testScheduler.advanceTimeBy(35000)
        testScheduler.advanceUntilIdle()
        assertTrue(fakeBackend.getExecutedCommands().isEmpty())
    }
}
