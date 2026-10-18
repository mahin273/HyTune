package com.mahin.hytune.domain.detection.strategy

import com.mahin.hytune.core.privilege.FakePrivilegeBackend
import com.mahin.hytune.domain.detection.model.DetectionSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class CgroupForegroundStrategyTest {

    private lateinit var fakeBackend: FakePrivilegeBackend
    private lateinit var strategy: CgroupForegroundStrategy

    @Before
    fun setUp() {
        fakeBackend = FakePrivilegeBackend()
        strategy = CgroupForegroundStrategy(fakeBackend)
    }

    @Test
    fun detect_returnsForegroundInfoFromCgroup() = runTest {
        val cgroupPath = "/dev/cpuset/top-app/cgroup.procs"
        fakeBackend.setFile(cgroupPath, "1234\n5678\n")
        fakeBackend.setFile("/proc/5678/cmdline", "com.miHoYo.GenshinImpact\u0000--extra-flags\n")

        val result = strategy.detect()

        assertNotNull(result)
        assertEquals("com.miHoYo.GenshinImpact", result!!.packageName)
        assertEquals(DetectionSource.CGROUP, result.source)
    }

    @Test
    fun detect_stripsProcessSuffixCorrectly() = runTest {
        val cgroupPath = "/dev/cpuset/top-app/cgroup.procs"
        fakeBackend.setFile(cgroupPath, "2048\n")
        fakeBackend.setFile("/proc/2048/cmdline", "com.example.gaming:unityEngine\u0000")

        val result = strategy.detect()

        assertNotNull(result)
        assertEquals("com.example.gaming", result!!.packageName)
    }

    @Test
    fun detect_triesFallbackCgroupPathWhenFirstIsEmpty() = runTest {
        // First path empty, second path has cgroup procs
        fakeBackend.setFile("/sys/fs/cgroup/top-app/cgroup.procs", "9999\n")
        fakeBackend.setFile("/proc/9999/cmdline", "com.pubg.imobile\u0000")

        val result = strategy.detect()

        assertNotNull(result)
        assertEquals("com.pubg.imobile", result!!.packageName)
    }

    @Test
    fun detect_returnsNullWhenAllCgroupNodesEmptyOrMissing() = runTest {
        val result = strategy.detect()
        assertNull(result)
    }
}
