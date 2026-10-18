package com.mahin.hytune.domain.detection.strategy

import com.mahin.hytune.core.privilege.PrivilegeBackend
import com.mahin.hytune.domain.detection.model.DetectionSource
import com.mahin.hytune.domain.detection.model.ForegroundInfo
import javax.inject.Inject

class DumpsysForegroundStrategy @Inject constructor(
    private val privilegeBackend: PrivilegeBackend
) : ForegroundDetectionStrategy {

    companion object {
        // Matches "mResumedActivity: ActivityRecord{... com.example.app/.MainActivity ...}"
        // or "topResumedActivity=ActivityRecord{... com.example.app/com.example.app.MainActivity ...}"
        private val RESUMED_ACTIVITY_REGEX = Regex(
            """(?:mResumedActivity|topResumedActivity|mFocusedActivity)[:=]\s*ActivityRecord\{.*?\b([a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+)+)/([^\s\}]+)"""
        )

        // Fallback: "mCurrentFocus=Window{... com.example.app/com.example.app.MainActivity}"
        private val CURRENT_FOCUS_REGEX = Regex(
            """(?:mCurrentFocus|mFocusedApp)[^\{]*\{.*?\b([a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+)+)/([^\s\}]+)"""
        )
    }

    override suspend fun detect(): ForegroundInfo? {
        val result = privilegeBackend.exec("dumpsys activity activities")
        if (result.isSuccess && result.output.isNotBlank()) {
            val match = RESUMED_ACTIVITY_REGEX.find(result.output)
            if (match != null) {
                val pkg = match.groupValues[1]
                val cls = match.groupValues[2]
                return ForegroundInfo(
                    packageName = pkg,
                    className = normalizeClassName(pkg, cls),
                    source = DetectionSource.DUMPSYS
                )
            }
        }

        // Fallback to dumpsys window
        val windowResult = privilegeBackend.exec("dumpsys window windows")
        if (windowResult.isSuccess && windowResult.output.isNotBlank()) {
            val match = CURRENT_FOCUS_REGEX.find(windowResult.output)
            if (match != null) {
                val pkg = match.groupValues[1]
                val cls = match.groupValues[2]
                return ForegroundInfo(
                    packageName = pkg,
                    className = normalizeClassName(pkg, cls),
                    source = DetectionSource.DUMPSYS
                )
            }
        }

        return null
    }

    private fun normalizeClassName(pkg: String, cls: String): String {
        return if (cls.startsWith(".")) {
            pkg + cls
        } else {
            cls
        }
    }
}
