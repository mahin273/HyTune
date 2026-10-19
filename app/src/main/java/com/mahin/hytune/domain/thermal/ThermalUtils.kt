package com.mahin.hytune.domain.thermal

import kotlin.math.abs

object ThermalUtils {

    /**
     * Normalizes raw temperature readings from various Linux/Android kernel sysfs nodes.
     *
     * Handles:
     * - Millidegrees Celsius (e.g., 45000 -> 45.0 C, 38500 -> 38.5 C)
     * - Deci-degrees Celsius (e.g., 450 -> 45.0 C, 820 -> 82.0 C)
     * - Direct Celsius degrees (e.g., 45.0 -> 45.0 C)
     *
     * Outlier protection clamps the result to plausible device operating limits [-40C, 125C].
     */
    fun normalizeTemperature(raw: Float): Float {
        val absVal = abs(raw)
        val normalized = when {
            absVal >= 10_000f -> raw / 1000f
            absVal >= 1000f -> raw / 1000f
            absVal >= 95f -> raw / 10f
            else -> raw
        }
        return normalized.coerceIn(-40f, 125f)
    }
}
