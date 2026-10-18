package com.mahin.hytune.domain.appmanager

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mahin.hytune.core.privilege.Capability
import com.mahin.hytune.core.privilege.FakePrivilegeBackend
import com.mahin.hytune.core.privilege.PrivilegeTier
import com.mahin.hytune.data.local.db.HyTuneDatabase
import com.mahin.hytune.data.local.db.dao.FreezeGroupDao
import com.mahin.hytune.domain.appmanager.model.AppOperation
import com.mahin.hytune.domain.appmanager.model.FreezeGroup
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class FreezeGroupRepositoryTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var db: HyTuneDatabase
    private lateinit var dao: FreezeGroupDao
    private lateinit var fakeBackend: FakePrivilegeBackend
    private lateinit var safetyGuard: PackageSafetyGuard
    private lateinit var operationEngine: AppOperationEngine
    private lateinit var repository: FreezeGroupRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, HyTuneDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.freezeGroupDao()

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
            dao = dao,
            operationEngine = operationEngine,
            safetyGuard = safetyGuard,
            ioDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun saveAndGetGroup() = runTest(testDispatcher) {
        val group = FreezeGroup(
            name = "Bloatware",
            description = "Carrier bloat",
            autoFreezeOnScreenOff = true,
            autoFreezeDelaySeconds = 20,
            packages = listOf("com.carrier.app1", "com.carrier.app2")
        )

        val id = repository.saveGroup(group)
        val fetched = repository.getGroupById(id).first()
        assertNotNull(fetched)
        assertEquals("Bloatware", fetched!!.name)
        assertEquals(2, fetched.packages.size)
        assertTrue(fetched.packages.contains("com.carrier.app1"))
    }

    @Test
    fun freezeGroup_executesFreezeOnAllNonCriticalPackages() = runTest(testDispatcher) {
        val group = FreezeGroup(
            name = "Mixed Group",
            packages = listOf("com.normal.app", "com.android.systemui")
        )
        val id = repository.saveGroup(group)

        val result = repository.freezeGroup(id)
        assertTrue(result.isSuccess)
        val opResult = result.getOrThrow()
        assertEquals(2, opResult.totalPackages)
        assertEquals(1, opResult.successCount)
        assertEquals(1, opResult.failureCount)
        assertTrue(opResult.failures.containsKey("com.android.systemui"))
        assertTrue(fakeBackend.getExecutedCommands().contains("pm disable-user --user 0 com.normal.app"))
    }

    @Test
    fun unfreezeGroup_executesUnfreezeOnAllPackages() = runTest(testDispatcher) {
        val group = FreezeGroup(
            name = "Games",
            packages = listOf("com.game.one", "com.game.two")
        )
        val id = repository.saveGroup(group)

        val result = repository.unfreezeGroup(id)
        assertTrue(result.isSuccess)
        val opResult = result.getOrThrow()
        assertEquals(2, opResult.successCount)
        assertEquals(0, opResult.failureCount)
        assertTrue(fakeBackend.getExecutedCommands().contains("pm enable com.game.one"))
        assertTrue(fakeBackend.getExecutedCommands().contains("pm enable com.game.two"))
    }

    @Test
    fun deleteGroup_removesGroupFromRepository() = runTest(testDispatcher) {
        val group = FreezeGroup(name = "Temp Group")
        val id = repository.saveGroup(group)
        assertNotNull(repository.getGroupById(id).first())

        repository.deleteGroup(id)
        assertNull(repository.getGroupById(id).first())
    }
}
