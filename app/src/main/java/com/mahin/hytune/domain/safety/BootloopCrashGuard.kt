package com.mahin.hytune.domain.safety

import com.mahin.hytune.data.local.datastore.AppSettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BootloopCrashGuard @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository
) {

    companion object {
        const val STABILITY_WINDOW_MS = 5 * 60 * 1000L // 5 minutes stability window
        const val CRASH_THRESHOLD = 2 // Number of consecutive crashes triggering Safe Recovery Mode
    }

    suspend fun onAppOrBootStart(currentTimeEpochMs: Long = System.currentTimeMillis()): Result<Boolean> {
        return runCatching {
            val settings = appSettingsRepository.settings.first()
            var currentCrashCount = settings.bootloopCrashCount
            var triggeredCrash = false

            if (settings.isApplyingSettings) {
                // Terminated mid-apply (hard kernel panic or battery pull)
                currentCrashCount++
                triggeredCrash = true
            } else if (!settings.gracefulShutdownOccurred && settings.lastApplyTimestampEpochMs > 0L) {
                val elapsedSinceApply = currentTimeEpochMs - settings.lastApplyTimestampEpochMs
                if (elapsedSinceApply in 0 until STABILITY_WINDOW_MS) {
                    // Abrupt reboot occurred within the 5-minute stability window
                    currentCrashCount++
                    triggeredCrash = true
                }
            }

            val isSafeRecoveryMode = currentCrashCount >= CRASH_THRESHOLD
            if (triggeredCrash) {
                appSettingsRepository.setBootloopCrashCount(currentCrashCount)
            }

            if (isSafeRecoveryMode) {
                appSettingsRepository.setSafeRecoveryModeActive(true)
            }

            // Reset volatile transient flags for the new running session
            appSettingsRepository.setIsApplyingSettings(false)
            appSettingsRepository.setGracefulShutdownOccurred(false)

            isSafeRecoveryMode
        }
    }

    suspend fun recordApplyAttempt(timestampMs: Long = System.currentTimeMillis()) {
        appSettingsRepository.setIsApplyingSettings(true)
        appSettingsRepository.setLastApplyTimestampEpochMs(timestampMs)
    }

    suspend fun markApplyCompleted() {
        appSettingsRepository.setIsApplyingSettings(false)
    }

    suspend fun acknowledgeStability() {
        appSettingsRepository.setBootloopCrashCount(0)
        appSettingsRepository.setSafeRecoveryModeActive(false)
    }

    suspend fun recordGracefulShutdown() {
        appSettingsRepository.setGracefulShutdownOccurred(true)
    }

    suspend fun resetCrashCounter() {
        appSettingsRepository.setBootloopCrashCount(0)
        appSettingsRepository.setSafeRecoveryModeActive(false)
    }
}
