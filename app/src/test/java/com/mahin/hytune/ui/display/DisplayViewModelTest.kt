package com.mahin.hytune.ui.display

import com.mahin.hytune.core.privilege.CommandResult
import com.mahin.hytune.core.privilege.FakePrivilegeBackend
import com.mahin.hytune.domain.display.DisplayControlEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DisplayViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeBackend: FakePrivilegeBackend
    private lateinit var engine: DisplayControlEngine
    private lateinit var viewModel: DisplayViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeBackend = FakePrivilegeBackend()

        fakeBackend.registerCommandHandler("wm size") {
            CommandResult.success(listOf("Physical size: 1080x2400"))
        }
        fakeBackend.registerCommandHandler("wm density") {
            CommandResult.success(listOf("Physical density: 420"))
        }
        fakeBackend.registerCommandHandler("settings get system min_refresh_rate") {
            CommandResult.success(listOf("60.0"))
        }
        fakeBackend.registerCommandHandler("settings get system peak_refresh_rate") {
            CommandResult.success(listOf("120.0"))
        }

        engine = DisplayControlEngine(fakeBackend, testDispatcher)
        viewModel = DisplayViewModel(engine)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_loadsDisplaySnapshot() = runTest(testDispatcher) {
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.snapshot)
        assertEquals(1080, state.snapshot?.physicalWidth)
        assertEquals(2400, state.snapshot?.physicalHeight)
        assertEquals(420, state.snapshot?.physicalDpi)
    }

    @Test
    fun applyResolutionWithCountdown_startsCountdownAndApplies() = runTest(testDispatcher) {
        advanceUntilIdle()

        val job = viewModel.applyResolutionWithCountdown(720, 1600, autoScaleDpi = true, timeoutSec = 15)
        job.join()
        runCurrent()

        val executed = fakeBackend.getExecutedCommands()
        assertTrue(executed.contains("wm size 720x1600"))

        val state = viewModel.uiState.value
        assertNotNull(state.countdownRemainingSec)
        assertEquals(15, state.countdownRemainingSec)
    }

    @Test
    fun applyResolutionWithCountdown_confirmChanges_clearsCountdown() = runTest(testDispatcher) {
        advanceUntilIdle()

        val job = viewModel.applyResolutionWithCountdown(720, 1600, autoScaleDpi = true, timeoutSec = 15)
        job.join()
        runCurrent()

        viewModel.confirmChanges()
        runCurrent()

        val state = viewModel.uiState.value
        assertNull(state.countdownRemainingSec)
        assertTrue(state.userMessage?.contains("confirmed") == true)
    }

    @Test
    fun applyResolutionWithCountdown_timeoutExpires_automaticallyReverts() = runTest(testDispatcher) {
        advanceUntilIdle()

        val job = viewModel.applyResolutionWithCountdown(720, 1600, autoScaleDpi = true, timeoutSec = 3)
        job.join()
        runCurrent()

        // Advance 3 seconds of virtual time
        advanceTimeBy(3500L)
        advanceUntilIdle()

        val executed = fakeBackend.getExecutedCommands()
        assertTrue(executed.contains("wm size reset"))
        assertNull(viewModel.uiState.value.countdownRemainingSec)
        assertTrue(viewModel.uiState.value.userMessage?.contains("Reverted") == true)
    }

    @Test
    fun revertChangesNow_immediatelyRestoresBaseline() = runTest(testDispatcher) {
        advanceUntilIdle()

        val job = viewModel.applyResolutionWithCountdown(720, 1600, autoScaleDpi = true, timeoutSec = 15)
        job.join()
        runCurrent()

        viewModel.revertChangesNow().join()
        advanceUntilIdle()

        val executed = fakeBackend.getExecutedCommands()
        assertTrue(executed.contains("wm size reset"))
        assertNull(viewModel.uiState.value.countdownRemainingSec)
    }

    @Test
    fun resetAllDefaults_executesAllResets() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.resetAllDefaults()
        advanceUntilIdle()

        val executed = fakeBackend.getExecutedCommands()
        assertTrue(executed.contains("wm size reset"))
        assertTrue(executed.contains("wm density reset"))
        assertTrue(executed.contains("settings delete system min_refresh_rate"))
        assertTrue(executed.contains("settings delete system peak_refresh_rate"))
    }

    @Test
    fun applyRefreshRate_and_resetRefreshRate() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.applyRefreshRate(90.0f, 90.0f)
        advanceUntilIdle()

        val executed = fakeBackend.getExecutedCommands()
        assertTrue(executed.contains("settings put system min_refresh_rate 90.0"))
        assertTrue(executed.contains("settings put system peak_refresh_rate 90.0"))

        viewModel.resetRefreshRate()
        advanceUntilIdle()

        val executedAfter = fakeBackend.getExecutedCommands()
        assertTrue(executedAfter.contains("settings delete system min_refresh_rate"))
    }
}
