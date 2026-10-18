package com.mahin.hytune.domain.battery.model

data class BatteryState(
    val levelPercent: Int = 0,
    val status: BatteryStatus = BatteryStatus.UNKNOWN,
    val health: BatteryHealth = BatteryHealth.UNKNOWN,
    val pluggedType: PluggedType = PluggedType.NONE,
    val voltageMv: Int = 0,
    val currentMa: Int = 0,
    val powerWatts: Float = 0.0f,
    val temperatureC: Float = 0.0f,
    val chargeCounterUah: Long = 0L,
    val technology: String = "Li-ion",
    val timestampEpochMs: Long = System.currentTimeMillis()
) {
    val isCharging: Boolean get() = status == BatteryStatus.CHARGING || pluggedType != PluggedType.NONE

    val voltageFormatted: String get() = String.format("%.2f V", voltageMv / 1000.0)

    val currentFormatted: String get() = "${if (currentMa > 0) "+" else ""}$currentMa mA"

    val powerFormatted: String get() = String.format("%.2f W", powerWatts)

    val temperatureFormatted: String get() = String.format("%.1f C", temperatureC)
}

enum class BatteryStatus {
    CHARGING,
    DISCHARGING,
    FULL,
    NOT_CHARGING,
    UNKNOWN
}

enum class BatteryHealth {
    GOOD,
    OVERHEAT,
    DEAD,
    OVER_VOLTAGE,
    COLD,
    UNKNOWN
}

enum class PluggedType {
    NONE,
    AC,
    USB,
    WIRELESS
}
