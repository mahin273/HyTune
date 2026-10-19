package com.mahin.hytune.domain.script

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mahin.hytune.core.privilege.CommandResult
import com.mahin.hytune.core.privilege.FakePrivilegeBackend
import com.mahin.hytune.data.local.db.HyTuneDatabase
import com.mahin.hytune.data.local.db.dao.CustomScriptDao
import com.mahin.hytune.domain.script.engine.ScriptExecutionEngine
import com.mahin.hytune.domain.script.model.CustomScript
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
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
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ScriptRepositoryTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: HyTuneDatabase
    private lateinit var dao: CustomScriptDao
    private lateinit var fakeBackend: FakePrivilegeBackend
    private lateinit var repository: ScriptRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HyTuneDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.customScriptDao()
        fakeBackend = FakePrivilegeBackend()
        val engine = ScriptExecutionEngine(fakeBackend, testDispatcher)
        repository = ScriptRepository(dao, engine, testDispatcher)
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        db.close()
    }

    @Test
    fun saveAndGetScript_worksCorrectly() = runTest(testDispatcher) {
        val script = CustomScript(
            name = "Test",
            scriptContent = "echo 1",
            runAtBoot = false,
            runAsRoot = true
        )
        val id = repository.saveScript(script)
        assertTrue(id > 0)

        val retrieved = repository.getScriptById(id)
        assertNotNull(retrieved)
        assertEquals("Test", retrieved?.name)
        assertEquals("echo 1", retrieved?.scriptContent)

        val all = repository.getAllScripts().first()
        assertEquals(1, all.size)

        repository.setRunAtBoot(id, true)
        val boot = repository.getBootScripts()
        assertEquals(1, boot.size)

        repository.deleteScript(id)
        assertNull(repository.getScriptById(id))
    }

    @Test
    fun executeScript_executesAndUpdateDatabaseHistory() = runTest(testDispatcher) {
        val id = repository.saveScript(
            CustomScript(
                name = "Flush",
                scriptContent = "sync",
                runAtBoot = false,
                runAsRoot = true
            )
        )
        fakeBackend.registerCommandHandler("sync") {
            CommandResult.success(listOf("synced"))
        }

        val result = repository.executeScript(id)
        assertTrue(result.isSuccess)

        val updated = repository.getScriptById(id)
        assertNotNull(updated)
        assertEquals(0, updated?.lastExitCode)
        assertEquals("synced", updated?.lastOutput)
        assertTrue((updated?.lastExecutedEpochMs ?: 0L) > 0L)
    }

    @Test
    fun executeRawScript_executesWithoutSaving() = runTest(testDispatcher) {
        fakeBackend.registerCommandHandler("echo one-liner") {
            CommandResult.success(listOf("one-liner"))
        }

        val result = repository.executeRawScript("echo one-liner")
        assertTrue(result.isSuccess)
        assertEquals("one-liner", result.getOrThrow().output)

        val all = repository.getAllScripts().first()
        assertTrue(all.isEmpty())
    }
}
