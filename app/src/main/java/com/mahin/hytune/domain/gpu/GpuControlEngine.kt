package com.mahin.hytune.domain.gpu

import com.mahin.hytune.core.privilege.PrivilegeBackend
import com.mahin.hytune.domain.gpu.model.GpuPolicy
import com.mahin.hytune.domain.gpu.model.GpuPowerLevelInfo
import com.mahin.hytune.domain.probe.HardwareProbe
import com.mahin.hytune.domain.probe.model.GpuVendor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class GpuControlEngine @Inject constructor(
    private val privilegeBackend: PrivilegeBackend,
    private val hardwareProbe: HardwareProbe,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun getGpuPolicy(): Result<GpuPolicy?> = withContext(ioDispatcher) {
        runCatching {
            val gpuInfo = hardwareProbe.probeGpu() ?: return@runCatching null
            val basePath = gpuInfo.basePath

            val curFreq = readCurrentFrequency(basePath, gpuInfo.vendor)
                ?: gpuInfo.availableFrequenciesKHz.firstOrNull()
                ?: 0L

            val (minFreq, maxFreq) = readFrequencyBoundaries(basePath, gpuInfo.availableFrequenciesKHz)

            val powerLevels = probePowerLevels(basePath)
            val idleTimer = privilegeBackend.readFile("$basePath/idle_timer").getOrNull()?.trim()?.toIntOrNull()

            GpuPolicy(
                vendor = gpuInfo.vendor,
                basePath = basePath,
                availableFrequenciesKHz = gpuInfo.availableFrequenciesKHz,
                minFreqKHz = minFreq,
                maxFreqKHz = maxFreq,
                curFreqKHz = curFreq,
                currentGovernor = gpuInfo.currentGovernor,
                availableGovernors = gpuInfo.availableGovernors,
                powerLevelInfo = powerLevels,
                idleTimerMs = idleTimer
            )
        }
    }

    suspend fun setGovernor(governor: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val policy = getGpuPolicy().getOrNull()
                ?: throw IllegalStateException("GPU not detected on this system")

            if (policy.availableGovernors.isNotEmpty() && !policy.availableGovernors.contains(governor)) {
                throw IllegalArgumentException(
                    "Unsupported GPU governor '$governor'. Available: ${policy.availableGovernors}"
                )
            }

            val targetPath = resolveGovernorPath(policy.basePath)
                ?: throw UnsupportedOperationException("GPU governor node not writable at ${policy.basePath}")

            val writeResult = privilegeBackend.writeFile(targetPath, governor)
            if (writeResult.isFailure) {
                throw writeResult.exceptionOrNull()
                    ?: IllegalStateException("Failed to write GPU governor to $targetPath")
            }

            val readBack = privilegeBackend.readFile(targetPath).getOrNull()?.trim()
            if (readBack != governor) {
                throw IllegalStateException("GPU governor verification failed: expected '$governor', got '$readBack'")
            }
        }
    }

    suspend fun setFrequencies(minFreqKHz: Long, maxFreqKHz: Long): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val policy = getGpuPolicy().getOrNull()
                ?: throw IllegalStateException("GPU not detected on this system")

            val available = policy.availableFrequenciesKHz
            val hwMin = available.minOrNull() ?: 0L
            val hwMax = available.maxOrNull() ?: Long.MAX_VALUE

            var targetMin = minFreqKHz.coerceIn(hwMin, hwMax)
            var targetMax = maxFreqKHz.coerceIn(hwMin, hwMax)
            if (targetMin > targetMax) {
                targetMin = targetMax
            }

            if (available.isNotEmpty()) {
                targetMin = snapToAvailable(targetMin, available)
                targetMax = snapToAvailable(targetMax, available)
                if (targetMin > targetMax) {
                    targetMin = targetMax
                }
            }

            val minNode = resolveMinFreqNode(policy.basePath)
                ?: throw UnsupportedOperationException("GPU min_freq node not found at ${policy.basePath}")
            val maxNode = resolveMaxFreqNode(policy.basePath)
                ?: throw UnsupportedOperationException("GPU max_freq node not found at ${policy.basePath}")

            // Check if sysfs uses Hz, kHz, or MHz
            val (minWriteVal, maxWriteVal) = formatFrequencyForNode(minNode, targetMin, targetMax)

            val currentMax = readNodeFrequencyKHz(maxNode) ?: hwMax

            if (targetMin > currentMax) {
                // Raise max first, then raise min
                privilegeBackend.writeFile(maxNode, maxWriteVal).getOrThrow()
                privilegeBackend.writeFile(minNode, minWriteVal).getOrThrow()
            } else {
                // Lower min first, then adjust max
                privilegeBackend.writeFile(minNode, minWriteVal).getOrThrow()
                privilegeBackend.writeFile(maxNode, maxWriteVal).getOrThrow()
            }

            val verifiedMin = readNodeFrequencyKHz(minNode)
            val verifiedMax = readNodeFrequencyKHz(maxNode)

            if (verifiedMin != null && verifiedMax != null) {
                if (verifiedMin != targetMin || verifiedMax != targetMax) {
                    throw IllegalStateException(
                        "GPU frequency verification failed: expected ($targetMin, $targetMax), got ($verifiedMin, $verifiedMax)"
                    )
                }
            }
        }
    }

    suspend fun setPowerLevel(minPwrlevel: Int?, maxPwrlevel: Int?): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val policy = getGpuPolicy().getOrNull()
                ?: throw IllegalStateException("GPU not detected on this system")

            val basePath = policy.basePath
            if (minPwrlevel != null) {
                val minNode = "$basePath/min_pwrlevel"
                if (privilegeBackend.exists(minNode)) {
                    privilegeBackend.writeFile(minNode, minPwrlevel.toString()).getOrThrow()
                }
            }

            if (maxPwrlevel != null) {
                val maxNode = "$basePath/max_pwrlevel"
                if (privilegeBackend.exists(maxNode)) {
                    privilegeBackend.writeFile(maxNode, maxPwrlevel.toString()).getOrThrow()
                }
            }
        }
    }

    suspend fun setIdleTimer(idleTimerMs: Int): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val policy = getGpuPolicy().getOrNull()
                ?: throw IllegalStateException("GPU not detected on this system")

            val node = "${policy.basePath}/idle_timer"
            if (!privilegeBackend.exists(node)) {
                throw UnsupportedOperationException("idle_timer node not supported at ${policy.basePath}")
            }

            privilegeBackend.writeFile(node, idleTimerMs.toString()).getOrThrow()
            val readBack = privilegeBackend.readFile(node).getOrNull()?.trim()?.toIntOrNull()
            if (readBack != idleTimerMs) {
                throw IllegalStateException("idle_timer verification failed: expected $idleTimerMs, got $readBack")
            }
        }
    }

    private suspend fun readCurrentFrequency(basePath: String, vendor: GpuVendor): Long? {
        val candidates = if (vendor == GpuVendor.ADRENO) {
            listOf("$basePath/devfreq/cur_freq", "$basePath/gpuclk", "$basePath/cur_freq")
        } else {
            listOf("$basePath/cur_freq")
        }

        for (path in candidates) {
            val raw = privilegeBackend.readFile(path).getOrNull()?.trim()?.toLongOrNull()
            if (raw != null && raw > 0) {
                return normalizeFrequencyKHz(raw)
            }
        }
        return null
    }

    private suspend fun readFrequencyBoundaries(
        basePath: String,
        available: List<Long>
    ): Pair<Long, Long> {
        val minNode = resolveMinFreqNode(basePath)
        val maxNode = resolveMaxFreqNode(basePath)

        val min = minNode?.let { readNodeFrequencyKHz(it) } ?: available.minOrNull() ?: 0L
        val max = maxNode?.let { readNodeFrequencyKHz(it) } ?: available.maxOrNull() ?: 0L

        return Pair(min, max)
    }

    private suspend fun probePowerLevels(basePath: String): GpuPowerLevelInfo? {
        val numNode = "$basePath/num_pwrlevels"
        if (!privilegeBackend.exists(numNode)) return null

        val num = privilegeBackend.readFile(numNode).getOrNull()?.trim()?.toIntOrNull() ?: return null
        val min = privilegeBackend.readFile("$basePath/min_pwrlevel").getOrNull()?.trim()?.toIntOrNull() ?: 0
        val max = privilegeBackend.readFile("$basePath/max_pwrlevel").getOrNull()?.trim()?.toIntOrNull() ?: (num - 1)
        val defaultLevel = privilegeBackend.readFile("$basePath/default_pwrlevel").getOrNull()?.trim()?.toIntOrNull()

        return GpuPowerLevelInfo(
            numPwrlevels = num,
            minPwrlevel = min,
            maxPwrlevel = max,
            defaultPwrlevel = defaultLevel
        )
    }

    private suspend fun resolveGovernorPath(basePath: String): String? {
        val candidates = listOf("$basePath/devfreq/governor", "$basePath/governor")
        return candidates.firstOrNull { privilegeBackend.exists(it) }
    }

    private suspend fun resolveMinFreqNode(basePath: String): String? {
        val candidates = listOf(
            "$basePath/devfreq/min_freq",
            "$basePath/min_freq",
            "$basePath/gpu_min_clock_mhz",
            "$basePath/min_clock_mhz"
        )
        return candidates.firstOrNull { privilegeBackend.exists(it) }
    }

    private suspend fun resolveMaxFreqNode(basePath: String): String? {
        val candidates = listOf(
            "$basePath/devfreq/max_freq",
            "$basePath/max_freq",
            "$basePath/gpu_max_clock_mhz",
            "$basePath/max_clock_mhz"
        )
        return candidates.firstOrNull { privilegeBackend.exists(it) }
    }

    private suspend fun readNodeFrequencyKHz(path: String): Long? {
        val raw = privilegeBackend.readFile(path).getOrNull()?.trim()?.toLongOrNull() ?: return null
        return normalizeFrequencyKHz(raw, isMhz = path.endsWith("_mhz"))
    }

    private suspend fun formatFrequencyForNode(nodePath: String, minKHz: Long, maxKHz: Long): Pair<String, String> {
        val existing = privilegeBackend.readFile(nodePath).getOrNull()?.trim()?.toLongOrNull() ?: 0L
        return if (nodePath.endsWith("_mhz") || (existing > 0 && existing < 10_000L)) {
            Pair((minKHz / 1000L).toString(), (maxKHz / 1000L).toString())
        } else if (existing > 10_000_000L) {
            Pair((minKHz * 1000L).toString(), (maxKHz * 1000L).toString())
        } else {
            Pair(minKHz.toString(), maxKHz.toString())
        }
    }

    private fun normalizeFrequencyKHz(raw: Long, isMhz: Boolean = false): Long {
        return when {
            isMhz -> raw * 1000L
            raw > 10_000_000L -> raw / 1000L
            raw in 100L..5000L -> raw * 1000L
            else -> raw
        }
    }

    private fun snapToAvailable(target: Long, available: List<Long>): Long {
        if (available.isEmpty()) return target
        return available.minByOrNull { abs(it - target) } ?: target
    }
}
