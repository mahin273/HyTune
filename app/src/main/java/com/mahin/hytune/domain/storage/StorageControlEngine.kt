package com.mahin.hytune.domain.storage

import com.mahin.hytune.core.privilege.PrivilegeBackend
import com.mahin.hytune.domain.storage.model.BlockDeviceQueue
import com.mahin.hytune.domain.storage.model.FsTrimResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageControlEngine @Inject constructor(
    private val privilegeBackend: PrivilegeBackend,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun getBlockDevices(): Result<List<BlockDeviceQueue>> = withContext(ioDispatcher) {
        runCatching {
            val basePath = "/sys/block"
            if (!privilegeBackend.exists(basePath)) {
                return@runCatching emptyList()
            }

            val entries = privilegeBackend.listFiles(basePath).getOrElse { emptyList() }
            val devices = mutableListOf<BlockDeviceQueue>()

            for (entry in entries) {
                val name = File(entry).name
                // Skip virtual and ramdisk devices
                if (name.startsWith("loop") || name.startsWith("ram") || name.startsWith("zram")) {
                    continue
                }

                val queueDir = "$basePath/$name/queue"
                if (!privilegeBackend.exists(queueDir)) {
                    continue
                }

                val schedulerPath = "$queueDir/scheduler"
                val (activeSched, availableScheds) = if (privilegeBackend.exists(schedulerPath)) {
                    parseSchedulers(privilegeBackend.readFile(schedulerPath).getOrNull())
                } else {
                    Pair("none", listOf("none"))
                }

                val readAheadKb = privilegeBackend.readFile("$queueDir/read_ahead_kb")
                    .getOrNull()?.trim()?.toIntOrNull() ?: 128

                val isRotational = privilegeBackend.readFile("$queueDir/rotational")
                    .getOrNull()?.trim() == "1"

                val sizeSectors = privilegeBackend.readFile("$basePath/$name/size")
                    .getOrNull()?.trim()?.toLongOrNull() ?: 0L
                val sizeBytes = sizeSectors * 512L

                devices.add(
                    BlockDeviceQueue(
                        name = name,
                        isRotational = isRotational,
                        currentScheduler = activeSched,
                        availableSchedulers = availableScheds,
                        readAheadKb = readAheadKb,
                        sizeBytes = sizeBytes
                    )
                )
            }

            // Sort devices: prioritize physical storage (sda, sdb, mmcblk0, nvme0n1) over virtual dm devices
            devices.sortedWith(
                compareBy<BlockDeviceQueue> {
                    when {
                        it.name.startsWith("sd") -> 0
                        it.name.startsWith("mmcblk") -> 1
                        it.name.startsWith("nvme") -> 2
                        it.name.startsWith("dm-") -> 4
                        else -> 3
                    }
                }.thenBy { it.name }
            )
        }
    }

    suspend fun setScheduler(deviceName: String, scheduler: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val schedulerPath = "/sys/block/$deviceName/queue/scheduler"
            if (!privilegeBackend.exists(schedulerPath)) {
                throw NoSuchFileException(File(schedulerPath), reason = "Queue scheduler not found for $deviceName")
            }

            val currentContent = privilegeBackend.readFile(schedulerPath).getOrNull()
            val (_, available) = parseSchedulers(currentContent)
            if (available.isNotEmpty() && !available.contains(scheduler)) {
                throw IllegalArgumentException("Scheduler '$scheduler' not available for $deviceName. Available: $available")
            }

            privilegeBackend.writeFile(schedulerPath, scheduler.trim()).getOrThrow()
        }
    }

    suspend fun setReadAhead(deviceName: String, readAheadKb: Int): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val clamped = readAheadKb.coerceIn(32, 4096)
            val path = "/sys/block/$deviceName/queue/read_ahead_kb"
            if (!privilegeBackend.exists(path)) {
                throw NoSuchFileException(File(path), reason = "read_ahead_kb not found for $deviceName")
            }

            privilegeBackend.writeFile(path, clamped.toString()).getOrThrow()
        }
    }

    suspend fun setGlobalReadAhead(readAheadKb: Int): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val devices = getBlockDevices().getOrThrow()
            for (dev in devices) {
                setReadAhead(dev.name, readAheadKb)
            }
        }
    }

    suspend fun runFsTrim(mountPoint: String = "/data"): Result<FsTrimResult> = withContext(ioDispatcher) {
        runCatching {
            val cmd = "fstrim -v $mountPoint"
            val res = privilegeBackend.exec(cmd)
            val output = if (res.isSuccess) res.output else res.errorOutput

            val bytes = parseTrimmedBytes(output)
            FsTrimResult(
                mountPoint = mountPoint,
                trimmedBytes = bytes,
                rawOutput = output,
                isSuccess = res.isSuccess
            )
        }
    }

    private fun parseTrimmedBytes(output: String): Long {
        val regex = Regex("(\\d+)\\s+bytes trimmed", RegexOption.IGNORE_CASE)
        val match = regex.find(output)
        return match?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    }

    private fun parseSchedulers(raw: String?): Pair<String, List<String>> {
        if (raw.isNullOrBlank()) {
            return Pair("none", listOf("none"))
        }

        val tokens = raw.trim().split(Regex("\\s+"))
        val available = tokens.map { it.removeSurrounding("[", "]") }.distinct()
        val active = tokens.firstOrNull { it.startsWith("[") && it.endsWith("]") }
            ?.removeSurrounding("[", "]")
            ?: available.firstOrNull()
            ?: "none"

        return Pair(active, available)
    }
}
