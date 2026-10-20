package com.mahin.hytune.domain.thermal.model

data class ThermalSnapshot(
    val zones: List<ThermalZoneInfo> = emptyList(),
    val coolingDevices: List<CoolingDeviceInfo> = emptyList(),
    val hottestZone: ThermalZoneInfo? = null,
    val isThrottling: Boolean = false,
    val timestampEpochMs: Long = System.currentTimeMillis()
) {
    val cpuTempC: Float?
        get() = zones.filter { it.type == ThermalZoneType.CPU }.maxOfOrNull { it.tempC }
            ?: hottestZone?.tempC

    val gpuTempC: Float?
        get() = zones.filter { it.type == ThermalZoneType.GPU }.maxOfOrNull { it.tempC }
            ?: zones.firstOrNull { it.name.lowercase().contains("gpu") || it.name.lowercase().contains("adreno") }?.tempC
            ?: hottestZone?.tempC

    val batteryTempC: Float?
        get() = zones.filter { it.type == ThermalZoneType.BATTERY }.maxOfOrNull { it.tempC }

    val socTempC: Float?
        get() = zones.filter { it.type == ThermalZoneType.SOC }.maxOfOrNull { it.tempC }
            ?: hottestZone?.tempC
}
