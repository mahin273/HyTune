package com.mahin.hytune.domain.diagnostics.engine

import android.os.Build
import com.mahin.hytune.core.privilege.PrivilegeBackend
import com.mahin.hytune.domain.battery.BatteryRepository
import com.mahin.hytune.domain.battery.model.BatteryHealth
import com.mahin.hytune.domain.diagnostics.model.DiagnosticCheckItem
import com.mahin.hytune.domain.diagnostics.model.DiagnosticReport
import com.mahin.hytune.domain.diagnostics.model.DiagnosticStatus
import com.mahin.hytune.domain.diagnostics.model.HardwareSubsystem
import com.mahin.hytune.domain.diagnostics.model.SubsystemDiagnosticResult
import com.mahin.hytune.domain.probe.HardwareProbe
import com.mahin.hytune.domain.thermal.ThermalEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HardwareDiagnosticsEngine @Inject constructor(
    private val backend: PrivilegeBackend,
    private val probe: HardwareProbe,
    private val thermalEngine: ThermalEngine,
    private val batteryRepository: BatteryRepository,
    private val sensorDiagnosticsEngine: SensorDiagnosticsEngine,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun runFullDiagnostics(): DiagnosticReport = withContext(ioDispatcher) {
        val cpuResult = checkCpuSubsystem()
        val thermalResult = checkThermalSubsystem()
        val memoryResult = checkMemorySubsystem()
        val batteryResult = checkBatterySubsystem()
        val sensorResult = checkSensorSubsystem()

        val subsystems = listOf(
            cpuResult,
            thermalResult,
            memoryResult,
            batteryResult,
            sensorResult
        )

        val overallStatus = when {
            subsystems.any { it.status == DiagnosticStatus.FAIL } -> DiagnosticStatus.FAIL
            subsystems.any { it.status == DiagnosticStatus.WARNING } -> DiagnosticStatus.WARNING
            else -> DiagnosticStatus.PASS
        }

        val deviceSummary = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})"

        DiagnosticReport(
            timestampEpochMs = System.currentTimeMillis(),
            overallStatus = overallStatus,
            subsystems = subsystems,
            deviceSummary = deviceSummary
        )
    }

    private suspend fun checkCpuSubsystem(): SubsystemDiagnosticResult {
        val checks = mutableListOf<DiagnosticCheckItem>()
        var worstStatus = DiagnosticStatus.PASS

        // Check Online Cores
        val onlineStr = backend.readFile("/sys/devices/system/cpu/online").getOrNull()?.trim()
        val onlineCheck = if (!onlineStr.isNullOrBlank()) {
            val totalCores = Runtime.getRuntime().availableProcessors()
            val isSingleCoreOffline = onlineStr.contains("-") && !onlineStr.endsWith("${totalCores - 1}")
            if (isSingleCoreOffline) {
                worstStatus = maxOfStatus(worstStatus, DiagnosticStatus.WARNING)
                DiagnosticCheckItem(
                    name = "CPU Core Availability",
                    status = DiagnosticStatus.WARNING,
                    measuredValue = onlineStr,
                    detail = "Some CPU cores appear to be offline or hotplugged"
                )
            } else {
                DiagnosticCheckItem(
                    name = "CPU Core Availability",
                    status = DiagnosticStatus.PASS,
                    measuredValue = onlineStr,
                    detail = "All CPU clusters online"
                )
            }
        } else {
            worstStatus = maxOfStatus(worstStatus, DiagnosticStatus.WARNING)
            DiagnosticCheckItem(
                name = "CPU Core Availability",
                status = DiagnosticStatus.WARNING,
                measuredValue = "Unknown",
                detail = "Unable to read sysfs CPU online topology"
            )
        }
        checks.add(onlineCheck)

        // Check Governor
        val gov = backend.readFile("/sys/devices/system/cpu/cpufreq/policy0/scaling_governor").getOrNull()?.trim()
        val govCheck = if (!gov.isNullOrBlank()) {
            DiagnosticCheckItem(
                name = "CPU Scaling Governor",
                status = DiagnosticStatus.PASS,
                measuredValue = gov,
                detail = "Active CPU governor operational"
            )
        } else {
            worstStatus = maxOfStatus(worstStatus, DiagnosticStatus.WARNING)
            DiagnosticCheckItem(
                name = "CPU Scaling Governor",
                status = DiagnosticStatus.WARNING,
                measuredValue = "Unreadable",
                detail = "Kernel cpufreq policy node unreadable or restricted"
            )
        }
        checks.add(govCheck)

        val recommendation = when (worstStatus) {
            DiagnosticStatus.FAIL -> "Critical CPU governor or core failure detected"
            DiagnosticStatus.WARNING -> "Check governor settings or kernel hotplug configuration"
            DiagnosticStatus.PASS -> null
        }

        return SubsystemDiagnosticResult(
            subsystem = HardwareSubsystem.CPU,
            status = worstStatus,
            checks = checks,
            recommendation = recommendation
        )
    }

    private suspend fun checkThermalSubsystem(): SubsystemDiagnosticResult {
        val checks = mutableListOf<DiagnosticCheckItem>()
        var worstStatus = DiagnosticStatus.PASS

        val snapshot = thermalEngine.sampleThermals()
        val zones = snapshot.zones
        if (zones.isEmpty()) {
            checks.add(
                DiagnosticCheckItem(
                    name = "Thermal Sensors",
                    status = DiagnosticStatus.WARNING,
                    measuredValue = "0 zones",
                    detail = "No kernel thermal zones discovered in /sys/class/thermal"
                )
            )
            return SubsystemDiagnosticResult(
                subsystem = HardwareSubsystem.THERMAL,
                status = DiagnosticStatus.WARNING,
                checks = checks,
                recommendation = "Verify kernel thermal subsystem permissions"
            )
        }

        val maxTemp = snapshot.hottestZone?.tempC ?: zones.maxOfOrNull { it.tempC } ?: 0f
        val maxTempFormatted = "%.1f C".format(maxTemp)

        val tempStatus = when {
            maxTemp >= 80.0f -> DiagnosticStatus.FAIL
            maxTemp >= 55.0f -> DiagnosticStatus.WARNING
            else -> DiagnosticStatus.PASS
        }
        worstStatus = maxOfStatus(worstStatus, tempStatus)

        checks.add(
            DiagnosticCheckItem(
                name = "Peak Zone Temperature",
                status = tempStatus,
                measuredValue = maxTempFormatted,
                detail = "Discovered ${zones.size} thermal sensors across SoC"
            )
        )

        val recommendation = when (worstStatus) {
            DiagnosticStatus.FAIL -> "Dangerous SoC temperature! Allow device to cool and reduce overclock"
            DiagnosticStatus.WARNING -> "Device temperature is elevated. Consider applying a cooler profile"
            DiagnosticStatus.PASS -> null
        }

        return SubsystemDiagnosticResult(
            subsystem = HardwareSubsystem.THERMAL,
            status = worstStatus,
            checks = checks,
            recommendation = recommendation
        )
    }

    private suspend fun checkMemorySubsystem(): SubsystemDiagnosticResult {
        val checks = mutableListOf<DiagnosticCheckItem>()
        var worstStatus = DiagnosticStatus.PASS

        val meminfo = backend.readFile("/proc/meminfo").getOrNull() ?: ""
        var memTotalKb = 0L
        var memAvailableKb = 0L

        meminfo.lines().forEach { line ->
            val parts = line.split(":").map { it.trim() }
            if (parts.size == 2) {
                val valueStr = parts[1].replace("kB", "").trim()
                val kb = valueStr.toLongOrNull() ?: 0L
                when (parts[0]) {
                    "MemTotal" -> memTotalKb = kb
                    "MemAvailable" -> memAvailableKb = kb
                }
            }
        }

        if (memTotalKb > 0L) {
            val availableRatio = memAvailableKb.toDouble() / memTotalKb.toDouble()
            val availablePercent = (availableRatio * 100.0).toInt()
            val memStatus = if (availablePercent < 10) DiagnosticStatus.WARNING else DiagnosticStatus.PASS
            worstStatus = maxOfStatus(worstStatus, memStatus)

            checks.add(
                DiagnosticCheckItem(
                    name = "Available RAM",
                    status = memStatus,
                    measuredValue = "$availablePercent% ($memAvailableKb kB free)",
                    detail = "Total RAM: ${memTotalKb / 1024} MB"
                )
            )
        } else {
            worstStatus = maxOfStatus(worstStatus, DiagnosticStatus.WARNING)
            checks.add(
                DiagnosticCheckItem(
                    name = "Available RAM",
                    status = DiagnosticStatus.WARNING,
                    measuredValue = "Unknown",
                    detail = "Unable to parse /proc/meminfo"
                )
            )
        }

        // Check Swappiness
        val swappiness = backend.readFile("/proc/sys/vm/swappiness").getOrNull()?.trim()
        if (!swappiness.isNullOrBlank()) {
            checks.add(
                DiagnosticCheckItem(
                    name = "Virtual Memory Swappiness",
                    status = DiagnosticStatus.PASS,
                    measuredValue = swappiness,
                    detail = "Kernel swappiness parameter configured"
                )
            )
        }

        val recommendation = when (worstStatus) {
            DiagnosticStatus.FAIL -> "RAM exhaustion detected; background processes may be killed"
            DiagnosticStatus.WARNING -> "Low available RAM; consider optimizing swap or swappiness"
            DiagnosticStatus.PASS -> null
        }

        return SubsystemDiagnosticResult(
            subsystem = HardwareSubsystem.MEMORY,
            status = worstStatus,
            checks = checks,
            recommendation = recommendation
        )
    }

    private suspend fun checkBatterySubsystem(): SubsystemDiagnosticResult {
        val checks = mutableListOf<DiagnosticCheckItem>()
        var worstStatus = DiagnosticStatus.PASS

        val state = try {
            batteryRepository.getBatteryState()
        } catch (_: Exception) {
            null
        }

        if (state != null) {
            val tempC = state.temperatureC
            val tempStatus = when {
                tempC >= 48.0 -> DiagnosticStatus.FAIL
                tempC >= 42.0 -> DiagnosticStatus.WARNING
                else -> DiagnosticStatus.PASS
            }
            worstStatus = maxOfStatus(worstStatus, tempStatus)

            checks.add(
                DiagnosticCheckItem(
                    name = "Battery Temperature",
                    status = tempStatus,
                    measuredValue = state.temperatureFormatted,
                    detail = "Safe operational ceiling: 45°C"
                )
            )

            val healthStatus = when (state.health) {
                BatteryHealth.GOOD, BatteryHealth.UNKNOWN -> DiagnosticStatus.PASS
                BatteryHealth.OVERHEAT, BatteryHealth.DEAD, BatteryHealth.OVER_VOLTAGE -> DiagnosticStatus.FAIL
                BatteryHealth.COLD -> DiagnosticStatus.WARNING
            }
            worstStatus = maxOfStatus(worstStatus, healthStatus)

            checks.add(
                DiagnosticCheckItem(
                    name = "Battery Health Indicator",
                    status = healthStatus,
                    measuredValue = state.health.name,
                    detail = "Battery charge level: ${state.levelPercent}%"
                )
            )
        } else {
            worstStatus = maxOfStatus(worstStatus, DiagnosticStatus.WARNING)
            checks.add(
                DiagnosticCheckItem(
                    name = "Battery Status",
                    status = DiagnosticStatus.WARNING,
                    measuredValue = "Unavailable",
                    detail = "Unable to read battery state from subsystem"
                )
            )
        }

        val recommendation = when (worstStatus) {
            DiagnosticStatus.FAIL -> "Battery is dangerously hot. Stop fast charging and intense workloads"
            DiagnosticStatus.WARNING -> "Battery temperature is elevated"
            DiagnosticStatus.PASS -> null
        }

        return SubsystemDiagnosticResult(
            subsystem = HardwareSubsystem.BATTERY,
            status = worstStatus,
            checks = checks,
            recommendation = recommendation
        )
    }

    private fun checkSensorSubsystem(): SubsystemDiagnosticResult {
        val checks = mutableListOf<DiagnosticCheckItem>()
        val sensors = sensorDiagnosticsEngine.getMonitoredSensors()

        for (sensor in sensors) {
            checks.add(
                DiagnosticCheckItem(
                    name = "${sensor.typeName} Sensor",
                    status = DiagnosticStatus.PASS,
                    measuredValue = if (sensor.isPresent) sensor.name else "Not equipped",
                    detail = if (sensor.isPresent) "Vendor: ${sensor.vendor}" else "Sensor not present on this hardware"
                )
            )
        }

        val totalPresent = sensors.count { it.isPresent }
        checks.add(
            DiagnosticCheckItem(
                name = "Overall Sensor Suite",
                status = DiagnosticStatus.PASS,
                measuredValue = "$totalPresent/${sensors.size} equipped",
                detail = "Hardware sensor inventory compiled"
            )
        )

        return SubsystemDiagnosticResult(
            subsystem = HardwareSubsystem.SENSORS,
            status = DiagnosticStatus.PASS,
            checks = checks,
            recommendation = null
        )
    }

    private fun maxOfStatus(first: DiagnosticStatus, second: DiagnosticStatus): DiagnosticStatus {
        return when {
            first == DiagnosticStatus.FAIL || second == DiagnosticStatus.FAIL -> DiagnosticStatus.FAIL
            first == DiagnosticStatus.WARNING || second == DiagnosticStatus.WARNING -> DiagnosticStatus.WARNING
            else -> DiagnosticStatus.PASS
        }
    }
}
