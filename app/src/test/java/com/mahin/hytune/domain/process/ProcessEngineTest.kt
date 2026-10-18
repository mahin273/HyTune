package com.mahin.hytune.domain.process

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mahin.hytune.core.privilege.Capability
import com.mahin.hytune.core.privilege.CommandResult
import com.mahin.hytune.core.privilege.FakePrivilegeBackend
import com.mahin.hytune.core.privilege.PrivilegeTier
import com.mahin.hytune.domain.process.model.ProcessSignal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
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
class ProcessEngineTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var fakeBackend: FakePrivilegeBackend
    private lateinit var engine: ProcessEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        fakeBackend = FakePrivilegeBackend(
            initialTier = PrivilegeTier.ROOT,
            initialCapabilities = Capability.entries.toSet()
        )
        engine = ProcessEngine(
            context = context,
            privilegeBackend = fakeBackend,
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun getProcesses_parsesPsOutputWithCpuColumn() = runTest(testDispatcher) {
        val samplePsOutput = listOf(
            "PID PPID USER NI VSZ RSS %CPU COMMAND",
            "1 0 root 0 41088 14420 0.5 /init",
            "543 1 system -10 1250000 85000 12.4 system_server",
            "1234 543 u0_a123 0 2134567 124560 3.2 com.google.android.youtube",
            "2001 543 u0_a200 10 980000 45000 0.0 com.example.game:isolated"
        )
        fakeBackend.registerCommandHandler("ps") {
            CommandResult.success(samplePsOutput)
        }

        val processes = engine.getProcesses()

        assertEquals(4, processes.size)

        val initProc = processes.first { it.pid == 1 }
        assertEquals(1, initProc.pid)
        assertEquals(0, initProc.ppid)
        assertEquals("root", initProc.user)
        assertEquals(0, initProc.nice)
        assertEquals(14420L, initProc.rssKb)
        assertEquals(0.5f, initProc.cpuUsagePercent)
        assertEquals("init", initProc.name)

        val youtubeProc = processes.first { it.pid == 1234 }
        assertEquals("com.google.android.youtube", youtubeProc.name)
        assertEquals("com.google.android.youtube", youtubeProc.packageName)
        assertEquals("u0_a123", youtubeProc.user)
        assertEquals(3.2f, youtubeProc.cpuUsagePercent)
        assertEquals(124560L, youtubeProc.rssKb)

        val gameProc = processes.first { it.pid == 2001 }
        assertEquals("com.example.game", gameProc.packageName)
        assertEquals(10, gameProc.nice)
    }

    @Test
    fun sendSignal_executesCorrectKillCommand() = runTest(testDispatcher) {
        fakeBackend.registerCommandHandler("kill") {
            CommandResult.success()
        }
        val result = engine.sendSignal(1234, ProcessSignal.KILL)

        assertTrue(result.isSuccess)
        assertTrue(fakeBackend.getExecutedCommands().any { it.contains("kill -9 1234") })
    }

    @Test
    fun setNice_clampsValueAndExecutesRenice() = runTest(testDispatcher) {
        fakeBackend.registerCommandHandler("renice") {
            CommandResult.success()
        }
        val result = engine.setNice(1234, 15)

        assertTrue(result.isSuccess)
        assertTrue(fakeBackend.getExecutedCommands().any { it.contains("renice -n 15 -p 1234") })

        // Test clamping out of bounds
        engine.setNice(1234, 50)
        assertTrue(fakeBackend.getExecutedCommands().any { it.contains("renice -n 19 -p 1234") })
    }

    @Test
    fun setOomScoreAdj_clampsAndWritesSysfsOrShell() = runTest(testDispatcher) {
        fakeBackend.registerCommandHandler("echo") {
            CommandResult.success()
        }
        val result = engine.setOomScoreAdj(1234, 500)

        assertTrue(result.isSuccess)
    }

    @Test
    fun getOomScoreAdj_readsValueSuccessfully() = runTest(testDispatcher) {
        fakeBackend.setFile("/proc/1234/oom_score_adj", "200")
        val score = engine.getOomScoreAdj(1234)

        assertEquals(200, score)
    }
}
