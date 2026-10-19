package com.mahin.hytune.domain.fps

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mahin.hytune.core.privilege.CommandResult
import com.mahin.hytune.core.privilege.FakePrivilegeBackend
import com.mahin.hytune.core.privilege.PrivilegeBackend
import com.mahin.hytune.core.privilege.PrivilegeManager
import com.mahin.hytune.core.privilege.PrivilegeTier
import com.mahin.hytune.core.privilege.RootShellRunner
import com.mahin.hytune.core.privilege.ShizukuShellRunner
import com.mahin.hytune.domain.detection.ForegroundAppDetector
import com.mahin.hytune.domain.detection.IgnoredPackagesFilter
import com.mahin.hytune.domain.detection.model.DetectionSource
import com.mahin.hytune.domain.detection.model.ForegroundInfo
import com.mahin.hytune.domain.detection.strategy.CgroupForegroundStrategy
import com.mahin.hytune.domain.detection.strategy.DumpsysForegroundStrategy
import com.mahin.hytune.domain.detection.strategy.UsageStatsForegroundStrategy
import com.mahin.hytune.domain.fps.model.FpsSource
import com.mahin.hytune.domain.fps.parser.GfxinfoFramestatsParser
import com.mahin.hytune.domain.fps.parser.SurfaceFlingerLatencyParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class FpsSamplerTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var fakeBackend: FakePrivilegeBackend
    private lateinit var privilegeManager: PrivilegeManager
    private lateinit var foregroundDetector: ForegroundAppDetector
    private lateinit var sfParser: SurfaceFlingerLatencyParser
    private lateinit var gfxParser: GfxinfoFramestatsParser
    private lateinit var fpsSampler: FpsSampler

    private var isRootAvailable = true

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        fakeBackend = FakePrivilegeBackend()

        val rootRunner = object : RootShellRunner {
            override fun isRootAvailable(): Boolean = isRootAvailable
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

        val ignoredFilter = IgnoredPackagesFilter(context)
        val cgroupStrategy = CgroupForegroundStrategy(fakeBackend)
        val dumpsysStrategy = DumpsysForegroundStrategy(fakeBackend)
        val usageStatsStrategy = UsageStatsForegroundStrategy(context)

        foregroundDetector = ForegroundAppDetector(
            privilegeManager = privilegeManager,
            cgroupStrategy = cgroupStrategy,
            dumpsysStrategy = dumpsysStrategy,
            usageStatsStrategy = usageStatsStrategy,
            ignoredPackagesFilter = ignoredFilter,
            ioDispatcher = testDispatcher
        )

        sfParser = SurfaceFlingerLatencyParser()
        gfxParser = GfxinfoFramestatsParser()

        fpsSampler = FpsSampler(
            privilegeManager = privilegeManager,
            foregroundAppDetector = foregroundDetector,
            surfaceFlingerParser = sfParser,
            gfxinfoParser = gfxParser,
            ioDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        fpsSampler.stop()
        Dispatchers.resetMain()
    }

    @Test
    fun findActiveSurfaceFlingerLayer_prioritizesSurfaceViewOverWindow() = runTest(testDispatcher) {
        val layersOutput = listOf(
            "StatusBar#0",
            "NavigationBar#0",
            "com.mihoyo.genshin/com.mihoyo.genshin.MainActivity#0",
            "SurfaceView[com.mihoyo.genshin/com.mihoyo.genshin.MainActivity]#0"
        )
        fakeBackend.registerCommandHandler("dumpsys SurfaceFlinger --list") {
            CommandResult.success(layersOutput)
        }

        val targetLayer = fpsSampler.findActiveSurfaceFlingerLayer("com.mihoyo.genshin")
        assertEquals("SurfaceView[com.mihoyo.genshin/com.mihoyo.genshin.MainActivity]#0", targetLayer)
    }

    @Test
    fun sampleOnce_withSurfaceFlingerLayer_computesLiveFps() = runTest(testDispatcher) {
        // Setup foreground app detection via dumpsys
        fakeBackend.registerCommandHandler("dumpsys activity activities") {
            CommandResult.success(listOf("mResumedActivity: ActivityRecord{123 u0 com.mihoyo.genshin/.MainActivity t1}"))
        }
        foregroundDetector.detectOnce()

        // Setup SurfaceFlinger list and latency
        val layerName = "SurfaceView[com.mihoyo.genshin/com.mihoyo.genshin.MainActivity]#0"
        fakeBackend.registerCommandHandler("dumpsys SurfaceFlinger --list") {
            CommandResult.success(listOf(layerName))
        }

        val latencyLines = mutableListOf("16666666")
        var currentTs = 1_000_000_000L
        for (i in 0 until 60) {
            latencyLines.add("$currentTs\t${currentTs + 1_000_000L}\t${currentTs + 500_000L}")
            currentTs += 16_666_666L
        }
        fakeBackend.registerCommandHandler("dumpsys SurfaceFlinger --latency") {
            CommandResult.success(latencyLines)
        }

        val snapshot = fpsSampler.sampleOnce()
        assertEquals(FpsSource.SURFACE_FLINGER, snapshot.source)
        assertEquals("com.mihoyo.genshin", snapshot.packageName)
        assertEquals(60, snapshot.totalFrames)
        assertTrue(snapshot.fps in 59.0..61.0)
    }

    @Test
    fun sampleOnce_whenSurfaceFlingerLayerMissing_fallsBackToGfxinfo() = runTest(testDispatcher) {
        fakeBackend.registerCommandHandler("dumpsys activity activities") {
            CommandResult.success(listOf("mResumedActivity: ActivityRecord{123 u0 com.android.chrome/.Main t1}"))
        }
        foregroundDetector.detectOnce()

        fakeBackend.registerCommandHandler("dumpsys SurfaceFlinger --list") {
            CommandResult.success(listOf("StatusBar#0"))
        }

        val gfxOutput = """
            ---PROFILEDATA---
            Flags,IntendedVsync,Vsync,OldestInputEvent,NewestInputEvent,HandleInputStart,AnimationStart,PerformTraversalsStart,DrawStart,SyncQueued,SyncStart,IssueDrawCommandsStart,SwapBuffers,FrameCompleted
            0,1000000000,1000000000,0,0,1001000000,1002000000,1003000000,1004000000,1005000000,1006000000,1007000000,1008000000,1014000000
            0,1016666666,1016666666,0,0,1017000000,1018000000,1019000000,1020000000,1021000000,1022000000,1023000000,1024000000,1028000000
            ---PROFILEDATA---
        """.trimIndent().lines()

        fakeBackend.registerCommandHandler("dumpsys gfxinfo") {
            CommandResult.success(gfxOutput)
        }

        val snapshot = fpsSampler.sampleOnce()
        assertEquals(FpsSource.GFXINFO, snapshot.source)
        assertEquals("com.android.chrome", snapshot.packageName)
        assertEquals(2, snapshot.totalFrames)
    }

    @Test
    fun sampleOnce_whenUnprivileged_returnsNoneSource() = runTest(testDispatcher) {
        isRootAvailable = false
        privilegeManager.refresh()

        val snapshot = fpsSampler.sampleOnce()
        assertEquals(FpsSource.NONE, snapshot.source)
        assertEquals(0.0, snapshot.fps, 0.01)
    }
}
