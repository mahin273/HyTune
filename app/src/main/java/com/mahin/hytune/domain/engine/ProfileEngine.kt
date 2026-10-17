package com.mahin.hytune.domain.engine

import com.mahin.hytune.domain.cpu.CpuControlEngine
import com.mahin.hytune.domain.engine.model.ApplyProfileResult
import com.mahin.hytune.domain.engine.model.CpuClusterDiff
import com.mahin.hytune.domain.engine.model.GpuDiff
import com.mahin.hytune.domain.engine.model.HardwareStateSnapshot
import com.mahin.hytune.domain.engine.model.MemoryDiff
import com.mahin.hytune.domain.engine.model.ProfileDiff
import com.mahin.hytune.domain.engine.model.StorageDeviceDiff
import com.mahin.hytune.domain.gpu.GpuControlEngine
import com.mahin.hytune.domain.memory.MemoryControlEngine
import com.mahin.hytune.domain.memory.model.DropCacheType
import com.mahin.hytune.domain.profile.FrequencyTargetResolver
import com.mahin.hytune.domain.profile.ProfileRepository
import com.mahin.hytune.domain.profile.model.TuningProfile
import com.mahin.hytune.domain.storage.StorageControlEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileEngine @Inject constructor(
    private val cpuControlEngine: CpuControlEngine,
    private val gpuControlEngine: GpuControlEngine,
    private val memoryControlEngine: MemoryControlEngine,
    private val storageControlEngine: StorageControlEngine,
    private val profileRepository: ProfileRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun captureCurrentState(): Result<HardwareStateSnapshot> = withContext(ioDispatcher) {
        runCatching {
            val cpuPolicies = cpuControlEngine.getClusterPolicies().getOrDefault(emptyList())
            val coreStates = cpuControlEngine.getCoreStates().getOrDefault(emptyList())
            val gpuPolicy = gpuControlEngine.getGpuPolicy().getOrNull()
            val vmTunables = memoryControlEngine.getVmTunables().getOrNull()
            val storageDevices = storageControlEngine.getBlockDevices().getOrDefault(emptyList())

            HardwareStateSnapshot(
                timestampEpochMs = System.currentTimeMillis(),
                cpuGovernors = cpuPolicies.associate { it.clusterId to it.currentGovernor },
                cpuMinFreqsKHz = cpuPolicies.associate { it.clusterId to it.minFreqKHz },
                cpuMaxFreqsKHz = cpuPolicies.associate { it.clusterId to it.maxFreqKHz },
                cpuCoreStates = coreStates.associate { it.coreId to it.isOnline },
                gpuGovernor = gpuPolicy?.currentGovernor,
                gpuMinFreqKHz = gpuPolicy?.minFreqKHz,
                gpuMaxFreqKHz = gpuPolicy?.maxFreqKHz,
                swappiness = vmTunables?.swappiness,
                vfsCachePressure = vmTunables?.vfsCachePressure,
                storageSchedulers = storageDevices.associate { it.name to it.currentScheduler },
                storageReadAheadKb = storageDevices.associate { it.name to it.readAheadKb }
            )
        }
    }

    suspend fun computeDiff(profile: TuningProfile): Result<ProfileDiff> = withContext(ioDispatcher) {
        runCatching {
            val cpuPolicies = cpuControlEngine.getClusterPolicies().getOrDefault(emptyList())
            val cpuDiffs = mutableListOf<CpuClusterDiff>()

            for (policy in cpuPolicies) {
                val clusterConfig = profile.cpuClusters[policy.clusterId]
                val targetGov = clusterConfig?.governor
                val targetMin = clusterConfig?.minFreqTarget?.let {
                    FrequencyTargetResolver.resolve(it, policy.availableFrequenciesKHz)
                }
                val targetMax = clusterConfig?.maxFreqTarget?.let {
                    FrequencyTargetResolver.resolve(it, policy.availableFrequenciesKHz)
                }

                cpuDiffs.add(
                    CpuClusterDiff(
                        clusterId = policy.clusterId,
                        targetGovernor = targetGov,
                        currentGovernor = policy.currentGovernor,
                        targetMinFreqKHz = targetMin,
                        currentMinFreqKHz = policy.minFreqKHz,
                        targetMaxFreqKHz = targetMax,
                        currentMaxFreqKHz = policy.maxFreqKHz
                    )
                )
            }

            // GPU Diff
            val gpuPolicy = gpuControlEngine.getGpuPolicy().getOrNull()
            val gpuDiff = if (gpuPolicy != null && profile.gpu != null) {
                val targetMin = profile.gpu.minFreqTarget?.let {
                    FrequencyTargetResolver.resolve(it, gpuPolicy.availableFrequenciesKHz)
                }
                val targetMax = profile.gpu.maxFreqTarget?.let {
                    FrequencyTargetResolver.resolve(it, gpuPolicy.availableFrequenciesKHz)
                }
                GpuDiff(
                    targetGovernor = profile.gpu.governor,
                    currentGovernor = gpuPolicy.currentGovernor,
                    targetMinFreqKHz = targetMin,
                    currentMinFreqKHz = gpuPolicy.minFreqKHz,
                    targetMaxFreqKHz = targetMax,
                    currentMaxFreqKHz = gpuPolicy.maxFreqKHz
                )
            } else null

            // Memory Diff
            val vmTunables = memoryControlEngine.getVmTunables().getOrNull()
            val memoryDiff = if (profile.memory != null) {
                MemoryDiff(
                    targetSwappiness = profile.memory.swappiness,
                    currentSwappiness = vmTunables?.swappiness,
                    targetVfsCachePressure = profile.memory.vfsCachePressure,
                    currentVfsCachePressure = vmTunables?.vfsCachePressure,
                    dropCachesBeforeApply = profile.memory.dropCachesBeforeApply
                )
            } else null

            // Storage Diff
            val storageDevices = storageControlEngine.getBlockDevices().getOrDefault(emptyList())
            val storageDiffs = mutableListOf<StorageDeviceDiff>()
            val storageConfig = profile.storage

            if (storageConfig != null) {
                for (dev in storageDevices) {
                    storageDiffs.add(
                        StorageDeviceDiff(
                            deviceName = dev.name,
                            targetScheduler = storageConfig.scheduler,
                            currentScheduler = dev.currentScheduler,
                            targetReadAheadKb = storageConfig.readAheadKb,
                            currentReadAheadKb = dev.readAheadKb
                        )
                    )
                }
            }

            ProfileDiff(
                profileId = profile.id,
                cpuDiffs = cpuDiffs,
                gpuDiff = gpuDiff,
                memoryDiff = memoryDiff,
                storageDiffs = storageDiffs,
                runTrimBeforeApply = profile.storage?.runTrimBeforeApply ?: false
            )
        }
    }

    suspend fun applyProfile(
        profile: TuningProfile,
        rollbackOnFailure: Boolean = true
    ): Result<ApplyProfileResult> = withContext(ioDispatcher) {
        runCatching {
            val snapshot = captureCurrentState().getOrThrow()
            val diff = computeDiff(profile).getOrThrow()

            if (!diff.hasChanges) {
                profileRepository.setActiveProfile(profile.id).getOrThrow()
                return@runCatching ApplyProfileResult.success(profile.id, diff, verifiedCount = 0)
            }

            var executedChangesCount = 0

            try {
                // 1. Pre-actions
                if (diff.memoryDiff?.dropCachesBeforeApply == true) {
                    memoryControlEngine.dropCaches(DropCacheType.ALL).getOrThrow()
                }
                if (diff.runTrimBeforeApply) {
                    storageControlEngine.runFsTrim("/data").getOrThrow()
                }

                // 2. CPU changes
                for (cpu in diff.cpuDiffs) {
                    if (cpu.targetGovernor != null && cpu.targetGovernor != cpu.currentGovernor) {
                        cpuControlEngine.setGovernor(cpu.clusterId, cpu.targetGovernor).getOrThrow()
                        executedChangesCount++
                    }

                    val targetMin = cpu.targetMinFreqKHz ?: cpu.currentMinFreqKHz
                    val targetMax = cpu.targetMaxFreqKHz ?: cpu.currentMaxFreqKHz
                    if (targetMin != cpu.currentMinFreqKHz || targetMax != cpu.currentMaxFreqKHz) {
                        cpuControlEngine.setFrequencies(cpu.clusterId, targetMin, targetMax).getOrThrow()
                        executedChangesCount++
                    }
                }

                // 3. GPU changes
                diff.gpuDiff?.let { gpu ->
                    if (gpu.targetGovernor != null && gpu.targetGovernor != gpu.currentGovernor) {
                        gpuControlEngine.setGovernor(gpu.targetGovernor).getOrThrow()
                        executedChangesCount++
                    }

                    val targetMin = gpu.targetMinFreqKHz ?: (gpu.currentMinFreqKHz ?: 0L)
                    val targetMax = gpu.targetMaxFreqKHz ?: (gpu.currentMaxFreqKHz ?: 0L)
                    if (targetMin != gpu.currentMinFreqKHz || targetMax != gpu.currentMaxFreqKHz) {
                        gpuControlEngine.setFrequencies(targetMin, targetMax).getOrThrow()
                        executedChangesCount++
                    }
                }

                // 4. Memory changes
                diff.memoryDiff?.let { mem ->
                    if (mem.targetSwappiness != null && mem.targetSwappiness != mem.currentSwappiness) {
                        memoryControlEngine.setSwappiness(mem.targetSwappiness).getOrThrow()
                        executedChangesCount++
                    }
                    if (mem.targetVfsCachePressure != null && mem.targetVfsCachePressure != mem.currentVfsCachePressure) {
                        memoryControlEngine.setVfsCachePressure(mem.targetVfsCachePressure).getOrThrow()
                        executedChangesCount++
                    }
                }

                // 5. Storage changes
                for (storage in diff.storageDiffs) {
                    if (storage.targetScheduler != null && storage.targetScheduler != storage.currentScheduler) {
                        storageControlEngine.setScheduler(storage.deviceName, storage.targetScheduler).getOrThrow()
                        executedChangesCount++
                    }
                    if (storage.targetReadAheadKb != null && storage.targetReadAheadKb != storage.currentReadAheadKb) {
                        storageControlEngine.setReadAhead(storage.deviceName, storage.targetReadAheadKb).getOrThrow()
                        executedChangesCount++
                    }
                }

                // 6. Update active profile in repository
                profileRepository.setActiveProfile(profile.id).getOrThrow()

                ApplyProfileResult.success(profile.id, diff, verifiedCount = executedChangesCount)

            } catch (e: Throwable) {
                var wasRolledBack = false
                if (rollbackOnFailure) {
                    val rollbackRes = rollback(snapshot)
                    wasRolledBack = rollbackRes.isSuccess
                }

                throw IllegalStateException(
                    "Failed to apply profile ${profile.id}: ${e.message} (Rolled back: $wasRolledBack)",
                    e
                )
            }
        }
    }

    suspend fun rollback(snapshot: HardwareStateSnapshot): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            // Restore CPU Governors
            for ((clusterId, gov) in snapshot.cpuGovernors) {
                cpuControlEngine.setGovernor(clusterId, gov)
            }

            // Restore CPU Frequencies
            for (clusterId in snapshot.cpuMinFreqsKHz.keys) {
                val min = snapshot.cpuMinFreqsKHz[clusterId] ?: continue
                val max = snapshot.cpuMaxFreqsKHz[clusterId] ?: continue
                cpuControlEngine.setFrequencies(clusterId, min, max)
            }

            // Restore CPU Core Online States
            for ((coreId, isOnline) in snapshot.cpuCoreStates) {
                if (coreId > 0) { // Core 0 is boot protected
                    cpuControlEngine.setCoreOnline(coreId, isOnline)
                }
            }

            // Restore GPU
            if (snapshot.gpuGovernor != null) {
                gpuControlEngine.setGovernor(snapshot.gpuGovernor)
            }
            if (snapshot.gpuMinFreqKHz != null && snapshot.gpuMaxFreqKHz != null) {
                gpuControlEngine.setFrequencies(snapshot.gpuMinFreqKHz, snapshot.gpuMaxFreqKHz)
            }

            // Restore Memory
            if (snapshot.swappiness != null) {
                memoryControlEngine.setSwappiness(snapshot.swappiness)
            }
            if (snapshot.vfsCachePressure != null) {
                memoryControlEngine.setVfsCachePressure(snapshot.vfsCachePressure)
            }

            // Restore Storage
            for ((dev, sched) in snapshot.storageSchedulers) {
                storageControlEngine.setScheduler(dev, sched)
            }
            for ((dev, kb) in snapshot.storageReadAheadKb) {
                storageControlEngine.setReadAhead(dev, kb)
            }
        }
    }

    suspend fun applyProfileById(
        profileId: String,
        rollbackOnFailure: Boolean = true
    ): Result<ApplyProfileResult> = withContext(ioDispatcher) {
        runCatching {
            val profile = profileRepository.getProfileById(profileId).getOrThrow()
                ?: throw NoSuchElementException("Profile not found: $profileId")
            applyProfile(profile, rollbackOnFailure).getOrThrow()
        }
    }
}
