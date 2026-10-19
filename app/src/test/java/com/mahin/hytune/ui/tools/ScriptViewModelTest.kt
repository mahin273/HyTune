package com.mahin.hytune.ui.tools

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mahin.hytune.core.privilege.CommandResult
import com.mahin.hytune.core.privilege.FakePrivilegeBackend
import com.mahin.hytune.data.local.db.HyTuneDatabase
import com.mahin.hytune.domain.script.ScriptRepository
import com.mahin.hytune.domain.script.engine.ScriptExecutionEngine
import com.mahin.hytune.domain.script.model.CustomScript
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
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
import java.io.IOException

import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ScriptViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var db: HyTuneDatabase
    private lateinit var fakeBackend: FakePrivilegeBackend
    private lateinit var repository: ScriptRepository
    private lateinit var viewModel: ScriptViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directExecutor = java.util.concurrent.Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(context, HyTuneDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
        fakeBackend = FakePrivilegeBackend()
        val engine = ScriptExecutionEngine(fakeBackend, testDispatcher)
        repository = ScriptRepository(db.customScriptDao(), engine, testDispatcher)
        viewModel = ScriptViewModel(repository)
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun saveAndExecuteScript_updatesUiState() = runTest(testDispatcher) {
        val script = CustomScript(
            name = "Test Script",
            scriptContent = "echo test",
            runAtBoot = false,
            runAsRoot = true
        )
        viewModel.saveScript(script)
        testScheduler.advanceUntilIdle()

        val allScripts = repository.getAllScripts().first()
        assertEquals(1, allScripts.size)
        val savedId = allScripts.first().id

        fakeBackend.registerCommandHandler("echo test") {
            CommandResult.success(listOf("test output"))
        }
        viewModel.executeScript(savedId)
        testScheduler.advanceUntilIdle()

        val stateAfterExec = viewModel.uiState.value
        assertNotNull(stateAfterExec.lastExecutionResult)
        assertEquals(0, stateAfterExec.lastExecutionResult?.exitCode)
        assertEquals("test output", stateAfterExec.lastExecutionResult?.output)

        viewModel.clearExecutionResult()
        testScheduler.advanceUntilIdle()
        assertEquals(null, viewModel.uiState.value.lastExecutionResult)

        viewModel.deleteScript(savedId)
        testScheduler.advanceUntilIdle()
        val scriptsAfterDelete = repository.getAllScripts().first()
        assertTrue(scriptsAfterDelete.isEmpty())
    }
}
