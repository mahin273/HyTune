package com.mahin.hytune.domain.detection

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mahin.hytune.core.privilege.CommandResult
import com.mahin.hytune.core.privilege.FakePrivilegeBackend
import com.mahin.hytune.core.privilege.PrivilegeBackend
import com.mahin.hytune.core.privilege.PrivilegeManager
import com.mahin.hytune.core.privilege.PrivilegeTier
import com.mahin.hytune.core.privilege.RootShellRunner
import com.mahin.hytune.core.privilege.ShizukuShellRunner
import com.mahin.hytune.domain.detection.model.DetectionSource
import com.mahin.hytune.domain.detection.strategy.CgroupForegroundStrategy
import com.mahin.hytune.domain.detection.strategy.DumpsysForegroundStrategy
import com.mahin.hytune.domain.detection.strategy.UsageStatsForegroundStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class ForegroundAppDetectorTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var fakeBackend: FakePrivilegeBackend
    private lateinit var privilegeManager: PrivilegeManager
    private lateinit var ignoredFilter: IgnoredPackagesFilter
    private lateinit var cgroupStrategy: CgroupForegroundStrategy
    private lateinit var dumpsysStrategy: DumpsysForegroundStrategy
    private lateinit var usageStatsStrategy: UsageStatsForegroundStrategy
    private lateinit var detector: ForegroundAppDetector

    private var rootGranted = true
    private var shizukuGranted = false

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        fakeBackend = FakePrivilegeBackend()

        val rootRunner = object : RootShellRunner {
            override fun isRootAvailable(): Boolean = rootGranted
            override suspend fun runCommand(command: String): CommandResult = fakeBackend.exec(command)
        }

        val shizukuRunner = object : ShizukuShellRunner {
            override fun isShizukuAvailable(): Boolean = shizukuGranted
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

        ignoredFilter = IgnoredPackagesFilter(context)
        cgroupStrategy = CgroupForegroundStrategy(fakeBackend)
        dumpsysStrategy = DumpsysForegroundStrategy(fakeBackend)
        usageStatsStrategy = UsageStatsForegroundStrategy(context)

        detector = ForegroundAppDetector(
            privilegeManager = privilegeManager,
            cgroupStrategy = cgroupStrategy,
            dumpsysStrategy = dumpsysStrategy,
            usageStatsStrategy = usageStatsStrategy,
            ignoredPackagesFilter = ignoredFilter,
            ioDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun detectOnce_usesCgroupWhenRootAvailable() = runTest(testDispatcher) {
        rootGranted = true
        privilegeManager.refresh()

        fakeBackend.setFile("/dev/cpuset/top-app/cgroup.procs", "4567\n")
        fakeBackend.setFile("/proc/4567/cmdline", "com.ea.gp.apexlegendsmobilefps\u0000")

        val result = detector.detectOnce()

        assertNotNull(result)
        assertEquals("com.ea.gp.apexlegendsmobilefps", result!!.packageName)
        assertEquals(DetectionSource.CGROUP, result.source)
        assertFalse(result.isHomeLauncher)
    }

    @Test
    fun detectOnce_fallsBackToDumpsysWhenCgroupFails() = runTest(testDispatcher) {
        rootGranted = true
        privilegeManager.refresh()

        val dumpsysOutput = "mResumedActivity: ActivityRecord{abc u0 com.riotgames.league.wildrift/com.riotgames.GameActivity t1}"
        fakeBackend.registerCommandHandler("dumpsys activity activities") {
            CommandResult.success(dumpsysOutput.lines())
        }

        val result = detector.detectOnce()

        assertNotNull(result)
        assertEquals("com.riotgames.league.wildrift", result!!.packageName)
        assertEquals(DetectionSource.DUMPSYS, result.source)
    }

    @Test
    fun detectOnce_ignoresSystemUiAndPreservesPreviousApp() = runTest(testDispatcher) {
        rootGranted = true
        privilegeManager.refresh()

        // 1. Detect legitimate game
        fakeBackend.setFile("/dev/cpuset/top-app/cgroup.procs", "1001\n")
        fakeBackend.setFile("/proc/1001/cmdline", "com.epicgames.fortnite\u0000")

        val gameResult = detector.detectOnce()
        assertEquals("com.epicgames.fortnite", gameResult?.packageName)

        // 2. User opens SystemUI notification shade
        fakeBackend.setFile("/proc/1001/cmdline", "com.android.systemui\u0000")

        val shadeResult = detector.detectOnce()
        // Should preserve Fortnite as the foreground app
        assertEquals("com.epicgames.fortnite", shadeResult?.packageName)
    }

    @Test
    fun detectOnce_flagsHomeLauncher() = runTest(testDispatcher) {
        rootGranted = true
        privilegeManager.refresh()

        fakeBackend.setFile("/dev/cpuset/top-app/cgroup.procs", "1002\n")
        fakeBackend.setFile("/proc/1002/cmdline", "com.google.android.apps.nexuslauncher\u0000")

        val result = detector.detectOnce()

        assertNotNull(result)
        assertEquals("com.google.android.apps.nexuslauncher", result!!.packageName)
        assertTrue(result.isHomeLauncher)
    }

    @Test
    fun startDetectionStream_emitsDebouncedUpdates() = runTest(testDispatcher) {
        fakeBackend.setFile("/dev/cpuset/top-app/cgroup.procs", "2001\n")
        fakeBackend.setFile("/proc/2001/cmdline", "com.dts.freefireth\u0000")

        val stream = detector.startDetectionStream(pollingIntervalMs = 10L, debounceMs = 10L)
        val firstInfo = stream.first()

        assertNotNull(firstInfo)
        assertEquals("com.dts.freefireth", firstInfo.packageName)
    }
}
