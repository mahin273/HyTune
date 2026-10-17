package com.mahin.hytune.domain.safety

import com.mahin.hytune.domain.safety.model.WhitelistResult
import java.io.File
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SysfsPathWhitelist @Inject constructor() {

    private val allowedPatterns = listOf(
        // CPU cpufreq governors, min/max freqs, and policy tunables
        Pattern.compile("^/sys/devices/system/cpu/cpu[0-9]+/cpufreq/[a-zA-Z0-9_]+$"),
        Pattern.compile("^/sys/devices/system/cpu/cpufreq/policy[0-9]+/[a-zA-Z0-9_]+$"),
        Pattern.compile("^/sys/devices/system/cpu/cpufreq/[a-zA-Z0-9_]+/[a-zA-Z0-9_]+$"),

        // CPU Core online/offline toggling
        Pattern.compile("^/sys/devices/system/cpu/cpu[0-9]+/online$"),

        // Qualcomm Adreno KGSL devfreq & sysfs controls
        Pattern.compile("^/sys/class/kgsl/kgsl-3d0/(devfreq/)?[a-zA-Z0-9_]+$"),
        Pattern.compile("^/sys/devices/platform/[a-zA-Z0-9_.,-]+/kgsl/kgsl-3d0/(devfreq/)?[a-zA-Z0-9_]+$"),

        // Generic Linux devfreq (Mali, Samsung Exynos, MediaTek, Adreno devfreq)
        Pattern.compile("^/sys/class/devfreq/[a-zA-Z0-9_.,-]+/[a-zA-Z0-9_]+$"),

        // Linux Virtual Memory tunables (/proc/sys/vm)
        Pattern.compile("^/proc/sys/vm/[a-zA-Z0-9_]+$"),

        // Linux Storage Block Queues (schedulers, read_ahead_kb, etc.)
        Pattern.compile("^/sys/block/[a-zA-Z0-9_]+/queue/[a-zA-Z0-9_]+$"),
        Pattern.compile("^/sys/devices/.*/block/[a-zA-Z0-9_]+/queue/[a-zA-Z0-9_]+$"),

        // ZRAM compressed swap block controls
        Pattern.compile("^/sys/block/zram[0-9]+/[a-zA-Z0-9_]+$"),

        // Thermal zones and cooling devices
        Pattern.compile("^/sys/class/thermal/thermal_zone[0-9]+/[a-zA-Z0-9_]+$"),
        Pattern.compile("^/sys/class/thermal/cooling_device[0-9]+/[a-zA-Z0-9_]+$"),
        Pattern.compile("^/sys/devices/virtual/thermal/thermal_zone[0-9]+/[a-zA-Z0-9_]+$")
    )

    private val forbiddenPatterns = listOf(
        Pattern.compile(".*/\\.\\.(/.*|$)"), // Directory traversal
        Pattern.compile("^/dev/block/(?!zram[0-9]+$).*"), // Physical flash partitions
        Pattern.compile("^/sys/firmware/.*"), // Firmware interfaces
        Pattern.compile("^/sys/kernel/debug/.*"), // Debugfs
        Pattern.compile("^/sys/fs/selinux/.*"), // SELinux policies
        Pattern.compile("^/proc/sysrq-trigger$"), // SysRq emergency trigger
        Pattern.compile("^/sys/power/state$") // Direct ACPI/sleep power state
    )

    fun validate(rawPath: String): WhitelistResult {
        val trimmed = rawPath.trim()
        if (trimmed.isEmpty()) {
            return WhitelistResult.Rejected(rawPath, "Path cannot be empty")
        }

        // Detect directory traversal or suspicious relative segments
        if (trimmed.contains("..")) {
            return WhitelistResult.Rejected(rawPath, "Path traversal sequence (..) detected")
        }

        // Lexically normalize path without host filesystem symlink resolution
        val normalized = normalizePath(trimmed)

        for (forbidden in forbiddenPatterns) {
            if (forbidden.matcher(normalized).matches()) {
                return WhitelistResult.Rejected(
                    normalized,
                    "Target path matches forbidden kernel security pattern"
                )
            }
        }

        val isApproved = allowedPatterns.any { pattern ->
            pattern.matcher(normalized).matches()
        }

        return if (isApproved) {
            WhitelistResult.Approved(normalized)
        } else {
            WhitelistResult.Rejected(
                normalized,
                "Target path is outside authorized kernel sysfs namespaces"
            )
        }
    }

    fun isWhitelisted(rawPath: String): Boolean {
        return validate(rawPath).isApproved
    }

    fun validateAll(paths: Collection<String>): WhitelistResult {
        for (path in paths) {
            val result = validate(path)
            if (result is WhitelistResult.Rejected) {
                return result
            }
        }
        return WhitelistResult.Approved("All ${paths.size} paths verified")
    }

    private fun normalizePath(path: String): String {
        return File(path).normalize().path
    }
}
