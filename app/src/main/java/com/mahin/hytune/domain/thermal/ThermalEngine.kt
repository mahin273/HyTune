package com.mahin.hytune.domain.thermal

import com.mahin.hytune.core.privilege.PrivilegeBackend
import com.mahin.hytune.domain.thermal.model.CoolingDeviceInfo
import com.mahin.hytune.domain.thermal.model.ThermalSnapshot
import com.mahin.hytune.domain.thermal.model.ThermalZoneInfo
import com.mahin.hytune.domain.thermal.model.ThermalZoneType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThermalEngine @Inject constructor(
    private val privilegeBackend: PrivilegeBackend,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    companion object {
        private const val THERMAL_BASE = "/sys/class/thermal"
        private const val MAX_ZONE_SCAN_LIMIT = 48
        private const val MAX_CDEV_SCAN_LIMIT = 32

        private const val CPU_THROTTLE_TEMP_C = 75.0f
        private const val GPU_THROTTLE_TEMP_C = 75.0f
        private const val BATTERY_THROTTLE_TEMP_C = 45.0f
        private const val SOC_THROTTLE_TEMP_C = 70.0f
    }

    suspend fun sampleThermals(): ThermalSnapshot = withContext(ioDispatcher) {
        val thermalEntries = privilegeBackend.listFiles(THERMAL_BASE).getOrNull() ?: emptyList()

        val zoneIds = if (thermalEntries.isNotEmpty()) {
            thermalEntries
                .filter { it.startsWith("thermal_zone") }
                .mapNotNull { it.removePrefix("thermal_zone").toIntOrNull() }
                .sorted()
        } else {
            (0 until MAX_ZONE_SCAN_LIMIT).toList()
        }

        val cdevIds = if (thermalEntries.isNotEmpty()) {
            thermalEntries
                .filter { it.startsWith("cooling_device") }
                .mapNotNull { it.removePrefix("cooling_device").toIntOrNull() }
                .sorted()
        } else {
            (0 until MAX_CDEV_SCAN_LIMIT).toList()
        }

        val zones = mutableListOf<ThermalZoneInfo>()
        for (id in zoneIds) {
            val zonePath = "$THERMAL_BASE/thermal_zone$id"
            var typeRaw = privilegeBackend.readFile("$zonePath/type").getOrNull()?.trim()
            val tempRaw = privilegeBackend.readFile("$zonePath/temp").getOrNull()?.trim()?.toFloatOrNull() ?: continue
            if (typeRaw.isNullOrBlank()) {
                typeRaw = "thermal_zone$id"
            }

            val tempC = normalizeTemperature(tempRaw)
            val zoneType = classifyZoneType(typeRaw)
            val isZoneThrottling = isZoneThrottling(zoneType, tempC)

            zones.add(
                ThermalZoneInfo(
                    id = id,
                    name = typeRaw,
                    tempC = tempC,
                    type = zoneType,
                    isThrottling = isZoneThrottling
                )
            )
        }

        if (zones.none { it.type == ThermalZoneType.BATTERY }) {
            val battTempRaw = privilegeBackend.readFile("/sys/class/power_supply/battery/temp").getOrNull()?.trim()?.toFloatOrNull()
            if (battTempRaw != null) {
                val bTempC = normalizeTemperature(battTempRaw)
                zones.add(
                    ThermalZoneInfo(
                        id = 999,
                        name = "battery",
                        tempC = bTempC,
                        type = ThermalZoneType.BATTERY,
                        isThrottling = bTempC >= BATTERY_THROTTLE_TEMP_C
                    )
                )
            }
        }

        val coolingDevices = mutableListOf<CoolingDeviceInfo>()
        for (id in cdevIds) {
            val cdevPath = "$THERMAL_BASE/cooling_device$id"
            val typeRaw = privilegeBackend.readFile("$cdevPath/type").getOrNull()?.trim()
            if (typeRaw.isNullOrBlank()) continue

            val curState = privilegeBackend.readFile("$cdevPath/cur_state").getOrNull()?.trim()?.toIntOrNull() ?: 0
            val maxState = privilegeBackend.readFile("$cdevPath/max_state").getOrNull()?.trim()?.toIntOrNull() ?: 0

            coolingDevices.add(
                CoolingDeviceInfo(
                    id = id,
                    name = typeRaw,
                    curState = curState,
                    maxState = maxState,
                    isThrottling = curState > 0
                )
            )
        }

        val hottest = zones.maxByOrNull { it.tempC }
        val isSystemThrottling = coolingDevices.any { it.isThrottling } || zones.any { it.isThrottling }

        ThermalSnapshot(
            zones = zones,
            coolingDevices = coolingDevices,
            hottestZone = hottest,
            isThrottling = isSystemThrottling
        )
    }

    private fun normalizeTemperature(raw: Float): Float {
        return ThermalUtils.normalizeTemperature(raw)
    }

    private fun classifyZoneType(name: String): ThermalZoneType {
        val lower = name.lowercase()
        return when {
            lower.contains("cpu") || lower.contains("tsens") || lower.contains("krait") ||
                lower.contains("kryo") || lower.contains("gold") || lower.contains("silver") ||
                lower.contains("prime") || lower.contains("little") || lower.contains("big") ||
                lower.contains("a53") || lower.contains("a55") || lower.contains("a76") ||
                lower.contains("a77") || lower.contains("a78") || lower.contains("x1") ||
                lower.contains("x2") || lower.contains("x3") || lower.contains("x4") -> ThermalZoneType.CPU
            lower.contains("gpu") || lower.contains("gpuss") || lower.contains("adreno") ||
                lower.contains("mali") || lower.contains("kgsl") -> ThermalZoneType.GPU
            lower.contains("batt") || lower.contains("bms") || lower.contains("battery") -> ThermalZoneType.BATTERY
            lower.contains("soc") || lower.contains("system") || lower.contains("pmic") -> ThermalZoneType.SOC
            lower.contains("modem") || lower.contains("wlan") || lower.contains("wifi") -> ThermalZoneType.MODEM
            lower.contains("skin") || lower.contains("chg") || lower.contains("charger") || lower.contains("quiet") -> ThermalZoneType.SKIN
            else -> ThermalZoneType.OTHER
        }
    }

    private fun isZoneThrottling(type: ThermalZoneType, tempC: Float): Boolean {
        return when (type) {
            ThermalZoneType.CPU -> tempC >= CPU_THROTTLE_TEMP_C
            ThermalZoneType.GPU -> tempC >= GPU_THROTTLE_TEMP_C
            ThermalZoneType.BATTERY, ThermalZoneType.SKIN -> tempC >= BATTERY_THROTTLE_TEMP_C
            ThermalZoneType.SOC -> tempC >= SOC_THROTTLE_TEMP_C
            else -> false
        }
    }
}
