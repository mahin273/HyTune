package com.mahin.hytune.domain.display

import com.mahin.hytune.core.privilege.PrivilegeManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DisplayRefreshRateController @Inject constructor(
    private val privilegeManager: PrivilegeManager
) {

    suspend fun getCurrentPeakRefreshRate(): Float? {
        val backend = privilegeManager.activeBackend.value
        val result = backend.exec("settings get system peak_refresh_rate")
        if (result.isSuccess) {
            val trimmed = result.output.trim()
            if (trimmed.isNotEmpty() && trimmed != "null") {
                return trimmed.toFloatOrNull()
            }
        }
        return null
    }

    suspend fun getCurrentMinRefreshRate(): Float? {
        val backend = privilegeManager.activeBackend.value
        val result = backend.exec("settings get system min_refresh_rate")
        if (result.isSuccess) {
            val trimmed = result.output.trim()
            if (trimmed.isNotEmpty() && trimmed != "null") {
                return trimmed.toFloatOrNull()
            }
        }
        return null
    }

    suspend fun setRefreshRate(hz: Int): Boolean {
        val backend = privilegeManager.activeBackend.value
        val rateStr = "$hz.0"
        val res1 = backend.exec("settings put system peak_refresh_rate $rateStr")
        val res2 = backend.exec("settings put system min_refresh_rate $rateStr")
        return res1.isSuccess && res2.isSuccess
    }

    suspend fun restoreRefreshRate(originalPeak: Float?, originalMin: Float?): Boolean {
        val backend = privilegeManager.activeBackend.value
        var success = true
        if (originalPeak != null) {
            val res = backend.exec("settings put system peak_refresh_rate $originalPeak")
            if (!res.isSuccess) success = false
        }
        if (originalMin != null) {
            val res = backend.exec("settings put system min_refresh_rate $originalMin")
            if (!res.isSuccess) success = false
        }
        return success
    }
}
