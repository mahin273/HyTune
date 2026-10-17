package com.mahin.hytune.domain.safety

import com.mahin.hytune.domain.safety.model.WhitelistResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SysfsPathWhitelistTest {

    private lateinit var whitelist: SysfsPathWhitelist

    @Before
    fun setUp() {
        whitelist = SysfsPathWhitelist()
    }

    @Test
    fun validCpuCpufreqPaths_areApproved() {
        val paths = listOf(
            "/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor",
            "/sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq",
            "/sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq",
            "/sys/devices/system/cpu/cpu4/cpufreq/scaling_governor",
            "/sys/devices/system/cpu/cpufreq/policy0/scaling_governor",
            "/sys/devices/system/cpu/cpufreq/schedutil/up_rate_limit_us"
        )

        for (path in paths) {
            val result = whitelist.validate(path)
            assertTrue("Expected $path to be approved", result is WhitelistResult.Approved)
            assertTrue(whitelist.isWhitelisted(path))
        }
    }

    @Test
    fun cpuCoreOnline_isApproved() {
        assertTrue(whitelist.isWhitelisted("/sys/devices/system/cpu/cpu1/online"))
        assertTrue(whitelist.isWhitelisted("/sys/devices/system/cpu/cpu7/online"))
    }

    @Test
    fun validGpuPaths_areApproved() {
        val paths = listOf(
            "/sys/class/kgsl/kgsl-3d0/devfreq/governor",
            "/sys/class/kgsl/kgsl-3d0/devfreq/min_freq",
            "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq",
            "/sys/class/kgsl/kgsl-3d0/min_clock_mhz",
            "/sys/class/devfreq/1c00000.qcom,kgsl-3d0/governor",
            "/sys/class/devfreq/gpufreq/governor"
        )

        for (path in paths) {
            assertTrue("Expected GPU path $path to be approved", whitelist.isWhitelisted(path))
        }
    }

    @Test
    fun validVmAndStoragePaths_areApproved() {
        val paths = listOf(
            "/proc/sys/vm/swappiness",
            "/proc/sys/vm/vfs_cache_pressure",
            "/proc/sys/vm/drop_caches",
            "/proc/sys/vm/compact_memory",
            "/sys/block/sda/queue/scheduler",
            "/sys/block/sda/queue/read_ahead_kb",
            "/sys/block/mmcblk0/queue/scheduler",
            "/sys/block/zram0/disksize",
            "/sys/block/zram0/reset",
            "/sys/block/zram0/comp_algorithm"
        )

        for (path in paths) {
            assertTrue("Expected $path to be approved", whitelist.isWhitelisted(path))
        }
    }

    @Test
    fun directoryTraversalAttempts_areRejected() {
        val traversalPaths = listOf(
            "/sys/devices/system/cpu/cpu0/cpufreq/../../../dev/block/bootdevice",
            "/sys/block/sda/queue/../../../../proc/sysrq-trigger",
            "/proc/sys/vm/../kernel/sysrq"
        )

        for (path in traversalPaths) {
            val result = whitelist.validate(path)
            assertTrue("Expected traversal $path to be rejected", result is WhitelistResult.Rejected)
            assertFalse(whitelist.isWhitelisted(path))
        }
    }

    @Test
    fun dangerousKernelPaths_areRejected() {
        val dangerousPaths = listOf(
            "/dev/block/sda",
            "/dev/block/bootdevice",
            "/sys/firmware/efi/efivars",
            "/sys/kernel/debug/tracing/trace",
            "/sys/fs/selinux/enforce",
            "/proc/sysrq-trigger",
            "/sys/power/state"
        )

        for (path in dangerousPaths) {
            val result = whitelist.validate(path)
            assertTrue("Expected dangerous path $path to be rejected", result is WhitelistResult.Rejected)
            assertFalse(whitelist.isWhitelisted(path))
        }
    }

    @Test
    fun emptyOrInvalidPaths_areRejected() {
        assertFalse(whitelist.isWhitelisted(""))
        assertFalse(whitelist.isWhitelisted("   "))
        assertFalse(whitelist.isWhitelisted("/random/unrecognized/node"))
    }

    @Test
    fun validateAll_verifiesEntireCollection() {
        val validList = listOf(
            "/proc/sys/vm/swappiness",
            "/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor"
        )
        assertTrue(whitelist.validateAll(validList) is WhitelistResult.Approved)

        val mixedList = listOf(
            "/proc/sys/vm/swappiness",
            "/dev/block/sda"
        )
        assertTrue(whitelist.validateAll(mixedList) is WhitelistResult.Rejected)
    }
}
