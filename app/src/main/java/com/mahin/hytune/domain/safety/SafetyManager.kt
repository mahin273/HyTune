package com.mahin.hytune.domain.safety

import com.mahin.hytune.data.local.datastore.AppSettingsRepository
import com.mahin.hytune.domain.engine.ProfileEngine
import com.mahin.hytune.domain.engine.model.ApplyProfileResult
import com.mahin.hytune.domain.engine.model.ProfileDiff
import com.mahin.hytune.domain.profile.ProfileRepository
import com.mahin.hytune.domain.profile.model.TuningProfile
import com.mahin.hytune.domain.safety.model.ProposedChange
import com.mahin.hytune.domain.safety.model.RiskLevel
import com.mahin.hytune.domain.safety.model.SafetyState
import com.mahin.hytune.domain.safety.model.WhitelistResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SafetyManager @Inject constructor(
    private val sysfsPathWhitelist: SysfsPathWhitelist,
    private val bootloopCrashGuard: BootloopCrashGuard,
    private val baselineSnapshotStore: BaselineSnapshotStore,
    private val profileEngine: ProfileEngine,
    private val profileRepository: ProfileRepository,
    private val appSettingsRepository: AppSettingsRepository
) {

    val safetyState: Flow<SafetyState> = appSettingsRepository.settings.map { settings ->
        val now = System.currentTimeMillis()
        val isWindowActive = (now - settings.lastApplyTimestampEpochMs) < BootloopCrashGuard.STABILITY_WINDOW_MS &&
                settings.lastApplyTimestampEpochMs > 0L

        SafetyState(
            bootloopCrashCount = settings.bootloopCrashCount,
            isSafeRecoveryModeActive = settings.safeRecoveryModeActive,
            isStabilityWindowActive = isWindowActive,
            hasBaselineSnapshot = !settings.baselineSnapshotJson.isNullOrBlank(),
            dryRunPreviewEnabled = settings.dryRunPreviewEnabled
        )
    }

    fun validatePath(rawPath: String): WhitelistResult {
        return sysfsPathWhitelist.validate(rawPath)
    }

    suspend fun prepareDryRun(profile: TuningProfile): Result<List<ProposedChange>> {
        return runCatching {
            val diff = profileEngine.computeDiff(profile).getOrThrow()
            buildProposedChanges(diff)
        }
    }

    fun buildProposedChanges(diff: ProfileDiff): List<ProposedChange> {
        val list = mutableListOf<ProposedChange>()

        // 1. CPU Cluster Diffs
        for (cpu in diff.cpuDiffs) {
            if (cpu.targetGovernor != null && cpu.targetGovernor != cpu.currentGovernor) {
                val govPath = "/sys/devices/system/cpu/cpu${cpu.clusterId}/cpufreq/scaling_governor"
                list.add(
                    ProposedChange(
                        subsystem = "CPU Cluster ${cpu.clusterId}",
                        parameter = "Governor",
                        currentValue = cpu.currentGovernor ?: "unknown",
                        targetValue = cpu.targetGovernor,
                        riskLevel = RiskLevel.MEDIUM,
                        isWhitelisted = sysfsPathWhitelist.isWhitelisted(govPath)
                    )
                )
            }

            val targetMin = cpu.targetMinFreqKHz ?: cpu.currentMinFreqKHz
            val targetMax = cpu.targetMaxFreqKHz ?: cpu.currentMaxFreqKHz
            if (targetMin != cpu.currentMinFreqKHz || targetMax != cpu.currentMaxFreqKHz) {
                val minFreqPath = "/sys/devices/system/cpu/cpu${cpu.clusterId}/cpufreq/scaling_min_freq"
                val maxFreqPath = "/sys/devices/system/cpu/cpu${cpu.clusterId}/cpufreq/scaling_max_freq"
                val isElevated = targetMax > cpu.currentMaxFreqKHz

                list.add(
                    ProposedChange(
                        subsystem = "CPU Cluster ${cpu.clusterId}",
                        parameter = "Frequencies (Min - Max)",
                        currentValue = "${cpu.currentMinFreqKHz / 1000} - ${cpu.currentMaxFreqKHz / 1000} MHz",
                        targetValue = "${targetMin / 1000} - ${targetMax / 1000} MHz",
                        riskLevel = if (isElevated) RiskLevel.HIGH else RiskLevel.MEDIUM,
                        isWhitelisted = sysfsPathWhitelist.isWhitelisted(minFreqPath) &&
                                sysfsPathWhitelist.isWhitelisted(maxFreqPath)
                    )
                )
            }
        }

        // 2. GPU Diff
        diff.gpuDiff?.let { gpu ->
            if (gpu.targetGovernor != null && gpu.targetGovernor != gpu.currentGovernor) {
                val govPath = "/sys/class/kgsl/kgsl-3d0/devfreq/governor"
                list.add(
                    ProposedChange(
                        subsystem = "GPU",
                        parameter = "Governor",
                        currentValue = gpu.currentGovernor ?: "unknown",
                        targetValue = gpu.targetGovernor,
                        riskLevel = RiskLevel.MEDIUM,
                        isWhitelisted = sysfsPathWhitelist.isWhitelisted(govPath)
                    )
                )
            }

            val targetMin = gpu.targetMinFreqKHz ?: (gpu.currentMinFreqKHz ?: 0L)
            val targetMax = gpu.targetMaxFreqKHz ?: (gpu.currentMaxFreqKHz ?: 0L)
            if (targetMin != gpu.currentMinFreqKHz || targetMax != gpu.currentMaxFreqKHz) {
                val freqPath = "/sys/class/kgsl/kgsl-3d0/devfreq/min_freq"
                list.add(
                    ProposedChange(
                        subsystem = "GPU",
                        parameter = "Frequencies (Min - Max)",
                        currentValue = "${(gpu.currentMinFreqKHz ?: 0L) / 1000} - ${(gpu.currentMaxFreqKHz ?: 0L) / 1000} MHz",
                        targetValue = "${targetMin / 1000} - ${targetMax / 1000} MHz",
                        riskLevel = RiskLevel.MEDIUM,
                        isWhitelisted = sysfsPathWhitelist.isWhitelisted(freqPath)
                    )
                )
            }
        }

        // 3. Memory Diff
        diff.memoryDiff?.let { mem ->
            if (mem.targetSwappiness != null && mem.targetSwappiness != mem.currentSwappiness) {
                val path = "/proc/sys/vm/swappiness"
                list.add(
                    ProposedChange(
                        subsystem = "Memory",
                        parameter = "Swappiness",
                        currentValue = "${mem.currentSwappiness ?: 60}",
                        targetValue = "${mem.targetSwappiness}",
                        riskLevel = RiskLevel.LOW,
                        isWhitelisted = sysfsPathWhitelist.isWhitelisted(path)
                    )
                )
            }

            if (mem.targetVfsCachePressure != null && mem.targetVfsCachePressure != mem.currentVfsCachePressure) {
                val path = "/proc/sys/vm/vfs_cache_pressure"
                list.add(
                    ProposedChange(
                        subsystem = "Memory",
                        parameter = "VFS Cache Pressure",
                        currentValue = "${mem.currentVfsCachePressure ?: 100}",
                        targetValue = "${mem.targetVfsCachePressure}",
                        riskLevel = RiskLevel.LOW,
                        isWhitelisted = sysfsPathWhitelist.isWhitelisted(path)
                    )
                )
            }

            if (mem.dropCachesBeforeApply) {
                val path = "/proc/sys/vm/drop_caches"
                list.add(
                    ProposedChange(
                        subsystem = "Memory",
                        parameter = "Drop Caches",
                        currentValue = "Retain Page & Inode Cache",
                        targetValue = "Drop Page Cache & Inodes",
                        riskLevel = RiskLevel.HIGH,
                        isWhitelisted = sysfsPathWhitelist.isWhitelisted(path)
                    )
                )
            }
        }

        // 4. Storage Diff
        for (storage in diff.storageDiffs) {
            if (storage.targetScheduler != null && storage.targetScheduler != storage.currentScheduler) {
                val path = "/sys/block/${storage.deviceName}/queue/scheduler"
                list.add(
                    ProposedChange(
                        subsystem = "Storage (${storage.deviceName})",
                        parameter = "I/O Scheduler",
                        currentValue = storage.currentScheduler ?: "unknown",
                        targetValue = storage.targetScheduler,
                        riskLevel = RiskLevel.LOW,
                        isWhitelisted = sysfsPathWhitelist.isWhitelisted(path)
                    )
                )
            }

            if (storage.targetReadAheadKb != null && storage.targetReadAheadKb != storage.currentReadAheadKb) {
                val path = "/sys/block/${storage.deviceName}/queue/read_ahead_kb"
                list.add(
                    ProposedChange(
                        subsystem = "Storage (${storage.deviceName})",
                        parameter = "Read-Ahead Buffer",
                        currentValue = "${storage.currentReadAheadKb ?: 128} KB",
                        targetValue = "${storage.targetReadAheadKb} KB",
                        riskLevel = RiskLevel.LOW,
                        isWhitelisted = sysfsPathWhitelist.isWhitelisted(path)
                    )
                )
            }
        }

        if (diff.runTrimBeforeApply) {
            list.add(
                ProposedChange(
                    subsystem = "Storage (/data)",
                    parameter = "FSTRIM Maintenance",
                    currentValue = "Skip TRIM",
                    targetValue = "Deallocate Deleted Flash Blocks",
                    riskLevel = RiskLevel.HIGH,
                    isWhitelisted = true
                )
            )
        }

        return list
    }

    suspend fun applyProfileSafely(
        profile: TuningProfile,
        bypassSafeRecoveryCheck: Boolean = false
    ): Result<ApplyProfileResult> {
        return runCatching {
            val settings = appSettingsRepository.settings.first()
            if (settings.safeRecoveryModeActive && !bypassSafeRecoveryCheck) {
                throw IllegalStateException(
                    "HyTune is currently in Safe Recovery Mode due to consecutive crashes. " +
                            "Reset the crash counter or perform Panic Restore before applying custom profiles."
                )
            }

            // Ensure baseline snapshot is permanently captured on first apply
            baselineSnapshotStore.saveBaselineIfNotPresent().getOrThrow()

            // Arm crash guard before hardware writes
            bootloopCrashGuard.recordApplyAttempt()

            try {
                val result = profileEngine.applyProfile(profile, rollbackOnFailure = true).getOrThrow()
                bootloopCrashGuard.markApplyCompleted()
                result
            } catch (e: Throwable) {
                bootloopCrashGuard.markApplyCompleted()
                throw e
            }
        }
    }

    suspend fun panicRestore(): Result<Unit> {
        return runCatching {
            val baseline = baselineSnapshotStore.getBaselineSnapshot().getOrThrow()
            profileEngine.rollback(baseline).getOrThrow()
            profileRepository.setActiveProfile("balance").getOrThrow()
            bootloopCrashGuard.resetCrashCounter()
        }
    }

    suspend fun setDryRunPreviewEnabled(enabled: Boolean) {
        appSettingsRepository.setDryRunPreviewEnabled(enabled)
    }

    suspend fun resetCrashCounter() {
        bootloopCrashGuard.resetCrashCounter()
    }
}
