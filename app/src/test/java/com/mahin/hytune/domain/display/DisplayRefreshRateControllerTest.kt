package com.mahin.hytune.domain.display

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

class DisplayRefreshRateControllerTest {

    private lateinit var fakeBackend: FakePrivilegeBackend
    private lateinit var privilegeManager: PrivilegeManager
    private lateinit var controller: DisplayRefreshRateController

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

        controller = DisplayRefreshRateController(privilegeManager)
    }

    @Test
    fun getCurrentPeakRefreshRate_parsesFloat() = runTest {
        fakeBackend.registerCommandHandler("settings get system peak_refresh_rate") {
            CommandResult.success(listOf("120.0"))
        }

        val rate = controller.getCurrentPeakRefreshRate()
        assertNotNull(rate)
        assertEquals(120.0f, rate!!, 0.01f)
    }

    @Test
    fun setRefreshRate_writesPeakAndMin() = runTest {
        val success = controller.setRefreshRate(90)
        assertTrue(success)

        val commands = fakeBackend.getExecutedCommands()
        assertTrue(commands.any { it.contains("settings put system peak_refresh_rate 90.0") })
        assertTrue(commands.any { it.contains("settings put system min_refresh_rate 90.0") })
    }

    @Test
    fun restoreRefreshRate_writesOriginalValues() = runTest {
        val success = controller.restoreRefreshRate(60.0f, 60.0f)
        assertTrue(success)

        val commands = fakeBackend.getExecutedCommands()
        assertTrue(commands.any { it.contains("settings put system peak_refresh_rate 60.0") })
        assertTrue(commands.any { it.contains("settings put system min_refresh_rate 60.0") })
    }
}
