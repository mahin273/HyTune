package com.mahin.hytune.domain.backup

import androidx.room.withTransaction
import com.mahin.hytune.data.local.datastore.AppSettings
import com.mahin.hytune.data.local.datastore.AppSettingsRepository
import com.mahin.hytune.data.local.db.HyTuneDatabase
import com.mahin.hytune.data.local.db.dao.AppRuleDao
import com.mahin.hytune.data.local.db.dao.AutomationRuleDao
import com.mahin.hytune.data.local.db.dao.CustomScriptDao
import com.mahin.hytune.data.local.db.dao.FreezeGroupDao
import com.mahin.hytune.data.local.db.dao.ProfileDao
import com.mahin.hytune.data.local.db.entity.AppRuleEntity
import com.mahin.hytune.data.local.db.entity.AutomationRuleEntity
import com.mahin.hytune.data.local.db.entity.CustomScriptEntity
import com.mahin.hytune.data.local.db.entity.FreezeGroupEntity
import com.mahin.hytune.data.local.db.entity.ProfileEntity
import com.mahin.hytune.domain.backup.engine.BackupChecksumCalculator
import com.mahin.hytune.domain.backup.model.AppRuleBackupDto
import com.mahin.hytune.domain.backup.model.AutomationRuleBackupDto
import com.mahin.hytune.domain.backup.model.BackupBundleDto
import com.mahin.hytune.domain.backup.model.BackupHeaderDto
import com.mahin.hytune.domain.backup.model.BackupPayloadDto
import com.mahin.hytune.domain.backup.model.ConflictStrategy
import com.mahin.hytune.domain.backup.model.CustomScriptBackupDto
import com.mahin.hytune.domain.backup.model.FreezeGroupBackupDto
import com.mahin.hytune.domain.backup.model.ProfileBackupDto
import com.mahin.hytune.domain.backup.model.RestoreFailureReason
import com.mahin.hytune.domain.backup.model.RestoreResult
import com.mahin.hytune.domain.backup.model.SettingsBackupDto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRestoreManager @Inject constructor(
    private val database: HyTuneDatabase,
    private val profileDao: ProfileDao,
    private val automationRuleDao: AutomationRuleDao,
    private val customScriptDao: CustomScriptDao,
    private val appRuleDao: AppRuleDao,
    private val freezeGroupDao: FreezeGroupDao,
    private val appSettingsRepository: AppSettingsRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val canonicalJson = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val exportJson = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun createBackup(
        appVersionName: String = "1.0.0",
        deviceModel: String = "Android Device"
    ): String = withContext(ioDispatcher) {
        val profiles = profileDao.getAllProfiles().first().map { it.toBackupDto() }
        val rules = automationRuleDao.getAllRules().first().map { it.toBackupDto() }
        val scripts = customScriptDao.getAllScripts().first().map { it.toBackupDto() }
        val appRules = appRuleDao.getAllAppRules().first().map { it.toBackupDto() }
        val freezeGroups = freezeGroupDao.getAllGroupsWithPackages().first().map { groupWithPkgs ->
            FreezeGroupBackupDto(
                id = groupWithPkgs.group.id,
                name = groupWithPkgs.group.name,
                description = groupWithPkgs.group.description,
                autoFreezeOnScreenOff = groupWithPkgs.group.autoFreezeOnScreenOff,
                autoFreezeDelaySeconds = groupWithPkgs.group.autoFreezeDelaySeconds,
                packageNames = groupWithPkgs.packages.map { it.packageName },
                createdAtEpochMs = groupWithPkgs.group.createdAtEpochMs
            )
        }
        val currentSettings = appSettingsRepository.settings.first()
        val settingsDto = currentSettings.toBackupDto()

        val payload = BackupPayloadDto(
            profiles = profiles,
            automationRules = rules,
            customScripts = scripts,
            appRules = appRules,
            freezeGroups = freezeGroups,
            settings = settingsDto
        )

        val payloadCanonicalJson = canonicalJson.encodeToString(BackupPayloadDto.serializer(), payload)
        val checksum = BackupChecksumCalculator.computeSha256(payloadCanonicalJson)

        val header = BackupHeaderDto(
            schemaVersion = BackupHeaderDto.CURRENT_SCHEMA_VERSION,
            exportedAtEpochMs = System.currentTimeMillis(),
            appVersionName = appVersionName,
            deviceModel = deviceModel,
            checksumSha256 = checksum
        )

        val bundle = BackupBundleDto(header = header, payload = payload)
        exportJson.encodeToString(BackupBundleDto.serializer(), bundle)
    }

    fun validateBackup(jsonString: String): Result<BackupBundleDto> {
        if (jsonString.isBlank()) {
            return Result.failure(IllegalArgumentException("Backup payload is empty"))
        }

        val bundle = try {
            canonicalJson.decodeFromString(BackupBundleDto.serializer(), jsonString)
        } catch (t: Throwable) {
            return Result.failure(IllegalArgumentException("Malformed backup JSON: ${t.message}"))
        }

        if (bundle.header.schemaVersion > BackupHeaderDto.CURRENT_SCHEMA_VERSION) {
            return Result.failure(
                IllegalStateException("Unsupported backup schema version: ${bundle.header.schemaVersion}")
            )
        }

        val recomputedJson = canonicalJson.encodeToString(BackupPayloadDto.serializer(), bundle.payload)
        val isValidChecksum = BackupChecksumCalculator.verifySha256(
            content = recomputedJson,
            expectedChecksum = bundle.header.checksumSha256
        )

        if (!isValidChecksum) {
            return Result.failure(SecurityException("Corrupted backup: SHA-256 checksum mismatch"))
        }

        return Result.success(bundle)
    }

    suspend fun restoreBackup(
        jsonString: String,
        conflictStrategy: ConflictStrategy = ConflictStrategy.OVERWRITE
    ): RestoreResult = withContext(ioDispatcher) {
        val validation = validateBackup(jsonString)
        if (validation.isFailure) {
            val ex = validation.exceptionOrNull()
            val reason = when (ex) {
                is SecurityException -> RestoreFailureReason.INVALID_CHECKSUM
                is IllegalStateException -> RestoreFailureReason.UNSUPPORTED_VERSION
                else -> RestoreFailureReason.MALFORMED_JSON
            }
            return@withContext RestoreResult.Failure(
                reason = reason,
                message = ex?.message ?: "Validation failed"
            )
        }

        val bundle = validation.getOrThrow()
        val payload = bundle.payload

        var restoredProfiles = 0
        var restoredRules = 0
        var restoredScripts = 0
        var restoredAppRules = 0
        var restoredFreezeGroups = 0

        try {
            database.withTransaction {
                // 1. Profiles
                val existingProfiles = profileDao.getAllProfiles().first()
                for (pDto in payload.profiles) {
                    val existing = existingProfiles.firstOrNull { it.id == pDto.id }
                    if (existing != null) {
                        when (conflictStrategy) {
                            ConflictStrategy.OVERWRITE -> {
                                profileDao.upsertProfile(pDto.toEntity())
                                restoredProfiles++
                            }
                            ConflictStrategy.SKIP_EXISTING -> {
                                // Do nothing
                            }
                            ConflictStrategy.RENAME_ON_CONFLICT -> {
                                if (pDto.isBuiltIn) {
                                    // Built-in profiles cannot be duplicated
                                    profileDao.upsertProfile(pDto.toEntity())
                                } else {
                                    val newId = UUID.randomUUID().toString()
                                    val renamed = pDto.toEntity().copy(
                                        id = newId,
                                        name = "${pDto.name} (Restored)"
                                    )
                                    profileDao.upsertProfile(renamed)
                                }
                                restoredProfiles++
                            }
                        }
                    } else {
                        profileDao.upsertProfile(pDto.toEntity())
                        restoredProfiles++
                    }
                }

                // 2. Automation Rules
                val existingRules = automationRuleDao.getAllRules().first()
                for (rDto in payload.automationRules) {
                    val existingById = existingRules.firstOrNull { it.id == rDto.id }
                    val existingByName = existingRules.firstOrNull { it.name.equals(rDto.name, ignoreCase = true) }

                    when (conflictStrategy) {
                        ConflictStrategy.OVERWRITE -> {
                            automationRuleDao.insertRule(rDto.toEntity())
                            restoredRules++
                        }
                        ConflictStrategy.SKIP_EXISTING -> {
                            if (existingById == null && existingByName == null) {
                                automationRuleDao.insertRule(rDto.toEntity().copy(id = 0L))
                                restoredRules++
                            }
                        }
                        ConflictStrategy.RENAME_ON_CONFLICT -> {
                            val newName = if (existingByName != null) "${rDto.name} (Restored)" else rDto.name
                            automationRuleDao.insertRule(rDto.toEntity().copy(id = 0L, name = newName))
                            restoredRules++
                        }
                    }
                }

                // 3. Custom Scripts
                val existingScripts = customScriptDao.getAllScripts().first()
                for (sDto in payload.customScripts) {
                    val existingById = existingScripts.firstOrNull { it.id == sDto.id }
                    val existingByName = existingScripts.firstOrNull { it.name.equals(sDto.name, ignoreCase = true) }

                    when (conflictStrategy) {
                        ConflictStrategy.OVERWRITE -> {
                            customScriptDao.insertScript(sDto.toEntity())
                            restoredScripts++
                        }
                        ConflictStrategy.SKIP_EXISTING -> {
                            if (existingById == null && existingByName == null) {
                                customScriptDao.insertScript(sDto.toEntity().copy(id = 0L))
                                restoredScripts++
                            }
                        }
                        ConflictStrategy.RENAME_ON_CONFLICT -> {
                            val newName = if (existingByName != null) "${sDto.name} (Restored)" else sDto.name
                            customScriptDao.insertScript(sDto.toEntity().copy(id = 0L, name = newName))
                            restoredScripts++
                        }
                    }
                }

                // 4. App Rules
                val existingAppRules = appRuleDao.getAllAppRules().first()
                for (aDto in payload.appRules) {
                    val existing = existingAppRules.firstOrNull { it.packageName == aDto.packageName }
                    if (existing != null) {
                        if (conflictStrategy == ConflictStrategy.OVERWRITE) {
                            appRuleDao.upsertAppRule(aDto.toEntity())
                            restoredAppRules++
                        }
                    } else {
                        appRuleDao.upsertAppRule(aDto.toEntity())
                        restoredAppRules++
                    }
                }

                // 5. Freeze Groups
                for (gDto in payload.freezeGroups) {
                    val groupEntity = FreezeGroupEntity(
                        id = if (conflictStrategy == ConflictStrategy.OVERWRITE) gDto.id else 0L,
                        name = if (conflictStrategy == ConflictStrategy.RENAME_ON_CONFLICT) "${gDto.name} (Restored)" else gDto.name,
                        description = gDto.description,
                        autoFreezeOnScreenOff = gDto.autoFreezeOnScreenOff,
                        autoFreezeDelaySeconds = gDto.autoFreezeDelaySeconds,
                        createdAtEpochMs = gDto.createdAtEpochMs
                    )
                    freezeGroupDao.saveGroupWithPackages(groupEntity, gDto.packageNames)
                    restoredFreezeGroups++
                }
            }

            // 6. Datastore Settings
            appSettingsRepository.restoreFromBackup(payload.settings)

            RestoreResult.Success(
                restoredProfiles = restoredProfiles,
                restoredRules = restoredRules,
                restoredScripts = restoredScripts,
                restoredAppRules = restoredAppRules,
                restoredFreezeGroups = restoredFreezeGroups,
                settingsRestored = true
            )
        } catch (t: Throwable) {
            RestoreResult.Failure(
                reason = RestoreFailureReason.DATABASE_ERROR,
                message = t.message ?: "Database transaction failed during restore"
            )
        }
    }

    private fun ProfileEntity.toBackupDto(): ProfileBackupDto = ProfileBackupDto(
        id = id,
        name = name,
        isBuiltIn = isBuiltIn,
        cpuGovernor = cpuGovernor,
        cpuMinFreqKHz = cpuMinFreqKHz,
        cpuMaxFreqKHz = cpuMaxFreqKHz,
        gpuGovernor = gpuGovernor,
        gpuMinFreqKHz = gpuMinFreqKHz,
        gpuMaxFreqKHz = gpuMaxFreqKHz,
        zramEnabled = zramEnabled,
        ioScheduler = ioScheduler,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs
    )

    private fun ProfileBackupDto.toEntity(): ProfileEntity = ProfileEntity(
        id = id,
        name = name,
        isBuiltIn = isBuiltIn,
        cpuGovernor = cpuGovernor,
        cpuMinFreqKHz = cpuMinFreqKHz,
        cpuMaxFreqKHz = cpuMaxFreqKHz,
        gpuGovernor = gpuGovernor,
        gpuMinFreqKHz = gpuMinFreqKHz,
        gpuMaxFreqKHz = gpuMaxFreqKHz,
        zramEnabled = zramEnabled,
        ioScheduler = ioScheduler,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs
    )

    private fun AutomationRuleEntity.toBackupDto(): AutomationRuleBackupDto = AutomationRuleBackupDto(
        id = id,
        name = name,
        isEnabled = isEnabled,
        priority = priority,
        triggerType = triggerType,
        triggerValue = triggerValue,
        conditionJson = conditionJson,
        actionType = actionType,
        actionValue = actionValue,
        cooldownSeconds = cooldownSeconds,
        lastTriggeredEpochMs = lastTriggeredEpochMs
    )

    private fun AutomationRuleBackupDto.toEntity(): AutomationRuleEntity = AutomationRuleEntity(
        id = id,
        name = name,
        isEnabled = isEnabled,
        priority = priority,
        triggerType = triggerType,
        triggerValue = triggerValue,
        conditionJson = conditionJson,
        actionType = actionType,
        actionValue = actionValue,
        cooldownSeconds = cooldownSeconds,
        lastTriggeredEpochMs = lastTriggeredEpochMs
    )

    private fun CustomScriptEntity.toBackupDto(): CustomScriptBackupDto = CustomScriptBackupDto(
        id = id,
        name = name,
        scriptContent = scriptContent,
        runAtBoot = runAtBoot,
        runAsRoot = runAsRoot,
        createdAtEpochMs = createdAtEpochMs
    )

    private fun CustomScriptBackupDto.toEntity(): CustomScriptEntity = CustomScriptEntity(
        id = id,
        name = name,
        scriptContent = scriptContent,
        runAtBoot = runAtBoot,
        runAsRoot = runAsRoot,
        createdAtEpochMs = createdAtEpochMs
    )

    private fun AppRuleEntity.toBackupDto(): AppRuleBackupDto = AppRuleBackupDto(
        packageName = packageName,
        profileId = profileId,
        isGame = isGame,
        targetRefreshRate = targetRefreshRate,
        dndEnabled = dndEnabled,
        bypassChargingEnabled = bypassChargingEnabled,
        freezeOnScreenOff = freezeOnScreenOff
    )

    private fun AppRuleBackupDto.toEntity(): AppRuleEntity = AppRuleEntity(
        packageName = packageName,
        profileId = profileId,
        isGame = isGame,
        targetRefreshRate = targetRefreshRate,
        dndEnabled = dndEnabled,
        bypassChargingEnabled = bypassChargingEnabled,
        freezeOnScreenOff = freezeOnScreenOff
    )

    private fun AppSettings.toBackupDto(): SettingsBackupDto = SettingsBackupDto(
        activeProfileId = activeProfileId,
        temperatureUnitCelsius = temperatureUnitCelsius,
        floatingMonitorEnabled = floatingMonitorEnabled,
        samplingIntervalMs = samplingIntervalMs,
        safetyRollbackTimeoutSec = safetyRollbackTimeoutSec,
        perAppProfilesEnabled = perAppProfilesEnabled,
        dynamicThemeEnabled = dynamicThemeEnabled,
        dryRunPreviewEnabled = dryRunPreviewEnabled,
        daemonServiceEnabled = daemonServiceEnabled,
        daemonNotificationDetailsEnabled = daemonNotificationDetailsEnabled,
        chargeLimitEnabled = chargeLimitEnabled,
        chargeLimitPercent = chargeLimitPercent,
        chargeCurrentLimitMa = chargeCurrentLimitMa,
        bypassChargingEnabled = bypassChargingEnabled,
        overlayCompactMode = overlayCompactMode,
        overlayOpacity = overlayOpacity,
        overlayShowCpu = overlayShowCpu,
        overlayShowGpu = overlayShowGpu,
        overlayShowBattery = overlayShowBattery,
        overlayShowTemp = overlayShowTemp,
        overlayShowRam = overlayShowRam,
        overlayShowFps = overlayShowFps,
        overlayPositionX = overlayPositionX,
        overlayPositionY = overlayPositionY,
        applyOnBoot = applyOnBoot,
        bootApplyDelaySeconds = bootApplyDelaySeconds,
        bootApplyNotificationEnabled = bootApplyNotificationEnabled
    )
}
