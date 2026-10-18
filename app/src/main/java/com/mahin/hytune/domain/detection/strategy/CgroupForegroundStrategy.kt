package com.mahin.hytune.domain.detection.strategy

import com.mahin.hytune.core.privilege.PrivilegeBackend
import com.mahin.hytune.domain.detection.model.DetectionSource
import com.mahin.hytune.domain.detection.model.ForegroundInfo
import javax.inject.Inject

class CgroupForegroundStrategy @Inject constructor(
    private val privilegeBackend: PrivilegeBackend
) : ForegroundDetectionStrategy {

    companion object {
        val CANDIDATE_CGROUP_PATHS = listOf(
            "/dev/cpuset/top-app/cgroup.procs",
            "/sys/fs/cgroup/top-app/cgroup.procs",
            "/dev/stune/top-app/cgroup.procs"
        )
        private val PACKAGE_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+$")
    }

    override suspend fun detect(): ForegroundInfo? {
        for (cgroupPath in CANDIDATE_CGROUP_PATHS) {
            val content = privilegeBackend.readFile(cgroupPath).getOrNull()?.trim()
            if (content.isNullOrBlank()) continue

            val pids = content.lines()
                .mapNotNull { it.trim().toIntOrNull() }
                .reversed()

            for (pid in pids) {
                val cmdline = privilegeBackend.readFile("/proc/$pid/cmdline").getOrNull()
                if (!cmdline.isNullOrBlank()) {
                    val rawName = cmdline.split("\u0000").firstOrNull()?.trim() ?: continue
                    val packageName = rawName.substringBefore(":")
                    if (isValidPackageName(packageName)) {
                        return ForegroundInfo(
                            packageName = packageName,
                            source = DetectionSource.CGROUP
                        )
                    }
                }
            }
        }
        return null
    }

    private fun isValidPackageName(name: String): Boolean {
        return PACKAGE_REGEX.matches(name)
    }
}
