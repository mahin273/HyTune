package com.mahin.hytune.ui.appmanager

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mahin.hytune.core.privilege.Capability
import com.mahin.hytune.core.privilege.FakePrivilegeBackend
import com.mahin.hytune.core.privilege.PrivilegeTier
import com.mahin.hytune.data.local.db.HyTuneDatabase
import com.mahin.hytune.domain.appmanager.AppOperationEngine
import com.mahin.hytune.domain.appmanager.FreezeGroupRepository
import com.mahin.hytune.domain.appmanager.FreezeShortcutManager
import com.mahin.hytune.domain.appmanager.PackageSafetyGuard
import com.mahin.hytune.domain.appmanager.model.FreezeGroup
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class FreezeGroupViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var db: HyTuneDatabase
    private lateinit var fakeBackend: FakePrivilegeBackend
    private lateinit var safetyGuard: PackageSafetyGuard
    private lateinit var operationEngine: AppOperationEngine
    private lateinit var repository: FreezeGroupRepository
    private lateinit var shortcutManager: FreezeShortcutManager
    private lateinit var viewModel: FreezeGroupViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, HyTuneDatabase::class.java)
            .allowMainThreadQueries()
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
        shortcutManager = FreezeShortcutManager(context)

        viewModel = FreezeGroupViewModel(
            repository = repository,
            shortcutManager = shortcutManager,
            ioDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun saveGroup_insertsGroupAndSetsStatusMessage() = runTest(testDispatcher) {
        viewModel.saveGroup(
            name = "Work Apps",
            description = "Slack and Teams",
            autoFreeze = false,
            delaySeconds = 30,
            packages = listOf("com.slack", "com.teams")
        )

        val status = viewModel.statusMessage.first { it != null }
        assertNotNull(status)
        assertTrue(status!!.contains("Work Apps"))

        val groups = viewModel.groups.first { it.any { g -> g.name == "Work Apps" && g.packages.size == 2 } }
        assertEquals(1, groups.size)
        assertEquals("Work Apps", groups[0].name)
        assertEquals(2, groups[0].packages.size)
    }

    @Test
    fun deleteGroup_removesGroupAndUpdatesStatus() = runTest(testDispatcher) {
        val id = repository.saveGroup(FreezeGroup(name = "Temp"))
        val initialGroups = viewModel.groups.first { it.isNotEmpty() }
        assertEquals(1, initialGroups.size)

        viewModel.deleteGroup(id)
        val status = viewModel.statusMessage.first { it == "Group deleted" }
        assertEquals("Group deleted", status)

        val afterGroups = viewModel.groups.first { it.isEmpty() }
        assertEquals(0, afterGroups.size)
    }

    @Test
    fun freezeGroup_executesBatchAndUpdatesStatus() = runTest(testDispatcher) {
        val id = repository.saveGroup(
            FreezeGroup(
                name = "Social",
                packages = listOf("com.social.app")
            )
        )

        viewModel.freezeGroup(id)
        val status = viewModel.statusMessage.first { it != null }
        assertNotNull(status)
        assertTrue(status!!.contains("Frozen 1/1 apps"))
        assertTrue(fakeBackend.getExecutedCommands().contains("pm disable-user --user 0 com.social.app"))
    }

    @Test
    fun unfreezeGroup_executesBatchAndUpdatesStatus() = runTest(testDispatcher) {
        val id = repository.saveGroup(
            FreezeGroup(
                name = "Games",
                packages = listOf("com.game.app")
            )
        )

        viewModel.unfreezeGroup(id)
        val status = viewModel.statusMessage.first { it != null }
        assertNotNull(status)
        assertTrue(status!!.contains("Unfrozen 1/1 apps"))
        assertTrue(fakeBackend.getExecutedCommands().contains("pm enable com.game.app"))
    }
}
