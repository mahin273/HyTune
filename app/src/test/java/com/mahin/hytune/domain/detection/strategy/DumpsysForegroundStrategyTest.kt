package com.mahin.hytune.domain.detection.strategy

import com.mahin.hytune.core.privilege.CommandResult
import com.mahin.hytune.core.privilege.FakePrivilegeBackend
import com.mahin.hytune.domain.detection.model.DetectionSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class DumpsysForegroundStrategyTest {

    private lateinit var fakeBackend: FakePrivilegeBackend
    private lateinit var strategy: DumpsysForegroundStrategy

    @Before
    fun setUp() {
        fakeBackend = FakePrivilegeBackend()
        strategy = DumpsysForegroundStrategy(fakeBackend)
    }

    @Test
    fun detect_parsesResumedActivityFromDumpsysActivities() = runTest {
        val dumpsysOutput = """
            ACTIVITY MANAGER ACTIVITIES (dumpsys activity activities)
            Display #0 (activities from top to bottom):
              Stack #1:
                Task id #42
                  * TaskRecord{4a1b2c3 #42 A=com.tencent.ig U=0 sz=1}
                    mResumedActivity: ActivityRecord{7d8e9f0 u0 com.tencent.ig/com.epicgames.ue4.SplashActivity t42}
        """.trimIndent()

        fakeBackend.registerCommandHandler("dumpsys activity activities") {
            CommandResult.success(dumpsysOutput.lines())
        }

        val result = strategy.detect()

        assertNotNull(result)
        assertEquals("com.tencent.ig", result!!.packageName)
        assertEquals("com.epicgames.ue4.SplashActivity", result.className)
        assertEquals(DetectionSource.DUMPSYS, result.source)
    }

    @Test
    fun detect_normalizesRelativeClassNamePrefix() = runTest {
        val dumpsysOutput = """
            topResumedActivity=ActivityRecord{abc1234 u0 com.supercell.clashofclans/.GameAppActivity t99}
        """.trimIndent()

        fakeBackend.registerCommandHandler("dumpsys activity activities") {
            CommandResult.success(dumpsysOutput.lines())
        }

        val result = strategy.detect()

        assertNotNull(result)
        assertEquals("com.supercell.clashofclans", result!!.packageName)
        assertEquals("com.supercell.clashofclans.GameAppActivity", result.className)
    }

    @Test
    fun detect_fallsBackToDumpsysWindowWhenActivitiesOutputMissing() = runTest {
        fakeBackend.registerCommandHandler("dumpsys activity activities") {
            CommandResult.success(emptyList())
        }

        val windowOutput = """
            WINDOW MANAGER WINDOWS (dumpsys window windows)
              mCurrentFocus=Window{a1b2c3d u0 com.valvesoftware.steam/com.valvesoftware.steam.MainActivity}
        """.trimIndent()
        fakeBackend.registerCommandHandler("dumpsys window windows") {
            CommandResult.success(windowOutput.lines())
        }

        val result = strategy.detect()

        assertNotNull(result)
        assertEquals("com.valvesoftware.steam", result!!.packageName)
        assertEquals("com.valvesoftware.steam.MainActivity", result.className)
    }

    @Test
    fun detect_returnsNullWhenAllDumpsysCallsFail() = runTest {
        fakeBackend.registerCommandHandler("dumpsys activity activities") {
            CommandResult.failure(1, listOf("permission denied"))
        }
        fakeBackend.registerCommandHandler("dumpsys window windows") {
            CommandResult.failure(1, listOf("permission denied"))
        }

        val result = strategy.detect()
        assertNull(result)
    }
}
