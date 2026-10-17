package com.mahin.hytune.domain.cpu

import com.mahin.hytune.core.privilege.PrivilegeBackend
import com.mahin.hytune.domain.cpu.model.CpuClusterPolicy
import com.mahin.hytune.domain.cpu.model.CpuCoreState
import com.mahin.hytune.domain.probe.HardwareProbe
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class CpuControlEngine @Inject constructor(
    private val privilegeBackend: PrivilegeBackend,
    private val hardwareProbe: HardwareProbe,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun getClusterPolicies(): Result<List<CpuClusterPolicy>> = withContext(ioDispatcher) {
        runCatching {
            val clusters = hardwareProbe.probeCpuClusters()
            clusters.map { cluster ->
                val path = cluster.policyPath
                val availableGovs = readStringList("$path/scaling_available_governors")
                val currentGov = privilegeBackend.readFile("$path/scaling_governor")
                    .getOrNull()?.trim().orEmpty()

                val minFreq = privilegeBackend.readFile("$path/scaling_min_freq")
                    .getOrNull()?.trim()?.toLongOrNull() ?: cluster.minFreqKHz
                val maxFreq = privilegeBackend.readFile("$path/scaling_max_freq")
                    .getOrNull()?.trim()?.toLongOrNull() ?: cluster.maxFreqKHz
                val curFreq = privilegeBackend.readFile("$path/scaling_cur_freq")
                    .getOrNull()?.trim()?.toLongOrNull()
                    ?: readFirstCoreCurFreq(cluster.coreIds)
                    ?: minFreq

                val tunables = readTunablesForGovernor(path, currentGov)

                CpuClusterPolicy(
                    clusterId = cluster.clusterId,
                    policyPath = path,
                    affectedCores = cluster.coreIds,
                    type = cluster.type,
                    availableGovernors = availableGovs,
                    currentGovernor = currentGov,
                    availableFrequenciesKHz = cluster.availableFrequenciesKHz,
                    minFreqKHz = minFreq,
                    maxFreqKHz = maxFreq,
                    curFreqKHz = curFreq,
                    governorTunables = tunables
                )
            }
        }
    }

    suspend fun getCoreStates(): Result<List<CpuCoreState>> = withContext(ioDispatcher) {
        runCatching {
            val cpuBase = "/sys/devices/system/cpu"
            val cpuList = privilegeBackend.listFiles(cpuBase).getOrDefault(emptyList())
            val coreDirs = cpuList.filter { it.matches(Regex("cpu[0-9]+")) }
                .sortedBy { it.removePrefix("cpu").toIntOrNull() ?: 0 }

            val states = mutableListOf<CpuCoreState>()
            for (coreDir in coreDirs) {
                val coreId = coreDir.removePrefix("cpu").toIntOrNull() ?: continue
                val onlinePath = "$cpuBase/$coreDir/online"

                if (coreId == 0) {
                    val hasOnlineNode = privilegeBackend.exists(onlinePath)
                    val isOnline = if (hasOnlineNode) {
                        privilegeBackend.readFile(onlinePath).getOrNull()?.trim() == "1"
                    } else {
                        true
                    }
                    states.add(
                        CpuCoreState(
                            coreId = 0,
                            isOnline = isOnline,
                            isConfigurable = false
                        )
                    )
                } else {
                    val exists = privilegeBackend.exists(onlinePath)
                    if (exists) {
                        val onlineRaw = privilegeBackend.readFile(onlinePath).getOrNull()?.trim()
                        val isOnline = onlineRaw == "1"
                        states.add(
                            CpuCoreState(
                                coreId = coreId,
                                isOnline = isOnline,
                                isConfigurable = true
                            )
                        )
                    } else {
                        states.add(
                            CpuCoreState(
                                coreId = coreId,
                                isOnline = true,
                                isConfigurable = false
                            )
                        )
                    }
                }
            }
            states
        }
    }

    suspend fun setGovernor(policyPath: String, governor: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val availableGovs = readStringList("$policyPath/scaling_available_governors")
            if (availableGovs.isNotEmpty() && !availableGovs.contains(governor)) {
                throw IllegalArgumentException(
                    "Unsupported governor '$governor' for policy at $policyPath. Available: $availableGovs"
                )
            }

            val writeResult = privilegeBackend.writeFile("$policyPath/scaling_governor", governor)
            if (writeResult.isFailure) {
                throw writeResult.exceptionOrNull()
                    ?: IllegalStateException("Failed to write governor to $policyPath/scaling_governor")
            }

            val readBack = privilegeBackend.readFile("$policyPath/scaling_governor").getOrNull()?.trim()
            if (readBack != governor) {
                throw IllegalStateException(
                    "Governor write verification failed at $policyPath: expected '$governor', got '$readBack'"
                )
            }
        }
    }

    suspend fun setGovernor(clusterId: Int, governor: String): Result<Unit> = withContext(ioDispatcher) {
        val policy = findPolicyByClusterId(clusterId).getOrElse { return@withContext Result.failure(it) }
        setGovernor(policy.policyPath, governor)
    }

    suspend fun setFrequencies(
        policyPath: String,
        minFreqKHz: Long,
        maxFreqKHz: Long
    ): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val availableFreqs = readLongList("$policyPath/scaling_available_frequencies")
            val hwMin = availableFreqs.minOrNull()
                ?: privilegeBackend.readFile("$policyPath/cpuinfo_min_freq").getOrNull()?.trim()?.toLongOrNull()
                ?: 0L
            val hwMax = availableFreqs.maxOrNull()
                ?: privilegeBackend.readFile("$policyPath/cpuinfo_max_freq").getOrNull()?.trim()?.toLongOrNull()
                ?: Long.MAX_VALUE

            var targetMin = minFreqKHz.coerceIn(hwMin, hwMax)
            var targetMax = maxFreqKHz.coerceIn(hwMin, hwMax)

            if (targetMin > targetMax) {
                targetMin = targetMax
            }

            if (availableFreqs.isNotEmpty()) {
                targetMin = snapToAvailableFrequency(targetMin, availableFreqs)
                targetMax = snapToAvailableFrequency(targetMax, availableFreqs)
                if (targetMin > targetMax) {
                    targetMin = targetMax
                }
            }

            val currentMax = privilegeBackend.readFile("$policyPath/scaling_max_freq")
                .getOrNull()?.trim()?.toLongOrNull() ?: hwMax

            if (targetMin > currentMax) {
                val writeMaxResult = privilegeBackend.writeFile("$policyPath/scaling_max_freq", targetMax.toString())
                if (writeMaxResult.isFailure) {
                    throw writeMaxResult.exceptionOrNull()
                        ?: IllegalStateException("Failed to write scaling_max_freq to $policyPath")
                }
                val writeMinResult = privilegeBackend.writeFile("$policyPath/scaling_min_freq", targetMin.toString())
                if (writeMinResult.isFailure) {
                    throw writeMinResult.exceptionOrNull()
                        ?: IllegalStateException("Failed to write scaling_min_freq to $policyPath")
                }
            } else {
                val writeMinResult = privilegeBackend.writeFile("$policyPath/scaling_min_freq", targetMin.toString())
                if (writeMinResult.isFailure) {
                    throw writeMinResult.exceptionOrNull()
                        ?: IllegalStateException("Failed to write scaling_min_freq to $policyPath")
                }
                val writeMaxResult = privilegeBackend.writeFile("$policyPath/scaling_max_freq", targetMax.toString())
                if (writeMaxResult.isFailure) {
                    throw writeMaxResult.exceptionOrNull()
                        ?: IllegalStateException("Failed to write scaling_max_freq to $policyPath")
                }
            }

            val verifyMin = privilegeBackend.readFile("$policyPath/scaling_min_freq")
                .getOrNull()?.trim()?.toLongOrNull()
            val verifyMax = privilegeBackend.readFile("$policyPath/scaling_max_freq")
                .getOrNull()?.trim()?.toLongOrNull()

            if (verifyMin != targetMin || verifyMax != targetMax) {
                throw IllegalStateException(
                    "Frequency verification failed at $policyPath: expected ($targetMin, $targetMax), got ($verifyMin, $verifyMax)"
                )
            }
        }
    }

    suspend fun setFrequencies(
        clusterId: Int,
        minFreqKHz: Long,
        maxFreqKHz: Long
    ): Result<Unit> = withContext(ioDispatcher) {
        val policy = findPolicyByClusterId(clusterId).getOrElse { return@withContext Result.failure(it) }
        setFrequencies(policy.policyPath, minFreqKHz, maxFreqKHz)
    }

    suspend fun setCoreOnline(coreId: Int, online: Boolean): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            if (coreId == 0 && !online) {
                throw IllegalArgumentException("Core 0 is the primary boot core and cannot be disabled")
            }

            if (!online) {
                val coreStates = getCoreStates().getOrDefault(emptyList())
                val onlineCount = coreStates.count { it.isOnline }
                if (onlineCount <= 1) {
                    throw IllegalStateException("Cannot disable Core $coreId: at least one CPU core must remain online")
                }
            }

            val onlinePath = "/sys/devices/system/cpu/cpu$coreId/online"
            if (!privilegeBackend.exists(onlinePath)) {
                throw UnsupportedOperationException("Core $coreId does not support hotplug (/online sysfs node missing)")
            }

            val targetValue = if (online) "1" else "0"
            val writeResult = privilegeBackend.writeFile(onlinePath, targetValue)
            if (writeResult.isFailure) {
                throw writeResult.exceptionOrNull()
                    ?: IllegalStateException("Failed to write $targetValue to $onlinePath")
            }

            val readBack = privilegeBackend.readFile(onlinePath).getOrNull()?.trim()
            if (readBack != targetValue) {
                throw IllegalStateException("Core hotplug verification failed at $onlinePath: expected $targetValue, got $readBack")
            }
        }
    }

    suspend fun getGovernorTunables(policyPath: String): Result<Map<String, String>> = withContext(ioDispatcher) {
        runCatching {
            val currentGov = privilegeBackend.readFile("$policyPath/scaling_governor").getOrNull()?.trim().orEmpty()
            readTunablesForGovernor(policyPath, currentGov)
        }
    }

    suspend fun setGovernorTunable(
        policyPath: String,
        tunableName: String,
        value: String
    ): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val currentGov = privilegeBackend.readFile("$policyPath/scaling_governor").getOrNull()?.trim().orEmpty()
            val policyTunablePath = "$policyPath/$currentGov/$tunableName"
            val globalTunablePath = "/sys/devices/system/cpu/cpufreq/$currentGov/$tunableName"

            val targetPath = when {
                privilegeBackend.exists(policyTunablePath) -> policyTunablePath
                privilegeBackend.exists(globalTunablePath) -> globalTunablePath
                else -> policyTunablePath
            }

            val writeResult = privilegeBackend.writeFile(targetPath, value)
            if (writeResult.isFailure) {
                throw writeResult.exceptionOrNull()
                    ?: IllegalStateException("Failed to write $value to $targetPath")
            }

            val readBack = privilegeBackend.readFile(targetPath).getOrNull()?.trim()
            if (readBack != value) {
                throw IllegalStateException("Governor tunable verification failed at $targetPath: expected $value, got $readBack")
            }
        }
    }

    private suspend fun findPolicyByClusterId(clusterId: Int): Result<CpuClusterPolicy> {
        val policies = getClusterPolicies().getOrElse { return Result.failure(it) }
        val policy = policies.find { it.clusterId == clusterId }
            ?: return Result.failure(IllegalArgumentException("No CPU cluster found with clusterId=$clusterId"))
        return Result.success(policy)
    }

    private suspend fun readTunablesForGovernor(policyPath: String, governor: String): Map<String, String> {
        if (governor.isEmpty()) return emptyMap()

        val policyTunableDir = "$policyPath/$governor"
        val globalTunableDir = "/sys/devices/system/cpu/cpufreq/$governor"

        val dirToRead = when {
            privilegeBackend.exists(policyTunableDir) -> policyTunableDir
            privilegeBackend.exists(globalTunableDir) -> globalTunableDir
            else -> return emptyMap()
        }

        val files = privilegeBackend.listFiles(dirToRead).getOrDefault(emptyList())
        val tunables = mutableMapOf<String, String>()

        for (file in files) {
            val content = privilegeBackend.readFile("$dirToRead/$file").getOrNull()?.trim()
            if (content != null && content.isNotEmpty()) {
                tunables[file] = content
            }
        }
        return tunables
    }

    private suspend fun readFirstCoreCurFreq(coreIds: List<Int>): Long? {
        for (coreId in coreIds) {
            val freq = privilegeBackend.readFile("/sys/devices/system/cpu/cpu$coreId/cpufreq/scaling_cur_freq")
                .getOrNull()?.trim()?.toLongOrNull()
            if (freq != null && freq > 0L) {
                return freq
            }
        }
        return null
    }

    private suspend fun readStringList(path: String): List<String> {
        val raw = privilegeBackend.readFile(path).getOrNull()?.trim().orEmpty()
        return if (raw.isNotEmpty()) {
            raw.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        } else {
            emptyList()
        }
    }

    private suspend fun readLongList(path: String): List<Long> {
        val raw = privilegeBackend.readFile(path).getOrNull()?.trim().orEmpty()
        return if (raw.isNotEmpty()) {
            raw.split("\\s+".toRegex()).mapNotNull { it.toLongOrNull() }.sorted()
        } else {
            emptyList()
        }
    }

    private fun snapToAvailableFrequency(target: Long, available: List<Long>): Long {
        if (available.isEmpty()) return target
        return available.minByOrNull { abs(it - target) } ?: target
    }
}
