package com.mahin.hytune.domain.notification

import com.mahin.hytune.core.privilege.CommandResult
import com.mahin.hytune.core.privilege.FakePrivilegeBackend
import com.mahin.hytune.core.privilege.PrivilegeBackend
import com.mahin.hytune.core.privilege.PrivilegeManager
import com.mahin.hytune.core.privilege.PrivilegeTier
import com.mahin.hytune.core.privilege.RootShellRunner
import com.mahin.hytune.core.privilege.ShizukuShellRunner
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DndControllerTest {

    private lateinit var fakeBackend: FakePrivilegeBackend
    private lateinit var privilegeManager: PrivilegeManager
    private lateinit var controller: DndController

    @Before
    fun setUp() {
        fakeBackend = FakePrivilegeBackend()
        val rootRunner = object : RootShellRunner {
            override fun isRootAvailable(): Boolean = true
            override suspend fun runCommand(command: String): CommandResult = fakeBackend.exec(command)
        }
        val shizukuRunner = object : ShizukuShellRunner {
            override fun isShizukuAvailable(): Boolean = false
            override suspend fun runCommand(command: String): CommandResult = fakeBackend.exec(command)
        }
        val rootBackend = object : PrivilegeBackend by fakeBackend {
            override val tier = PrivilegeTier.ROOT
        }
        val shizukuBackend = object : PrivilegeBackend by fakeBackend {
            override val tier = PrivilegeTier.SHIZUKU
        }
        val unprivilegedBackend = object : PrivilegeBackend by fakeBackend {
            override val tier = PrivilegeTier.NONE
        }

        privilegeManager = PrivilegeManager(
            rootShellRunner = rootRunner,
            shizukuShellRunner = shizukuRunner,
            rootBackend = rootBackend,
            shizukuBackend = shizukuBackend,
            unprivilegedBackend = unprivilegedBackend
        )
        privilegeManager.refresh()

        controller = DndController(privilegeManager)
    }

    @Test
    fun getCurrentZenMode_returnsStringValue() = runTest {
        fakeBackend.registerCommandHandler("settings get global zen_mode") {
            CommandResult.success(listOf("1"))
        }

        val mode = controller.getCurrentZenMode()
        assertNotNull(mode)
        assertEquals("1", mode)
    }

    @Test
    fun enableDnd_writesZenMode1() = runTest {
        val success = controller.enableDnd()
        assertTrue(success)

        val commands = fakeBackend.getExecutedCommands()
        assertTrue(commands.any { it.contains("settings put global zen_mode 1") })
    }

    @Test
    fun restoreDnd_writesOriginalZenMode() = runTest {
        val success = controller.restoreDnd("0")
        assertTrue(success)

        val commands = fakeBackend.getExecutedCommands()
        assertTrue(commands.any { it.contains("settings put global zen_mode 0") })
    }
}
