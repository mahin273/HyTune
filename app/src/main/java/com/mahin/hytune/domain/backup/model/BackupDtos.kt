package com.mahin.hytune.domain.backup.model

import kotlinx.serialization.Serializable

@Serializable
data class BackupHeaderDto(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val exportedAtEpochMs: Long = System.currentTimeMillis(),
    val appVersionName: String = "1.0.0",
    val deviceModel: String = "Unknown",
    val checksumSha256: String = ""
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

@Serializable
data class ProfileBackupDto(
    val id: String,
    val name: String,
    val isBuiltIn: Boolean = false,
    val cpuGovernor: String? = null,
    val cpuMinFreqKHz: Long? = null,
    val cpuMaxFreqKHz: Long? = null,
    val gpuGovernor: String? = null,
    val gpuMinFreqKHz: Long? = null,
    val gpuMaxFreqKHz: Long? = null,
    val zramEnabled: Boolean? = null,
    val ioScheduler: String? = null,
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L
)

@Serializable
data class AutomationRuleBackupDto(
    val id: Long = 0L,
    val name: String,
    val isEnabled: Boolean = true,
    val priority: Int = 50,
    val triggerType: String,
    val triggerValue: String = "",
    val conditionJson: String = "[]",
    val actionType: String,
    val actionValue: String = "",
    val cooldownSeconds: Int = 30,
    val lastTriggeredEpochMs: Long = 0L
)

@Serializable
data class CustomScriptBackupDto(
    val id: Long = 0L,
    val name: String,
    val scriptContent: String,
    val runAtBoot: Boolean = false,
    val runAsRoot: Boolean = true,
    val createdAtEpochMs: Long = 0L
)

@Serializable
data class AppRuleBackupDto(
    val packageName: String,
    val profileId: String,
    val isGame: Boolean = false,
    val targetRefreshRate: Int? = null,
    val dndEnabled: Boolean = false,
    val bypassChargingEnabled: Boolean = false,
    val freezeOnScreenOff: Boolean = false
)

@Serializable
data class FreezeGroupBackupDto(
    val id: Long = 0L,
    val name: String,
    val description: String = "",
    val autoFreezeOnScreenOff: Boolean = false,
    val autoFreezeDelaySeconds: Int = 30,
    val packageNames: List<String> = emptyList(),
    val createdAtEpochMs: Long = 0L
)

@Serializable
data class SettingsBackupDto(
    val activeProfileId: String = "balance",
    val temperatureUnitCelsius: Boolean = true,
    val floatingMonitorEnabled: Boolean = false,
    val samplingIntervalMs: Long = 1000L,
    val safetyRollbackTimeoutSec: Int = 15,
    val perAppProfilesEnabled: Boolean = false,
    val dynamicThemeEnabled: Boolean = true,
    val dryRunPreviewEnabled: Boolean = true,
    val daemonServiceEnabled: Boolean = false,
    val daemonNotificationDetailsEnabled: Boolean = true,
    val chargeLimitEnabled: Boolean = false,
    val chargeLimitPercent: Int = 80,
    val chargeCurrentLimitMa: Int = 0,
    val bypassChargingEnabled: Boolean = false,
    val overlayCompactMode: Boolean = true,
    val overlayOpacity: Float = 0.85f,
    val overlayShowCpu: Boolean = true,
    val overlayShowGpu: Boolean = true,
    val overlayShowBattery: Boolean = true,
    val overlayShowTemp: Boolean = true,
    val overlayShowRam: Boolean = true,
    val overlayShowFps: Boolean = true,
    val overlayPositionX: Int = 100,
    val overlayPositionY: Int = 200,
    val applyOnBoot: Boolean = false,
    val bootApplyDelaySeconds: Int = 30,
    val bootApplyNotificationEnabled: Boolean = true
)

@Serializable
data class BackupPayloadDto(
    val profiles: List<ProfileBackupDto> = emptyList(),
    val automationRules: List<AutomationRuleBackupDto> = emptyList(),
    val customScripts: List<CustomScriptBackupDto> = emptyList(),
    val appRules: List<AppRuleBackupDto> = emptyList(),
    val freezeGroups: List<FreezeGroupBackupDto> = emptyList(),
    val settings: SettingsBackupDto = SettingsBackupDto()
)

@Serializable
data class BackupBundleDto(
    val header: BackupHeaderDto,
    val payload: BackupPayloadDto
)

enum class ConflictStrategy {
    OVERWRITE,
    SKIP_EXISTING,
    RENAME_ON_CONFLICT
}

enum class RestoreFailureReason {
    MALFORMED_JSON,
    INVALID_CHECKSUM,
    UNSUPPORTED_VERSION,
    EMPTY_PAYLOAD,
    DATABASE_ERROR
}

sealed interface RestoreResult {
    data class Success(
        val restoredProfiles: Int,
        val restoredRules: Int,
        val restoredScripts: Int,
        val restoredAppRules: Int,
        val restoredFreezeGroups: Int,
        val settingsRestored: Boolean
    ) : RestoreResult

    data class Failure(
        val reason: RestoreFailureReason,
        val message: String
    ) : RestoreResult
}
