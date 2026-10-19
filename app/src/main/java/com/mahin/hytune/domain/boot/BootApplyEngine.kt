package com.mahin.hytune.domain.boot

import com.mahin.hytune.data.local.datastore.AppSettingsRepository
import com.mahin.hytune.domain.boot.model.BootApplyResult
import com.mahin.hytune.domain.engine.ProfileEngine
import com.mahin.hytune.domain.safety.BootloopCrashGuard
import com.mahin.hytune.domain.script.ScriptRepository
import com.mahin.hytune.service.notification.DaemonNotificationManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BootApplyEngine @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository,
    private val bootloopCrashGuard: BootloopCrashGuard,
    private val profileEngine: ProfileEngine,
    private val scriptRepository: ScriptRepository,
    private val notificationManager: DaemonNotificationManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun startBootApply(overrideDelayMs: Long? = null): BootApplyResult = withContext(ioDispatcher) {
        val settings = appSettingsRepository.settings.first()

        if (!settings.applyOnBoot) {
            return@withContext BootApplyResult(
                success = true,
                message = "Boot apply disabled by user setting",
                skippedReason = "DISABLED"
            )
        }

        val guardResult = bootloopCrashGuard.onAppOrBootStart()
        val isSafeRecoveryMode = guardResult.getOrDefault(false)

        if (isSafeRecoveryMode) {
            val abortMsg = "Boot apply blocked: Safe Recovery Mode active (crash threshold exceeded)"
            appSettingsRepository.setLastBootApplyStatus(abortMsg)
            if (settings.bootApplyNotificationEnabled) {
                notificationManager.postBootNotification(
                    title = "HyTune Safe Mode",
                    message = abortMsg,
                    isWarning = true
                )
            }
            return@withContext BootApplyResult(
                success = false,
                message = abortMsg,
                skippedReason = "SAFE_RECOVERY_MODE"
            )
        }

        val delayMs = overrideDelayMs ?: (settings.bootApplyDelaySeconds * 1000L)
        if (delayMs > 0) {
            delay(delayMs)
        }

        try {
            bootloopCrashGuard.recordApplyAttempt()

            val profileId = settings.activeProfileId
            var profileSuccess = false
            var profileError: String? = null

            if (profileId.isNotBlank()) {
                val profileRes = profileEngine.applyProfileById(profileId)
                if (profileRes.isSuccess) {
                    profileSuccess = true
                } else {
                    profileError = profileRes.exceptionOrNull()?.message ?: "Unknown profile error"
                }
            }

            val bootScripts = scriptRepository.getBootScripts()
            var scriptSuccessCount = 0

            for (script in bootScripts) {
                val scriptRes = scriptRepository.executeScript(script.id)
                if (scriptRes.isSuccess && scriptRes.getOrNull()?.exitCode == 0) {
                    scriptSuccessCount++
                }
            }

            bootloopCrashGuard.markApplyCompleted()

            val overallSuccess = profileId.isBlank() || profileSuccess
            val statusMessage = buildString {
                if (overallSuccess) {
                    append("Success: Applied profile '$profileId'")
                } else {
                    append("Partial: Profile '$profileId' failed ($profileError)")
                }
                if (bootScripts.isNotEmpty()) {
                    append(", executed $scriptSuccessCount/${bootScripts.size} boot scripts")
                }
            }

            appSettingsRepository.setLastBootApplyStatus(statusMessage)

            if (settings.bootApplyNotificationEnabled) {
                notificationManager.postBootNotification(
                    title = if (overallSuccess) "HyTune Boot Apply" else "HyTune Boot Warning",
                    message = statusMessage,
                    isWarning = !overallSuccess
                )
            }

            BootApplyResult(
                success = overallSuccess,
                message = statusMessage,
                appliedProfileId = if (profileSuccess) profileId else null,
                executedScriptCount = scriptSuccessCount
            )
        } catch (e: Throwable) {
            bootloopCrashGuard.markApplyCompleted()
            val errorMsg = "Failed during boot apply: ${e.message}"
            appSettingsRepository.setLastBootApplyStatus(errorMsg)
            if (settings.bootApplyNotificationEnabled) {
                notificationManager.postBootNotification(
                    title = "HyTune Boot Error",
                    message = errorMsg,
                    isWarning = true
                )
            }
            BootApplyResult(
                success = false,
                message = errorMsg,
                skippedReason = "ERROR"
            )
        }
    }

    suspend fun testBootApplyNow(): BootApplyResult {
        return startBootApply(overrideDelayMs = 0L)
    }
}
