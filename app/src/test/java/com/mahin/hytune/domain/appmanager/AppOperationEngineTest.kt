package com.mahin.hytune.domain.appmanager

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mahin.hytune.core.privilege.Capability
import com.mahin.hytune.core.privilege.CommandResult
import com.mahin.hytune.core.privilege.FakePrivilegeBackend
import com.mahin.hytune.core.privilege.PrivilegeTier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
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
class AppOperationEngineTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var fakeBackend: FakePrivilegeBackend
    private lateinit var safetyGuard: PackageSafetyGuard
    private lateinit var engine: AppOperationEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        fakeBackend = FakePrivilegeBackend(
            initialTier = PrivilegeTier.ROOT,
            initialCapabilities = Capability.entries.toSet()
        )
        safetyGuard = PackageSafetyGuard(context)
        engine = AppOperationEngine(
            privilegeBackend = fakeBackend,
            safetyGuard = safetyGuard,
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun freeze_executesCorrectPmCommand() = runTest {
        val result = engine.freeze("com.bloatware.app")
        assertTrue(result.isSuccess)
        val opResult = result.getOrThrow()
        assertTrue(opResult.success)
        assertEquals("pm disable-user --user 0 com.bloatware.app", opResult.commandExecuted)
        assertTrue(fakeBackend.getExecutedCommands().contains("pm disable-user --user 0 com.bloatware.app"))
    }

    @Test
    fun unfreeze_executesCorrectPmCommand() = runTest {
        val result = engine.unfreeze("com.bloatware.app")
        assertTrue(result.isSuccess)
        val opResult = result.getOrThrow()
        assertTrue(opResult.success)
        assertEquals("pm enable com.bloatware.app", opResult.commandExecuted)
        assertTrue(fakeBackend.getExecutedCommands().contains("pm enable com.bloatware.app"))
    }

    @Test
    fun suspend_executesCorrectPmCommand() = runTest {
        val result = engine.suspend("com.social.media")
        assertTrue(result.isSuccess)
        val opResult = result.getOrThrow()
        assertTrue(opResult.success)
        assertEquals("pm suspend --user 0 com.social.media", opResult.commandExecuted)
        assertTrue(fakeBackend.getExecutedCommands().contains("pm suspend --user 0 com.social.media"))
    }

    @Test
    fun unsuspend_executesCorrectPmCommand() = runTest {
        val result = engine.unsuspend("com.social.media")
        assertTrue(result.isSuccess)
        val opResult = result.getOrThrow()
        assertTrue(opResult.success)
        assertEquals("pm unsuspend --user 0 com.social.media", opResult.commandExecuted)
        assertTrue(fakeBackend.getExecutedCommands().contains("pm unsuspend --user 0 com.social.media"))
    }

    @Test
    fun hide_executesCorrectPmCommand() = runTest {
        val result = engine.hide("com.secret.vault")
        assertTrue(result.isSuccess)
        val opResult = result.getOrThrow()
        assertTrue(opResult.success)
        assertEquals("pm hide --user 0 com.secret.vault", opResult.commandExecuted)
    }

    @Test
    fun unhide_executesCorrectPmCommand() = runTest {
        val result = engine.unhide("com.secret.vault")
        assertTrue(result.isSuccess)
        val opResult = result.getOrThrow()
        assertTrue(opResult.success)
        assertEquals("pm unhide --user 0 com.secret.vault", opResult.commandExecuted)
    }

    @Test
    fun forceStop_executesCorrectAmCommand() = runTest {
        val result = engine.forceStop("com.heavy.game")
        assertTrue(result.isSuccess)
        val opResult = result.getOrThrow()
        assertTrue(opResult.success)
        assertEquals("am force-stop com.heavy.game", opResult.commandExecuted)
    }

    @Test
    fun clearData_executesCorrectPmCommand() = runTest {
        val result = engine.clearData("com.test.app")
        assertTrue(result.isSuccess)
        val opResult = result.getOrThrow()
        assertTrue(opResult.success)
        assertEquals("pm clear --user 0 com.test.app", opResult.commandExecuted)
    }

    @Test
    fun uninstallUser0_withoutKeepData_executesCorrectPmCommand() = runTest {
        val result = engine.uninstallUser0("com.oem.carrier", keepData = false)
        assertTrue(result.isSuccess)
        val opResult = result.getOrThrow()
        assertTrue(opResult.success)
        assertEquals("pm uninstall --user 0 com.oem.carrier", opResult.commandExecuted)
    }

    @Test
    fun uninstallUser0_withKeepData_executesCorrectPmCommand() = runTest {
        val result = engine.uninstallUser0("com.oem.carrier", keepData = true)
        assertTrue(result.isSuccess)
        val opResult = result.getOrThrow()
        assertTrue(opResult.success)
        assertEquals("pm uninstall -k --user 0 com.oem.carrier", opResult.commandExecuted)
    }

    @Test
    fun reinstallExisting_executesCorrectCmdPackageCommand() = runTest {
        val result = engine.reinstallExisting("com.oem.carrier")
        assertTrue(result.isSuccess)
        val opResult = result.getOrThrow()
        assertTrue(opResult.success)
        assertEquals("cmd package install-existing com.oem.carrier", opResult.commandExecuted)
    }

    @Test
    fun criticalPackage_destructiveOperationFailsWithSecurityException() = runTest {
        val result = engine.freeze("com.android.systemui")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
        assertTrue(fakeBackend.getExecutedCommands().isEmpty())
    }

    @Test
    fun commandFailure_returnsOperationResultWithFailure() = runTest {
        fakeBackend.registerCommandHandler("pm clear") {
            CommandResult(exitCode = 1, stderr = listOf("Error: package not found"))
        }

        val result = engine.clearData("com.nonexistent.app")
        assertTrue(result.isSuccess)
        val opResult = result.getOrThrow()
        assertFalse(opResult.success)
        assertTrue(opResult.message!!.contains("package not found"))
    }

    @Test
    fun stdoutWithDeleteFailedKeyword_returnsOperationResultWithFailure() = runTest {
        fakeBackend.registerCommandHandler("pm uninstall") {
            CommandResult(exitCode = 0, stdout = listOf("Failure [DELETE_FAILED_INTERNAL_ERROR]"))
        }

        val result = engine.uninstallUser0("com.oem.system", keepData = false)
        assertTrue(result.isSuccess)
        val opResult = result.getOrThrow()
        assertFalse(opResult.success)
        assertTrue(opResult.message!!.contains("DELETE_FAILED"))
    }

    @Test
    fun backendMissingFreezeCapability_throwsSecurityException() = runTest {
        fakeBackend.setCapabilities(Capability.SYSFS_READ)
        val result = engine.freeze("com.some.app")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
    }
}
