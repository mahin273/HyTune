package com.mahin.hytune.domain.safety

import com.mahin.hytune.data.local.datastore.AppSettingsRepository
import com.mahin.hytune.domain.engine.ProfileEngine
import com.mahin.hytune.domain.engine.model.HardwareStateSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BaselineSnapshotStore @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository,
    private val profileEngine: ProfileEngine
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun hasBaselineSnapshot(): Boolean {
        val settings = appSettingsRepository.settings.first()
        return !settings.baselineSnapshotJson.isNullOrBlank()
    }

    suspend fun saveBaselineIfNotPresent(): Result<HardwareStateSnapshot> {
        return runCatching {
            val settings = appSettingsRepository.settings.first()
            val existingJson = settings.baselineSnapshotJson

            if (!existingJson.isNullOrBlank()) {
                return@runCatching json.decodeFromString<HardwareStateSnapshot>(existingJson)
            }

            val captured = profileEngine.captureCurrentState().getOrThrow()
            val encoded = json.encodeToString(captured)
            appSettingsRepository.setBaselineSnapshotJson(encoded)
            captured
        }
    }

    suspend fun getBaselineSnapshot(): Result<HardwareStateSnapshot> {
        return runCatching {
            val settings = appSettingsRepository.settings.first()
            val existingJson = settings.baselineSnapshotJson

            if (!existingJson.isNullOrBlank()) {
                json.decodeFromString<HardwareStateSnapshot>(existingJson)
            } else {
                saveBaselineIfNotPresent().getOrThrow()
            }
        }
    }

    suspend fun forceUpdateBaseline(snapshot: HardwareStateSnapshot): Result<Unit> {
        return runCatching {
            val encoded = json.encodeToString(snapshot)
            appSettingsRepository.setBaselineSnapshotJson(encoded)
        }
    }
}
