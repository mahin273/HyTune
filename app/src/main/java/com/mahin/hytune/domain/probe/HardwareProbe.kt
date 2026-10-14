package com.mahin.hytune.domain.probe

import com.mahin.hytune.core.privilege.PrivilegeBackend
import com.mahin.hytune.domain.probe.model.ClusterType
import com.mahin.hytune.domain.probe.model.CpuClusterInfo
import com.mahin.hytune.domain.probe.model.CpuHardwareSummary
import com.mahin.hytune.domain.probe.model.GpuInfo
import com.mahin.hytune.domain.probe.model.GpuVendor
import com.mahin.hytune.domain.probe.model.HardwareSummary
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HardwareProbe @Inject constructor(
    private val privilegeBackend: PrivilegeBackend
) {

    suspend fun probeHardware(): HardwareSummary {
        val cpuSummary = probeCpuInfo()
        val clusters = probeCpuClusters()
        val gpu = probeGpu()

        val finalCoreCount = if (clusters.isNotEmpty()) {
            clusters.sumOf { it.coreIds.size }
        } else {
            cpuSummary.coreCount
        }

        return HardwareSummary(
            socName = cpuSummary.hardwareName.ifBlank { "Generic SoC" },
            totalCores = finalCoreCount,
            clusters = clusters,
            gpu = gpu
        )
    }

    suspend fun probeCpuInfo(): CpuHardwareSummary {
        val content = privilegeBackend.readFile("/proc/cpuinfo").getOrDefault("")
        var processorCount = 0
        var hardware = ""
        var architecture = ""
        var implementer = ""

        for (line in content.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("processor", ignoreCase = true) && trimmed.contains(":")) {
                processorCount++
            } else if (trimmed.startsWith("Hardware", ignoreCase = true) && trimmed.contains(":")) {
                val value = trimmed.substringAfter(":").trim()
                if (value.isNotBlank() && hardware.isBlank()) {
                    hardware = value
                }
            } else if (trimmed.startsWith("model name", ignoreCase = true) && trimmed.contains(":")) {
                val value = trimmed.substringAfter(":").trim()
                if (value.isNotBlank() && hardware.isBlank()) {
                    hardware = value
                }
            } else if (trimmed.startsWith("CPU implementer", ignoreCase = true) && trimmed.contains(":")) {
                if (implementer.isBlank()) {
                    implementer = trimmed.substringAfter(":").trim()
                }
            } else if (trimmed.startsWith("CPU architecture", ignoreCase = true) && trimmed.contains(":")) {
                if (architecture.isBlank()) {
                    architecture = trimmed.substringAfter(":").trim()
                }
            }
        }

        if (processorCount == 0) {
            processorCount = Runtime.getRuntime().availableProcessors()
        }

        return CpuHardwareSummary(
            coreCount = processorCount,
            hardwareName = hardware,
            architecture = architecture,
            implementer = implementer
        )
    }

    suspend fun probeCpuClusters(): List<CpuClusterInfo> {
        val policyBasePath = "/sys/devices/system/cpu/cpufreq"
        val discoveredPolicies = mutableListOf<RawPolicy>()

        val listResult = privilegeBackend.listFiles(policyBasePath)
        if (listResult.isSuccess) {
            val entries = listResult.getOrDefault(emptyList())
            val policyNames = entries.filter { it.matches(Regex("policy[0-9]+")) }
                .sortedBy { it.removePrefix("policy").toIntOrNull() ?: 0 }

            for (policy in policyNames) {
                val path = "$policyBasePath/$policy"
                val raw = readPolicyData(path, policy.removePrefix("policy").toIntOrNull() ?: 0)
                if (raw != null) {
                    discoveredPolicies.add(raw)
                }
            }
        }

        if (discoveredPolicies.isEmpty()) {
            val cpuBase = "/sys/devices/system/cpu"
            val cpuList = privilegeBackend.listFiles(cpuBase).getOrDefault(emptyList())
            val cpuDirs = cpuList.filter { it.matches(Regex("cpu[0-9]+")) }
                .sortedBy { it.removePrefix("cpu").toIntOrNull() ?: 0 }

            val seenCores = mutableSetOf<Int>()
            for (cpuDir in cpuDirs) {
                val coreIndex = cpuDir.removePrefix("cpu").toIntOrNull() ?: continue
                if (seenCores.contains(coreIndex)) continue

                val cpufreqPath = "$cpuBase/$cpuDir/cpufreq"
                if (privilegeBackend.exists(cpufreqPath)) {
                    val raw = readPolicyData(cpufreqPath, coreIndex)
                    if (raw != null) {
                        seenCores.addAll(raw.coreIds)
                        discoveredPolicies.add(raw)
                    }
                }
            }
        }

        if (discoveredPolicies.isEmpty()) {
            return emptyList()
        }

        val sortedByMaxFreq = discoveredPolicies.sortedWith(
            compareBy<RawPolicy> { it.maxFreqKHz }.thenBy { it.clusterId }
        )

        val clusterTypes = assignClusterTypes(sortedByMaxFreq.size)

        val result = mutableListOf<CpuClusterInfo>()
        for (i in sortedByMaxFreq.indices) {
            val raw = sortedByMaxFreq[i]
            val type = clusterTypes.getOrElse(i) { ClusterType.UNKNOWN }
            result.add(
                CpuClusterInfo(
                    clusterId = raw.clusterId,
                    policyPath = raw.policyPath,
                    coreIds = raw.coreIds.sorted(),
                    type = type,
                    availableFrequenciesKHz = raw.availableFrequenciesKHz,
                    minFreqKHz = raw.minFreqKHz,
                    maxFreqKHz = raw.maxFreqKHz
                )
            )
        }

        return result.sortedBy { it.clusterId }
    }

    suspend fun probeGpu(): GpuInfo? {
        val kgslPath = "/sys/class/kgsl/kgsl-3d0"
        if (privilegeBackend.exists(kgslPath)) {
            val rawFrequencies = privilegeBackend.readFile("$kgslPath/gpu_available_frequencies")
                .recoverCatching {
                    privilegeBackend.readFile("$kgslPath/devfreq/available_frequencies").getOrThrow()
                }.getOrDefault("")

            val frequencies = parseFrequencies(rawFrequencies)
            val currentGovernor = privilegeBackend.readFile("$kgslPath/devfreq/governor").getOrDefault("").trim()
            val availableGovernors = privilegeBackend.readFile("$kgslPath/devfreq/available_governors")
                .getOrDefault("")
                .split("\\s+".toRegex())
                .filter { it.isNotBlank() }

            return GpuInfo(
                vendor = GpuVendor.ADRENO,
                basePath = kgslPath,
                availableFrequenciesKHz = frequencies,
                currentGovernor = currentGovernor,
                availableGovernors = availableGovernors
            )
        }

        val devfreqBase = "/sys/class/devfreq"
        val devfreqNodes = privilegeBackend.listFiles(devfreqBase).getOrDefault(emptyList())
        val gpuDevfreqNode = devfreqNodes.firstOrNull { node ->
            val lower = node.lowercase()
            lower.contains("mali") || lower.contains("gpu") || lower.contains("gpufreq") || lower.contains("kgsl")
        }

        if (gpuDevfreqNode != null) {
            val targetPath = "$devfreqBase/$gpuDevfreqNode"
            val rawFrequencies = privilegeBackend.readFile("$targetPath/available_frequencies").getOrDefault("")
            val frequencies = parseFrequencies(rawFrequencies)
            val currentGovernor = privilegeBackend.readFile("$targetPath/governor").getOrDefault("").trim()
            val availableGovernors = privilegeBackend.readFile("$targetPath/available_governors")
                .getOrDefault("")
                .split("\\s+".toRegex())
                .filter { it.isNotBlank() }

            val vendor = if (gpuDevfreqNode.lowercase().contains("mali")) {
                GpuVendor.MALI
            } else {
                GpuVendor.GENERIC_DEVFREQ
            }

            return GpuInfo(
                vendor = vendor,
                basePath = targetPath,
                availableFrequenciesKHz = frequencies,
                currentGovernor = currentGovernor,
                availableGovernors = availableGovernors
            )
        }

        return null
    }

    private suspend fun readPolicyData(policyPath: String, defaultId: Int): RawPolicy? {
        val affected = privilegeBackend.readFile("$policyPath/affected_cpus")
            .recoverCatching {
                privilegeBackend.readFile("$policyPath/related_cpus").getOrThrow()
            }.getOrDefault("")

        val coreIds = affected.split("\\s+".toRegex())
            .mapNotNull { it.toIntOrNull() }
            .ifEmpty { listOf(defaultId) }

        val rawFrequencies = privilegeBackend.readFile("$policyPath/scaling_available_frequencies").getOrDefault("")
        var frequencies = parseFrequencies(rawFrequencies)

        val minFreqKHz: Long
        val maxFreqKHz: Long

        if (frequencies.isNotEmpty()) {
            minFreqKHz = frequencies.first()
            maxFreqKHz = frequencies.last()
        } else {
            minFreqKHz = privilegeBackend.readFile("$policyPath/cpuinfo_min_freq").getOrDefault("0").trim().toLongOrNull() ?: 0L
            maxFreqKHz = privilegeBackend.readFile("$policyPath/cpuinfo_max_freq").getOrDefault("0").trim().toLongOrNull() ?: 0L
            if (minFreqKHz > 0 && maxFreqKHz > 0) {
                frequencies = if (minFreqKHz == maxFreqKHz) listOf(minFreqKHz) else listOf(minFreqKHz, maxFreqKHz)
            }
        }

        return RawPolicy(
            clusterId = defaultId,
            policyPath = policyPath,
            coreIds = coreIds,
            availableFrequenciesKHz = frequencies,
            minFreqKHz = minFreqKHz,
            maxFreqKHz = maxFreqKHz
        )
    }

    private fun parseFrequencies(raw: String): List<Long> {
        return raw.split("\\s+".toRegex())
            .mapNotNull { it.trim().toLongOrNull() }
            .map { freq ->
                if (freq > 10_000_000L) freq / 1000L else freq
            }
            .filter { it > 0 }
            .distinct()
            .sorted()
    }

    private fun assignClusterTypes(clusterCount: Int): List<ClusterType> {
        return when (clusterCount) {
            1 -> listOf(ClusterType.BIG)
            2 -> listOf(ClusterType.LITTLE, ClusterType.BIG)
            3 -> listOf(ClusterType.LITTLE, ClusterType.MID, ClusterType.PRIME)
            4 -> listOf(ClusterType.LITTLE, ClusterType.MID, ClusterType.BIG, ClusterType.PRIME)
            else -> List(clusterCount) { ClusterType.UNKNOWN }
        }
    }

    private data class RawPolicy(
        val clusterId: Int,
        val policyPath: String,
        val coreIds: List<Int>,
        val availableFrequenciesKHz: List<Long>,
        val minFreqKHz: Long,
        val maxFreqKHz: Long
    )
}
