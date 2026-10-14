package com.mahin.hytune.domain.sampler

import com.mahin.hytune.core.privilege.PrivilegeBackend
import com.mahin.hytune.domain.sampler.model.BatterySample
import com.mahin.hytune.domain.sampler.model.CpuSample
import com.mahin.hytune.domain.sampler.model.MemorySample
import com.mahin.hytune.domain.sampler.model.SystemSnapshot
import com.mahin.hytune.domain.sampler.model.ThermalSample
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class SystemSamplerEngine @Inject constructor(
    private val privilegeBackend: PrivilegeBackend,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private var previousTotalJiffies: Long = 0L
    private var previousIdleJiffies: Long = 0L

    fun sampleStream(intervalMs: Long = 1000L): Flow<SystemSnapshot> = flow {
        while (currentCoroutineContext().isActive) {
            emit(sampleOnce())
            delay(intervalMs)
        }
    }.flowOn(ioDispatcher)

    suspend fun sampleOnce(): SystemSnapshot = withContext(ioDispatcher) {
        val timestamp = System.currentTimeMillis()
        val cpu = sampleCpu()
        val memory = sampleMemory()
        val thermals = sampleThermals()
        val battery = sampleBattery()

        SystemSnapshot(
            timestampEpochMs = timestamp,
            cpu = cpu,
            memory = memory,
            thermals = thermals,
            battery = battery
        )
    }

    suspend fun sampleCpu(): CpuSample {
        val statContent = privilegeBackend.readFile("/proc/stat").getOrDefault("")
        var overallUsage = 0f

        val firstLine = statContent.lineSequence().firstOrNull { it.startsWith("cpu ") }
        if (firstLine != null) {
            val parts = firstLine.removePrefix("cpu ").trim().split("\\s+".toRegex()).mapNotNull { it.toLongOrNull() }
            if (parts.size >= 4) {
                val user = parts[0]
                val nice = parts[1]
                val system = parts[2]
                val idle = parts[3]
                val iowait = if (parts.size > 4) parts[4] else 0L
                val irq = if (parts.size > 5) parts[5] else 0L
                val softirq = if (parts.size > 6) parts[6] else 0L
                val steal = if (parts.size > 7) parts[7] else 0L

                val currentTotal = user + nice + system + idle + iowait + irq + softirq + steal
                val currentIdle = idle + iowait

                if (previousTotalJiffies > 0L) {
                    val deltaTotal = currentTotal - previousTotalJiffies
                    val deltaIdle = currentIdle - previousIdleJiffies

                    if (deltaTotal > 0L) {
                        val active = (deltaTotal - deltaIdle).coerceAtLeast(0L)
                        overallUsage = (active * 100f / deltaTotal).coerceIn(0f, 100f)
                    }
                }

                previousTotalJiffies = currentTotal
                previousIdleJiffies = currentIdle
            }
        }

        val coreFrequencies = mutableMapOf<Int, Long>()
        val cpuBase = "/sys/devices/system/cpu"
        val cpuList = privilegeBackend.listFiles(cpuBase).getOrDefault(emptyList())
        val cpuDirs = cpuList.filter { it.matches(Regex("cpu[0-9]+")) }

        for (cpuDir in cpuDirs) {
            val coreIndex = cpuDir.removePrefix("cpu").toIntOrNull() ?: continue
            val curFreqPath = "$cpuBase/$cpuDir/cpufreq/scaling_cur_freq"
            val freqRaw = privilegeBackend.readFile(curFreqPath).getOrNull()?.trim()?.toLongOrNull()
            if (freqRaw != null && freqRaw > 0L) {
                coreFrequencies[coreIndex] = freqRaw
            }
        }

        return CpuSample(
            overallUsagePercent = overallUsage,
            coreFrequenciesKHz = coreFrequencies
        )
    }

    suspend fun sampleMemory(): MemorySample {
        val meminfoContent = privilegeBackend.readFile("/proc/meminfo").getOrDefault("")
        val entries = mutableMapOf<String, Long>()

        for (line in meminfoContent.lineSequence()) {
            val parts = line.split(":")
            if (parts.size >= 2) {
                val key = parts[0].trim()
                val valueStr = parts[1].trim().substringBefore(" ").trim()
                val valueKb = valueStr.toLongOrNull() ?: 0L
                entries[key] = valueKb
            }
        }

        val totalKb = entries["MemTotal"] ?: 0L
        val freeKb = entries["MemFree"] ?: 0L
        val buffersKb = entries["Buffers"] ?: 0L
        val cachedKb = entries["Cached"] ?: 0L

        val availableKb = entries["MemAvailable"] ?: (freeKb + buffersKb + cachedKb)
        val usedKb = (totalKb - availableKb).coerceAtLeast(0L)

        val swapTotalKb = entries["SwapTotal"] ?: 0L
        val swapFreeKb = entries["SwapFree"] ?: 0L
        val swapUsedKb = (swapTotalKb - swapFreeKb).coerceAtLeast(0L)

        val usagePercent = if (totalKb > 0L) (usedKb * 100f / totalKb).coerceIn(0f, 100f) else 0f
        val swapUsagePercent = if (swapTotalKb > 0L) (swapUsedKb * 100f / swapTotalKb).coerceIn(0f, 100f) else 0f

        return MemorySample(
            totalBytes = totalKb * 1024L,
            availableBytes = availableKb * 1024L,
            usedBytes = usedKb * 1024L,
            usagePercent = usagePercent,
            swapTotalBytes = swapTotalKb * 1024L,
            swapUsedBytes = swapUsedKb * 1024L,
            swapUsagePercent = swapUsagePercent
        )
    }

    suspend fun sampleThermals(): ThermalSample {
        val thermalBase = "/sys/class/thermal"
        val zones = mutableMapOf<String, Float>()
        var socTemp = 0f
        var batteryTemp = 0f
        var maxTemp = 0f

        val zoneDirs = privilegeBackend.listFiles(thermalBase).getOrDefault(emptyList())
            .filter { it.startsWith("thermal_zone") }
            .ifEmpty { (0..9).map { "thermal_zone$it" } }

        for (zone in zoneDirs) {
            val type = privilegeBackend.readFile("$thermalBase/$zone/type").getOrNull()?.trim() ?: zone
            val tempRawStr = privilegeBackend.readFile("$thermalBase/$zone/temp").getOrNull()?.trim() ?: continue
            val tempRaw = tempRawStr.toFloatOrNull() ?: continue

            val tempC = if (abs(tempRaw) > 1000f) tempRaw / 1000f else tempRaw

            zones[type] = tempC

            if (tempC > maxTemp) {
                maxTemp = tempC
            }

            val typeLower = type.lowercase()
            if (socTemp == 0f && (typeLower.contains("soc") || typeLower.contains("cpu") || typeLower.contains("tsens"))) {
                socTemp = tempC
            }
            if (batteryTemp == 0f && (typeLower.contains("battery") || typeLower.contains("bms") || typeLower.contains("batt"))) {
                batteryTemp = tempC
            }
        }

        if (socTemp == 0f) socTemp = maxTemp

        return ThermalSample(
            socTempC = socTemp,
            batteryTempC = batteryTemp,
            maxTempC = maxTemp,
            zones = zones
        )
    }

    suspend fun sampleBattery(): BatterySample? {
        val batteryBase = "/sys/class/power_supply/battery"
        if (!privilegeBackend.exists(batteryBase)) {
            return null
        }

        val capacityStr = privilegeBackend.readFile("$batteryBase/capacity").getOrNull()?.trim()
        val percent = capacityStr?.toIntOrNull() ?: 0

        val tempRawStr = privilegeBackend.readFile("$batteryBase/temp").getOrNull()?.trim()
        val tempRaw = tempRawStr?.toFloatOrNull() ?: 0f
        val tempC = when {
            tempRaw > 1000f -> tempRaw / 1000f
            tempRaw > 100f -> tempRaw / 10f
            else -> tempRaw
        }

        val voltageRaw = privilegeBackend.readFile("$batteryBase/voltage_now").getOrNull()?.trim()?.toLongOrNull() ?: 0L
        val voltageV = voltageRaw / 1_000_000f

        val currentRaw = privilegeBackend.readFile("$batteryBase/current_now").getOrNull()?.trim()?.toLongOrNull() ?: 0L
        val currentA = currentRaw / 1_000_000f

        val powerWatts = voltageV * currentA

        return BatterySample(
            percent = percent,
            temperatureC = tempC,
            voltageV = voltageV,
            currentAmperes = currentA,
            powerWatts = powerWatts
        )
    }
}
