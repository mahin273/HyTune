package com.mahin.hytune.core.privilege

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PrivilegeManagerTest {

    private lateinit var mockRootRunner: TestRootShellRunner
    private lateinit var mockShizukuRunner: TestShizukuShellRunner
    private lateinit var rootBackend: FakePrivilegeBackend
    private lateinit var shizukuBackend: FakePrivilegeBackend
    private lateinit var unprivilegedBackend: FakePrivilegeBackend
    private lateinit var manager: PrivilegeManager

    private class TestRootShellRunner : RootShellRunner {
        var rootAvailable: Boolean = false
        override fun isRootAvailable(): Boolean = rootAvailable
        override suspend fun runCommand(command: String): CommandResult = CommandResult.success()
    }

    private class TestShizukuShellRunner : ShizukuShellRunner {
        var shizukuAvailable: Boolean = false
        override fun isShizukuAvailable(): Boolean = shizukuAvailable
        override suspend fun runCommand(command: String): CommandResult = CommandResult.success()
    }

    @Before
    fun setUp() {
        mockRootRunner = TestRootShellRunner()
        mockShizukuRunner = TestShizukuShellRunner()

        rootBackend = FakePrivilegeBackend(initialTier = PrivilegeTier.ROOT)
        shizukuBackend = FakePrivilegeBackend(initialTier = PrivilegeTier.SHIZUKU)
        unprivilegedBackend = FakePrivilegeBackend(initialTier = PrivilegeTier.NONE)

        manager = PrivilegeManager(
            rootShellRunner = mockRootRunner,
            shizukuShellRunner = mockShizukuRunner,
            rootBackend = rootBackend,
            shizukuBackend = shizukuBackend,
            unprivilegedBackend = unprivilegedBackend
        )
    }

    @Test
    fun neitherAvailable_resolvesUnprivilegedBackend() {
        mockRootRunner.rootAvailable = false
        mockShizukuRunner.shizukuAvailable = false

        manager.refresh()

        assertEquals(PrivilegeTier.NONE, manager.activeBackend.value.tier)
        assertEquals(unprivilegedBackend, manager.activeBackend.value)
    }

    @Test
    fun rootAvailable_takesHighestPrecedence() {
        mockRootRunner.rootAvailable = true
        mockShizukuRunner.shizukuAvailable = true

        manager.refresh()

        assertEquals(PrivilegeTier.ROOT, manager.activeBackend.value.tier)
        assertEquals(rootBackend, manager.activeBackend.value)
    }

    @Test
    fun shizukuAvailable_takesPrecedenceWhenRootAbsent() {
        mockRootRunner.rootAvailable = false
        mockShizukuRunner.shizukuAvailable = true

        manager.refresh()

        assertEquals(PrivilegeTier.SHIZUKU, manager.activeBackend.value.tier)
        assertEquals(shizukuBackend, manager.activeBackend.value)
    }

    @Test
    fun dynamicUpgrade_whenRootBecomesAvailable() {
        mockRootRunner.rootAvailable = false
        mockShizukuRunner.shizukuAvailable = false
        manager.refresh()
        assertEquals(PrivilegeTier.NONE, manager.activeBackend.value.tier)

        mockRootRunner.rootAvailable = true
        manager.refresh()
        assertEquals(PrivilegeTier.ROOT, manager.activeBackend.value.tier)
    }

    @Test
    fun manualOverride_bypassesSystemProbing() {
        mockRootRunner.rootAvailable = true
        manager.refresh()
        assertEquals(PrivilegeTier.ROOT, manager.activeBackend.value.tier)

        val customTestBackend = FakePrivilegeBackend(initialTier = PrivilegeTier.NONE)
        manager.setOverride(customTestBackend)

        assertEquals(customTestBackend, manager.activeBackend.value)

        manager.setOverride(null)
        assertEquals(PrivilegeTier.ROOT, manager.activeBackend.value.tier)
    }
}
