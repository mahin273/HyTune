package com.mahin.hytune.domain.display

import com.mahin.hytune.core.privilege.CommandResult
import com.mahin.hytune.core.privilege.FakePrivilegeBackend
import com.mahin.hytune.domain.display.model.DisplayStateSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DisplayControlEngineTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeBackend: FakePrivilegeBackend
    private lateinit var engine: DisplayControlEngine

    @Before
    fun setUp() {
        fakeBackend = FakePrivilegeBackend()
        engine = DisplayControlEngine(fakeBackend, testDispatcher)
    }

    @Test
    fun getDisplayState_parsesPhysicalAndOverrideSizes() = runTest(testDispatcher) {
        fakeBackend.registerCommandHandler("wm size") {
            CommandResult.success(listOf("Physical size: 1080x2400", "Override size: 720x1600"))
        }
        fakeBackend.registerCommandHandler("wm density") {
            CommandResult.success(listOf("Physical density: 420", "Override density: 360"))
        }
        fakeBackend.registerCommandHandler("settings get system min_refresh_rate") {
            CommandResult.success(listOf("60.0"))
        }
        fakeBackend.registerCommandHandler("settings get system peak_refresh_rate") {
            CommandResult.success(listOf("120.0"))
        }

        val result = engine.getDisplayState()
        assertTrue(result.isSuccess)

        val snapshot = result.getOrThrow()
        assertEquals(1080, snapshot.physicalWidth)
        assertEquals(2400, snapshot.physicalHeight)
        assertEquals(720, snapshot.overrideWidth)
        assertEquals(1600, snapshot.overrideHeight)
        assertEquals(720, snapshot.activeWidth)
        assertEquals(1600, snapshot.activeHeight)
        assertEquals(420, snapshot.physicalDpi)
        assertEquals(360, snapshot.overrideDpi)
        assertEquals(360, snapshot.activeDpi)
        assertEquals(60.0f, snapshot.minRefreshRateHz)
        assertEquals(120.0f, snapshot.peakRefreshRateHz)
        assertTrue(snapshot.isOverridden)
    }

    @Test
    fun getDisplayState_withoutOverrides_reflectsPhysicalDimensions() = runTest(testDispatcher) {
        fakeBackend.registerCommandHandler("wm size") {
            CommandResult.success(listOf("Physical size: 1440x3200"))
        }
        fakeBackend.registerCommandHandler("wm density") {
            CommandResult.success(listOf("Physical density: 560"))
        }
        fakeBackend.registerCommandHandler("settings get system min_refresh_rate") {
            CommandResult.success(listOf("null"))
        }
        fakeBackend.registerCommandHandler("settings get system peak_refresh_rate") {
            CommandResult.success(listOf("null"))
        }

        val result = engine.getDisplayState()
        assertTrue(result.isSuccess)

        val snapshot = result.getOrThrow()
        assertEquals(1440, snapshot.physicalWidth)
        assertEquals(3200, snapshot.physicalHeight)
        assertNull(snapshot.overrideWidth)
        assertNull(snapshot.overrideHeight)
        assertEquals(1440, snapshot.activeWidth)
        assertEquals(3200, snapshot.activeHeight)
        assertEquals(560, snapshot.activeDpi)
        assertFalse(snapshot.isOverridden)
    }

    @Test
    fun setResolution_validDimensions_executesCommand() = runTest(testDispatcher) {
        val result = engine.setResolution(720, 1600)
        assertTrue(result.isSuccess)

        val executed = fakeBackend.getExecutedCommands()
        assertTrue(executed.contains("wm size 720x1600"))
    }

    @Test
    fun setResolution_invalidDimensions_fails() = runTest(testDispatcher) {
        val result = engine.setResolution(100, 200)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun resetResolution_executesWmSizeReset() = runTest(testDispatcher) {
        val result = engine.resetResolution()
        assertTrue(result.isSuccess)

        val executed = fakeBackend.getExecutedCommands()
        assertTrue(executed.contains("wm size reset"))
    }

    @Test
    fun setDensity_validDpi_executesCommand() = runTest(testDispatcher) {
        val result = engine.setDensity(380)
        assertTrue(result.isSuccess)

        val executed = fakeBackend.getExecutedCommands()
        assertTrue(executed.contains("wm density 380"))
    }

    @Test
    fun resetDensity_executesWmDensityReset() = runTest(testDispatcher) {
        val result = engine.resetDensity()
        assertTrue(result.isSuccess)

        val executed = fakeBackend.getExecutedCommands()
        assertTrue(executed.contains("wm density reset"))
    }

    @Test
    fun setRefreshRate_validHz_executesCommands() = runTest(testDispatcher) {
        val result = engine.setRefreshRate(60.0f, 120.0f)
        assertTrue(result.isSuccess)

        val executed = fakeBackend.getExecutedCommands()
        assertTrue(executed.contains("settings put system min_refresh_rate 60.0"))
        assertTrue(executed.contains("settings put system peak_refresh_rate 120.0"))
    }

    @Test
    fun resetRefreshRate_executesDeleteSettings() = runTest(testDispatcher) {
        val result = engine.resetRefreshRate()
        assertTrue(result.isSuccess)

        val executed = fakeBackend.getExecutedCommands()
        assertTrue(executed.contains("settings delete system min_refresh_rate"))
        assertTrue(executed.contains("settings delete system peak_refresh_rate"))
    }

    @Test
    fun calculateScaledDpi_proportionalScaling() {
        val scaledDpi = engine.calculateScaledDpi(
            baseWidth = 1080,
            targetWidth = 720,
            baseDpi = 420
        )
        // (720 / 1080) * 420 = 280
        assertEquals(280, scaledDpi)
    }

    @Test
    fun restoreSnapshot_restoresOverrides() = runTest(testDispatcher) {
        val snapshot = DisplayStateSnapshot(
            physicalWidth = 1080,
            physicalHeight = 2400,
            overrideWidth = 720,
            overrideHeight = 1600,
            physicalDpi = 420,
            overrideDpi = 360,
            minRefreshRateHz = 60.0f,
            peakRefreshRateHz = 120.0f
        )

        val result = engine.restoreSnapshot(snapshot)
        assertTrue(result.isSuccess)

        val executed = fakeBackend.getExecutedCommands()
        assertTrue(executed.contains("wm size 720x1600"))
        assertTrue(executed.contains("wm density 360"))
        assertTrue(executed.contains("settings put system min_refresh_rate 60.0"))
        assertTrue(executed.contains("settings put system peak_refresh_rate 120.0"))
    }

    @Test
    fun restoreSnapshot_whenNoOverrides_executesResets() = runTest(testDispatcher) {
        val snapshot = DisplayStateSnapshot(
            physicalWidth = 1080,
            physicalHeight = 2400,
            physicalDpi = 420
        )

        val result = engine.restoreSnapshot(snapshot)
        assertTrue(result.isSuccess)

        val executed = fakeBackend.getExecutedCommands()
        assertTrue(executed.contains("wm size reset"))
        assertTrue(executed.contains("wm density reset"))
        assertTrue(executed.contains("settings delete system min_refresh_rate"))
        assertTrue(executed.contains("settings delete system peak_refresh_rate"))
    }
}
