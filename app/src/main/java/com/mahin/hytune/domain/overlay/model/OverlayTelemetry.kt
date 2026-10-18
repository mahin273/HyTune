package com.mahin.hytune.domain.overlay.model

data class OverlayTelemetry(
    val cpuLoadPercent: Int = 0,
    val cpuFreqMhz: Int = 0,
    val gpuLoadPercent: Int = 0,
    val gpuFreqMhz: Int = 0,
    val ramUsedMb: Int = 0,
    val ramTotalMb: Int = 0,
    val batteryPowerWatts: Float = 0f,
    val temperatureC: Float = 0f,
    val isThrottling: Boolean = false
) {
    fun toCompactText(config: OverlayConfig): String {
        val parts = mutableListOf<String>()
        if (config.showCpu) {
            val freqStr = if (cpuFreqMhz > 0) "${cpuFreqMhz}MHz" else "${cpuLoadPercent}%"
            parts.add("CPU $freqStr")
        }
        if (config.showGpu && gpuFreqMhz > 0) {
            parts.add("GPU ${gpuFreqMhz}MHz")
        }
        if (config.showTemp && temperatureC > 0f) {
            parts.add("${temperatureC.toInt()} C")
        }
        if (config.showBattery && batteryPowerWatts > 0f) {
            parts.add(String.format(java.util.Locale.US, "%.1fW", batteryPowerWatts))
        }
        if (config.showRam && ramTotalMb > 0) {
            parts.add("RAM ${ramUsedMb}/${ramTotalMb}MB")
        }
        if (isThrottling) {
            parts.add("THROTTLE")
        }
        return if (parts.isEmpty()) "HyTune HUD" else parts.joinToString(" | ")
    }
}
