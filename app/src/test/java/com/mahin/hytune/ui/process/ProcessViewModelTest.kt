package com.mahin.hytune.ui.process

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mahin.hytune.core.privilege.Capability
import com.mahin.hytune.core.privilege.CommandResult
import com.mahin.hytune.core.privilege.FakePrivilegeBackend
import com.mahin.hytune.core.privilege.PrivilegeTier
import com.mahin.hytune.domain.appmanager.PackageSafetyGuard
import com.mahin.hytune.domain.process.ProcessEngine
import com.mahin.hytune.domain.process.ProcessRepository
import com.mahin.hytune.domain.process.model.ProcessFilter
import com.mahin.hytune.domain.process.model.ProcessSignal
import com.mahin.hytune.domain.process.model.ProcessSortBy
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
class ProcessViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var fakeBackend: FakePrivilegeBackend
    private lateinit var safetyGuard: PackageSafetyGuard
    private lateinit var engine: ProcessEngine
    private lateinit var repository: ProcessRepository
    private lateinit var viewModel: ProcessViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        fakeBackend = FakePrivilegeBackend(
            initialTier = PrivilegeTier.ROOT,
            initialCapabilities = Capability.entries.toSet()
        )
        val samplePsOutput = listOf(
            "PID PPID USER NI VSZ RSS %CPU COMMAND",
            "1 0 root 0 41088 14420 0.5 /init",
            "543 1 system -10 1250000 85000 15.0 system_server",
            "1000 543 u0_a100 0 500000 50000 25.0 com.heavy.game",
            "1234 543 u0_a123 0 2134567 124560 5.0 com.google.android.youtube",
            "3000 1 u0_a300 10 300000 20000 1.0 com.light.chat"
        )
        fakeBackend.registerCommandHandler("ps") {
            CommandResult.success(samplePsOutput)
        }

        safetyGuard = PackageSafetyGuard(context)
        engine = ProcessEngine(context, fakeBackend, testDispatcher)
        repository = ProcessRepository(engine, safetyGuard, testDispatcher)
        viewModel = ProcessViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun uiState_loadsAndSortsByCpuDescendingByDefault() = runTest(testDispatcher) {
        val state = viewModel.uiState.first { it.processes.isNotEmpty() }
        assertEquals(5, state.processes.size)

        // Default sort is CPU Descending
        assertEquals(1000, state.filteredProcesses[0].pid) // 25.0%
        assertEquals(543, state.filteredProcesses[1].pid)  // 15.0%
        assertEquals(1234, state.filteredProcesses[2].pid) // 5.0%
    }

    @Test
    fun setSearchQuery_filtersProcesses() = runTest(testDispatcher) {
        viewModel.setSearchQuery("youtube")
        val state = viewModel.uiState.first { it.searchQuery == "youtube" }

        assertEquals(1, state.filteredProcesses.size)
        assertEquals("com.google.android.youtube", state.filteredProcesses.first().name)
    }

    @Test
    fun setFilter_filtersUserAppsAndSystem() = runTest(testDispatcher) {
        viewModel.setFilter(ProcessFilter.USER_APPS)
        val stateUser = viewModel.uiState.first { it.filter == ProcessFilter.USER_APPS }
        assertTrue(stateUser.filteredProcesses.all { !it.isCritical && it.packageName != null })

        viewModel.setFilter(ProcessFilter.SYSTEM)
        val stateSystem = viewModel.uiState.first { it.filter == ProcessFilter.SYSTEM }
        assertTrue(stateSystem.filteredProcesses.all { it.isCritical || it.user == "root" || it.user == "system" })
    }

    @Test
    fun setSortBy_changesOrderAndTogglesDirection() = runTest(testDispatcher) {
        // Sort by RAM (descending)
        viewModel.setSortBy(ProcessSortBy.RAM)
        var state = viewModel.uiState.first { it.sortBy == ProcessSortBy.RAM }
        assertEquals(1234, state.filteredProcesses.first().pid) // Highest RSS: 124560

        // Toggle direction (RAM ascending)
        viewModel.setSortBy(ProcessSortBy.RAM)
        state = viewModel.uiState.first { it.sortAscending }
        assertEquals(1, state.filteredProcesses.first().pid) // Lowest RSS: 14420
    }

    @Test
    fun sendSignal_killsProcessAndUpdatesStatus() = runTest(testDispatcher) {
        val state = viewModel.uiState.first { it.processes.isNotEmpty() }
        val target = state.processes.first { it.pid == 1000 }

        fakeBackend.registerCommandHandler("kill") {
            CommandResult.success()
        }
        viewModel.sendSignal(target, ProcessSignal.KILL)

        val updatedState = viewModel.uiState.first { it.statusMessage != null }
        assertNotNull(updatedState.statusMessage)
        assertTrue(updatedState.statusMessage!!.contains("SIGKILL"))
        assertTrue(fakeBackend.getExecutedCommands().any { it.contains("kill -9 1000") })
    }
}
