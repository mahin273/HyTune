package com.mahin.hytune.domain.diagnostics.model

enum class DiagnosticStatus {
    PASS,
    WARNING,
    FAIL
}

enum class HardwareSubsystem {
    CPU,
    THERMAL,
    MEMORY,
    BATTERY,
    SENSORS,
    DISPLAY
}

data class DiagnosticCheckItem(
    val name: String,
    val status: DiagnosticStatus,
    val measuredValue: String,
    val detail: String
)

data class SubsystemDiagnosticResult(
    val subsystem: HardwareSubsystem,
    val status: DiagnosticStatus,
    val checks: List<DiagnosticCheckItem>,
    val recommendation: String? = null
)

data class DiagnosticReport(
    val timestampEpochMs: Long = System.currentTimeMillis(),
    val overallStatus: DiagnosticStatus,
    val subsystems: List<SubsystemDiagnosticResult>,
    val deviceSummary: String = ""
)

data class SensorDiagnosticInfo(
    val type: Int,
    val typeName: String,
    val name: String,
    val vendor: String,
    val version: Int,
    val powerMa: Float,
    val resolution: Float,
    val maxRange: Float,
    val isPresent: Boolean
)

data class TouchDiagnosticMetrics(
    val sampleCount: Int = 0,
    val averageIntervalMs: Float = 0f,
    val touchPollingRateHz: Float = 0f,
    val jitterMs: Float = 0f,
    val pointerCount: Int = 1
)

enum class DeadPixelColor(val colorInt: Long, val label: String) {
    RED(0xFFFF0000, "Red"),
    GREEN(0xFF00FF00, "Green"),
    BLUE(0xFF0000FF, "Blue"),
    WHITE(0xFFFFFFFF, "White"),
    BLACK(0xFF000000, "Black")
}
