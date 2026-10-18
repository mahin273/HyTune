package com.mahin.hytune.domain.process

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mahin.hytune.core.privilege.Capability
import com.mahin.hytune.core.privilege.CommandResult
import com.mahin.hytune.core.privilege.FakePrivilegeBackend
import com.mahin.hytune.core.privilege.PrivilegeTier
import com.mahin.hytune.domain.appmanager.PackageSafetyGuard
import com.mahin.hytune.domain.process.model.ProcessSignal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
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
class ProcessRepositoryTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var fakeBackend: FakePrivilegeBackend
    private lateinit var safetyGuard: PackageSafetyGuard
    private lateinit var engine: ProcessEngine
    private lateinit var repository: ProcessRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        fakeBackend = FakePrivilegeBackend(
            initialTier = PrivilegeTier.ROOT,
            initialCapabilities = Capability.entries.toSet()
        )
        safetyGuard = PackageSafetyGuard(context)
        engine = ProcessEngine(context, fakeBackend, testDispatcher)
        repository = ProcessRepository(engine, safetyGuard, testDispatcher)
    }

    @Test
    fun sendSignal_blocksDestructiveSignalsOnCriticalProcesses() = runTest(testDispatcher) {
        val criticalProcs = listOf("system_server", "zygote", "zygote64", "init", "surfaceflinger")

        for (proc in criticalProcs) {
            val resultKill = repository.sendSignal(100, proc, ProcessSignal.KILL)
            assertFalse(resultKill.isSuccess)
            assertTrue(resultKill.exceptionOrNull() is SecurityException)

            val resultTerm = repository.sendSignal(100, proc, ProcessSignal.TERM)
            assertFalse(resultTerm.isSuccess)

            val resultStop = repository.sendSignal(100, proc, ProcessSignal.STOP)
            assertFalse(resultStop.isSuccess)
        }

        // Verify shell was NOT invoked for blocked calls
        assertTrue(fakeBackend.getExecutedCommands().isEmpty())
    }

    @Test
    fun sendSignal_allowsSignalsOnNonCriticalProcesses() = runTest(testDispatcher) {
        fakeBackend.registerCommandHandler("kill") {
            CommandResult.success()
        }
        val result = repository.sendSignal(5432, "com.social.app", ProcessSignal.KILL)

        assertTrue(result.isSuccess)
        assertTrue(fakeBackend.getExecutedCommands().any { it.contains("kill -9 5432") })
    }

    @Test
    fun setOomScoreAdj_blocksDeprioritizingCriticalProcesses() = runTest(testDispatcher) {
        val result = repository.setOomScoreAdj(500, "system_server", 500)
        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull() is SecurityException)
        assertTrue(fakeBackend.getExecutedCommands().isEmpty())
    }

    @Test
    fun getProcesses_marksCriticalFlagAccurately() = runTest(testDispatcher) {
        val samplePsOutput = listOf(
            "PID PPID USER NI VSZ RSS %CPU COMMAND",
            "1 0 root 0 41088 14420 0.5 /init",
            "1234 543 u0_a123 0 2134567 124560 3.2 com.google.android.youtube"
        )
        fakeBackend.registerCommandHandler("ps") {
            CommandResult.success(samplePsOutput)
        }

        val procs = repository.getProcesses()
        val init = procs.first { it.pid == 1 }
        val youtube = procs.first { it.pid == 1234 }

        assertTrue(init.isCritical)
        assertFalse(youtube.isCritical)
    }
}
