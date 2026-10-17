package com.mahin.hytune.ui.storage

import com.mahin.hytune.core.privilege.CommandResult
import com.mahin.hytune.core.privilege.FakePrivilegeBackend
import com.mahin.hytune.domain.storage.StorageControlEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StorageViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeBackend: FakePrivilegeBackend
    private lateinit var engine: StorageControlEngine
    private lateinit var viewModel: StorageViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        fakeBackend = FakePrivilegeBackend()
        fakeBackend.setFile("/sys/block/sda/queue/scheduler", "none [mq-deadline] kyber\n")
        fakeBackend.setFile("/sys/block/sda/queue/read_ahead_kb", "128\n")
        fakeBackend.setFile("/sys/block/sda/queue/rotational", "0\n")
        fakeBackend.setFile("/sys/block/sda/size", "250069680\n")

        engine = StorageControlEngine(fakeBackend, testDispatcher)
        viewModel = StorageViewModel(engine)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialLoad_populatesBlockDevicesAndStagedValues() = runTest {
        val state = viewModel.uiState.value

        assertEquals(1, state.blockDevices.size)
        val sda = state.blockDevices.first()
        assertEquals("sda", sda.name)
        assertEquals("mq-deadline", sda.currentScheduler)
        assertEquals(128, sda.readAheadKb)

        assertEquals("mq-deadline", state.stagedSchedulers["sda"])
        assertEquals(128, state.stagedReadAhead["sda"])
        assertFalse(state.isDirty())
    }

    @Test
    fun onSchedulerChanged_updatesStagedAndSetsIsDirty() = runTest {
        viewModel.onSchedulerChanged("sda", "none")

        val state = viewModel.uiState.value
        assertEquals("none", state.stagedSchedulers["sda"])
        assertTrue(state.isDirty("sda"))
        assertTrue(state.isDirty())
    }

    @Test
    fun onReadAheadChanged_updatesStagedAndSetsIsDirty() = runTest {
        viewModel.onReadAheadChanged("sda", 512)

        val state = viewModel.uiState.value
        assertEquals(512, state.stagedReadAhead["sda"])
        assertTrue(state.isDirty("sda"))
        assertTrue(state.isDirty())
    }

    @Test
    fun applyStorageSettings_writesToEngineAndResetsDirty() = runTest {
        viewModel.onSchedulerChanged("sda", "none")
        viewModel.onReadAheadChanged("sda", 512)
        assertTrue(viewModel.uiState.value.isDirty())

        viewModel.applyStorageSettings("sda")

        val state = viewModel.uiState.value
        assertFalse(state.isDirty())
        assertEquals("Storage settings for sda applied successfully", state.userMessage)
        assertNull(state.errorMessage)

        assertEquals("none", fakeBackend.getFile("/sys/block/sda/queue/scheduler"))
        assertEquals("512", fakeBackend.getFile("/sys/block/sda/queue/read_ahead_kb"))
    }

    @Test
    fun resetStorageSettings_revertsStagedValuesToLive() = runTest {
        viewModel.onSchedulerChanged("sda", "none")
        viewModel.onReadAheadChanged("sda", 1024)
        assertTrue(viewModel.uiState.value.isDirty())

        viewModel.resetStorageSettings("sda")

        val state = viewModel.uiState.value
        assertFalse(state.isDirty())
        assertEquals("mq-deadline", state.stagedSchedulers["sda"])
        assertEquals(128, state.stagedReadAhead["sda"])
    }

    @Test
    fun runTrim_invokesEngineAndSetsTrimResult() = runTest {
        fakeBackend.registerCommandHandler("fstrim") {
            CommandResult.success(listOf("/data: 2147483648 bytes trimmed"))
        }

        viewModel.runTrim("/data")

        val state = viewModel.uiState.value
        assertNotNull(state.fstrimResult)
        assertEquals(2147483648L, state.fstrimResult?.trimmedBytes)
        assertTrue(state.userMessage?.contains("2.00 GB trimmed") == true)
        assertNull(state.errorMessage)
    }
}
