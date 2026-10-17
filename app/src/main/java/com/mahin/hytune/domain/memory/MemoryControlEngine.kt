package com.mahin.hytune.domain.memory

import com.mahin.hytune.core.privilege.PrivilegeBackend
import com.mahin.hytune.domain.memory.model.DropCacheType
import com.mahin.hytune.domain.memory.model.VmTunables
import com.mahin.hytune.domain.memory.model.ZramInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryControlEngine @Inject constructor(
    private val privilegeBackend: PrivilegeBackend,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun getVmTunables(): Result<VmTunables> = withContext(ioDispatcher) {
        runCatching {
            val swappiness = privilegeBackend.readFile("/proc/sys/vm/swappiness")
                .getOrNull()?.trim()?.toIntOrNull() ?: 60

            val vfsCachePressure = privilegeBackend.readFile("/proc/sys/vm/vfs_cache_pressure")
                .getOrNull()?.trim()?.toIntOrNull() ?: 100

            val dirtyRatio = privilegeBackend.readFile("/proc/sys/vm/dirty_ratio")
                .getOrNull()?.trim()?.toIntOrNull() ?: 20

            val dirtyBackgroundRatio = privilegeBackend.readFile("/proc/sys/vm/dirty_background_ratio")
                .getOrNull()?.trim()?.toIntOrNull() ?: 10

            val extraFreeKbytes = privilegeBackend.readFile("/proc/sys/vm/extra_free_kbytes")
                .getOrNull()?.trim()?.toLongOrNull()

            VmTunables(
                swappiness = swappiness,
                vfsCachePressure = vfsCachePressure,
                dirtyRatio = dirtyRatio,
                dirtyBackgroundRatio = dirtyBackgroundRatio,
                extraFreeKbytes = extraFreeKbytes
            )
        }
    }

    suspend fun setSwappiness(value: Int): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val clamped = value.coerceIn(0, 200)
            privilegeBackend.writeFile("/proc/sys/vm/swappiness", clamped.toString()).getOrThrow()
        }
    }

    suspend fun setVfsCachePressure(value: Int): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val clamped = value.coerceIn(0, 500)
            privilegeBackend.writeFile("/proc/sys/vm/vfs_cache_pressure", clamped.toString()).getOrThrow()
        }
    }

    suspend fun setDirtyRatios(dirtyRatio: Int, dirtyBackgroundRatio: Int): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val clampedDirty = dirtyRatio.coerceIn(1, 100)
            val clampedBg = dirtyBackgroundRatio.coerceIn(1, clampedDirty)
            privilegeBackend.writeFile("/proc/sys/vm/dirty_background_ratio", clampedBg.toString()).getOrThrow()
            privilegeBackend.writeFile("/proc/sys/vm/dirty_ratio", clampedDirty.toString()).getOrThrow()
        }
    }

    suspend fun getZramInfo(): Result<ZramInfo?> = withContext(ioDispatcher) {
        runCatching {
            val zramSysfs = "/sys/block/zram0"
            if (!privilegeBackend.exists(zramSysfs)) {
                return@runCatching null
            }

            val diskSizeBytes = privilegeBackend.readFile("$zramSysfs/disksize")
                .getOrNull()?.trim()?.toLongOrNull() ?: 0L

            val (activeAlgo, availableAlgos) = parseAlgorithms(
                privilegeBackend.readFile("$zramSysfs/comp_algorithm").getOrNull()
            )

            val (origSize, comprSize, memUsed) = readZramStats(zramSysfs)

            val maxStreams = privilegeBackend.readFile("$zramSysfs/max_comp_streams")
                .getOrNull()?.trim()?.toIntOrNull() ?: 1

            val swapsContent = privilegeBackend.readFile("/proc/swaps").getOrNull().orEmpty()
            val isEnabled = diskSizeBytes > 0L && swapsContent.contains("zram0")

            ZramInfo(
                enabled = isEnabled,
                disksizeBytes = diskSizeBytes,
                origSizeBytes = origSize,
                comprSizeBytes = comprSize,
                memUsedTotalBytes = memUsed,
                activeAlgorithm = activeAlgo,
                availableAlgorithms = availableAlgos,
                maxCompStreams = maxStreams
            )
        }
    }

    suspend fun resizeZram(newSizeBytes: Long, algorithm: String? = null): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            require(newSizeBytes >= 0L) { "ZRAM size must be non-negative: $newSizeBytes" }

            val zramSysfs = "/sys/block/zram0"
            if (!privilegeBackend.exists(zramSysfs)) {
                throw IllegalStateException("ZRAM sysfs node not found at $zramSysfs")
            }

            val devicePath = if (privilegeBackend.exists("/dev/block/zram0")) {
                "/dev/block/zram0"
            } else {
                "/dev/zram0"
            }

            val swaps = privilegeBackend.readFile("/proc/swaps").getOrNull().orEmpty()
            if (swaps.contains("zram0")) {
                val swapoffRes = privilegeBackend.exec("swapoff $devicePath")
                if (!swapoffRes.isSuccess) {
                    throw IllegalStateException("swapoff failed for $devicePath: ${swapoffRes.errorOutput}")
                }
            }

            privilegeBackend.writeFile("$zramSysfs/reset", "1").getOrThrow()

            if (!algorithm.isNullOrBlank()) {
                privilegeBackend.writeFile("$zramSysfs/comp_algorithm", algorithm.trim()).getOrThrow()
            }

            if (newSizeBytes > 0L) {
                privilegeBackend.writeFile("$zramSysfs/disksize", newSizeBytes.toString()).getOrThrow()

                val mkswapRes = privilegeBackend.exec("mkswap $devicePath")
                if (!mkswapRes.isSuccess) {
                    throw IllegalStateException("mkswap failed for $devicePath: ${mkswapRes.errorOutput}")
                }

                val swaponRes = privilegeBackend.exec("swapon $devicePath -p 32758")
                if (!swaponRes.isSuccess) {
                    throw IllegalStateException("swapon failed for $devicePath: ${swaponRes.errorOutput}")
                }
            }
        }
    }

    suspend fun dropCaches(type: DropCacheType): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            privilegeBackend.exec("sync")
            privilegeBackend.writeFile("/proc/sys/vm/drop_caches", type.mode.toString()).getOrThrow()
        }
    }

    suspend fun compactZram(): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val compactPath = "/sys/block/zram0/compact"
            if (privilegeBackend.exists(compactPath)) {
                privilegeBackend.writeFile(compactPath, "1").getOrThrow()
            }
        }
    }

    private suspend fun readZramStats(zramSysfs: String): Triple<Long, Long, Long> {
        val mmStatContent = privilegeBackend.readFile("$zramSysfs/mm_stat").getOrNull()
        if (!mmStatContent.isNullOrBlank()) {
            val tokens = mmStatContent.trim().split(Regex("\\s+"))
            if (tokens.size >= 3) {
                val orig = tokens[0].toLongOrNull() ?: 0L
                val compr = tokens[1].toLongOrNull() ?: 0L
                val memUsed = tokens[2].toLongOrNull() ?: 0L
                return Triple(orig, compr, memUsed)
            }
        }

        val orig = privilegeBackend.readFile("$zramSysfs/orig_data_size")
            .getOrNull()?.trim()?.toLongOrNull() ?: 0L
        val compr = privilegeBackend.readFile("$zramSysfs/compr_data_size")
            .getOrNull()?.trim()?.toLongOrNull() ?: 0L
        val memUsed = privilegeBackend.readFile("$zramSysfs/mem_used_total")
            .getOrNull()?.trim()?.toLongOrNull() ?: 0L

        return Triple(orig, compr, memUsed)
    }

    private fun parseAlgorithms(raw: String?): Pair<String, List<String>> {
        if (raw.isNullOrBlank()) {
            return Pair("lz4", listOf("lz4"))
        }

        val tokens = raw.trim().split(Regex("\\s+"))
        val available = tokens.map { it.removeSurrounding("[", "]") }.distinct()
        val active = tokens.firstOrNull { it.startsWith("[") && it.endsWith("]") }
            ?.removeSurrounding("[", "]")
            ?: available.firstOrNull()
            ?: "lz4"

        return Pair(active, available)
    }
}
