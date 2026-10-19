package com.mahin.hytune.domain.display

import com.mahin.hytune.core.privilege.PrivilegeBackend
import com.mahin.hytune.domain.display.model.DisplayDensity
import com.mahin.hytune.domain.display.model.DisplayRefreshRate
import com.mahin.hytune.domain.display.model.DisplayResolution
import com.mahin.hytune.domain.display.model.DisplayStateSnapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DisplayControlEngine @Inject constructor(
    private val privilegeBackend: PrivilegeBackend,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    companion object {
        const val MIN_DIMENSION = 240
        const val MAX_DIMENSION = 7680
        const val MIN_DPI = 72
        const val MAX_DPI = 1000
        const val MIN_HZ = 10f
        const val MAX_HZ = 360f
    }

    suspend fun getDisplayState(): Result<DisplayStateSnapshot> = withContext(ioDispatcher) {
        runCatching {
            val sizeResult = privilegeBackend.exec("wm size")
            var physicalWidth = 1080
            var physicalHeight = 2400
            var overrideWidth: Int? = null
            var overrideHeight: Int? = null

            for (line in sizeResult.stdout) {
                val trimmed = line.trim()
                if (trimmed.startsWith("Physical size:", ignoreCase = true)) {
                    val rawSize = trimmed.substringAfter(":").trim()
                    val parts = rawSize.split("x")
                    if (parts.size == 2) {
                        physicalWidth = parts[0].trim().toIntOrNull() ?: physicalWidth
                        physicalHeight = parts[1].trim().toIntOrNull() ?: physicalHeight
                    }
                } else if (trimmed.startsWith("Override size:", ignoreCase = true)) {
                    val rawSize = trimmed.substringAfter(":").trim()
                    val parts = rawSize.split("x")
                    if (parts.size == 2) {
                        overrideWidth = parts[0].trim().toIntOrNull()
                        overrideHeight = parts[1].trim().toIntOrNull()
                    }
                }
            }

            val densityResult = privilegeBackend.exec("wm density")
            var physicalDpi = 420
            var overrideDpi: Int? = null

            for (line in densityResult.stdout) {
                val trimmed = line.trim()
                if (trimmed.startsWith("Physical density:", ignoreCase = true)) {
                    val rawDpi = trimmed.substringAfter(":").trim()
                    physicalDpi = rawDpi.toIntOrNull() ?: physicalDpi
                } else if (trimmed.startsWith("Override density:", ignoreCase = true)) {
                    val rawDpi = trimmed.substringAfter(":").trim()
                    overrideDpi = rawDpi.toIntOrNull()
                }
            }

            val minRefreshResult = privilegeBackend.exec("settings get system min_refresh_rate")
            val peakRefreshResult = privilegeBackend.exec("settings get system peak_refresh_rate")

            val minHz = minRefreshResult.stdout.firstOrNull()?.trim()?.toFloatOrNull()
            val peakHz = peakRefreshResult.stdout.firstOrNull()?.trim()?.toFloatOrNull()

            DisplayStateSnapshot(
                physicalWidth = physicalWidth,
                physicalHeight = physicalHeight,
                overrideWidth = overrideWidth,
                overrideHeight = overrideHeight,
                physicalDpi = physicalDpi,
                overrideDpi = overrideDpi,
                minRefreshRateHz = minHz,
                peakRefreshRateHz = peakHz
            )
        }
    }

    suspend fun setResolution(width: Int, height: Int): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            require(width in MIN_DIMENSION..MAX_DIMENSION) { "Width $width is out of range ($MIN_DIMENSION..$MAX_DIMENSION)" }
            require(height in MIN_DIMENSION..MAX_DIMENSION) { "Height $height is out of range ($MIN_DIMENSION..$MAX_DIMENSION)" }

            val result = privilegeBackend.exec("wm size ${width}x${height}")
            if (!result.isSuccess) {
                throw IllegalStateException("Failed to set resolution: ${result.errorOutput}")
            }
        }
    }

    suspend fun resetResolution(): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val result = privilegeBackend.exec("wm size reset")
            if (!result.isSuccess) {
                throw IllegalStateException("Failed to reset resolution: ${result.errorOutput}")
            }
        }
    }

    suspend fun setDensity(dpi: Int): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            require(dpi in MIN_DPI..MAX_DPI) { "DPI $dpi is out of range ($MIN_DPI..$MAX_DPI)" }

            val result = privilegeBackend.exec("wm density $dpi")
            if (!result.isSuccess) {
                throw IllegalStateException("Failed to set density: ${result.errorOutput}")
            }
        }
    }

    suspend fun resetDensity(): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val result = privilegeBackend.exec("wm density reset")
            if (!result.isSuccess) {
                throw IllegalStateException("Failed to reset density: ${result.errorOutput}")
            }
        }
    }

    suspend fun setRefreshRate(minHz: Float, peakHz: Float): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            require(minHz in MIN_HZ..MAX_HZ) { "Min refresh rate $minHz is out of range ($MIN_HZ..$MAX_HZ)" }
            require(peakHz in MIN_HZ..MAX_HZ) { "Peak refresh rate $peakHz is out of range ($MIN_HZ..$MAX_HZ)" }
            require(minHz <= peakHz) { "Min refresh rate ($minHz) cannot exceed peak ($peakHz)" }

            val res1 = privilegeBackend.exec("settings put system min_refresh_rate $minHz")
            val res2 = privilegeBackend.exec("settings put system peak_refresh_rate $peakHz")
            privilegeBackend.exec("settings put system user_refresh_rate $peakHz")
            privilegeBackend.exec("settings put secure user_refresh_rate $peakHz")
            privilegeBackend.exec("settings put secure refresh_rate_mode 2")
            if (!res1.isSuccess || !res2.isSuccess) {
                throw IllegalStateException("Failed to set refresh rate: ${res1.errorOutput} ${res2.errorOutput}")
            }
        }
    }

    suspend fun resetRefreshRate(): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            privilegeBackend.exec("settings delete system min_refresh_rate")
            privilegeBackend.exec("settings delete system peak_refresh_rate")
            privilegeBackend.exec("settings delete system user_refresh_rate")
            privilegeBackend.exec("settings delete secure user_refresh_rate")
            privilegeBackend.exec("settings delete secure refresh_rate_mode")
            Unit
        }
    }

    fun calculateScaledDpi(baseWidth: Int, targetWidth: Int, baseDpi: Int): Int {
        if (baseWidth <= 0 || targetWidth <= 0 || baseDpi <= 0) return baseDpi
        val ratio = targetWidth.toFloat() / baseWidth.toFloat()
        return (baseDpi * ratio).toInt().coerceIn(MIN_DPI, MAX_DPI)
    }

    suspend fun restoreSnapshot(snapshot: DisplayStateSnapshot): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            if (snapshot.overrideWidth != null && snapshot.overrideHeight != null) {
                setResolution(snapshot.overrideWidth, snapshot.overrideHeight).getOrThrow()
            } else {
                resetResolution().getOrThrow()
            }

            if (snapshot.overrideDpi != null) {
                setDensity(snapshot.overrideDpi).getOrThrow()
            } else {
                resetDensity().getOrThrow()
            }

            if (snapshot.minRefreshRateHz != null && snapshot.peakRefreshRateHz != null) {
                setRefreshRate(snapshot.minRefreshRateHz, snapshot.peakRefreshRateHz).getOrThrow()
            } else {
                resetRefreshRate().getOrThrow()
            }
            Unit
        }
    }
}
