package com.mahin.hytune.domain.profile

import com.mahin.hytune.data.local.datastore.AppSettingsRepository
import com.mahin.hytune.data.local.db.dao.ProfileDao
import com.mahin.hytune.data.local.db.entity.ProfileEntity
import com.mahin.hytune.domain.profile.model.CpuClusterProfileConfig
import com.mahin.hytune.domain.profile.model.FrequencyTarget
import com.mahin.hytune.domain.profile.model.GpuProfileConfig
import com.mahin.hytune.domain.profile.model.MemoryProfileConfig
import com.mahin.hytune.domain.profile.model.ProfileMode
import com.mahin.hytune.domain.profile.model.StorageProfileConfig
import com.mahin.hytune.domain.profile.model.TuningProfile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    private val profileDao: ProfileDao,
    private val appSettingsRepository: AppSettingsRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun seedBuiltInProfilesIfEmpty(): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val existing = profileDao.getBuiltInProfiles()
            if (existing.isEmpty()) {
                val entities = BuiltInProfiles.ALL_BUILT_IN.map { it.toEntity() }
                profileDao.upsertProfiles(entities)
            }
        }
    }

    fun getAllProfiles(): Flow<List<TuningProfile>> {
        return profileDao.getAllProfiles().map { entities ->
            if (entities.isEmpty()) {
                BuiltInProfiles.ALL_BUILT_IN
            } else {
                entities.map { it.toDomain() }
            }
        }.flowOn(ioDispatcher)
    }

    suspend fun getProfileById(id: String): Result<TuningProfile?> = withContext(ioDispatcher) {
        runCatching {
            val builtIn = BuiltInProfiles.findById(id)
            if (builtIn != null) {
                return@runCatching builtIn
            }
            profileDao.getProfileById(id)?.toDomain()
        }
    }

    suspend fun saveCustomProfile(profile: TuningProfile): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            require(!profile.isBuiltIn) { "Cannot overwrite built-in profile: ${profile.id}" }
            profileDao.upsertProfile(profile.toEntity())
        }
    }

    suspend fun deleteCustomProfile(id: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val deletedCount = profileDao.deleteCustomProfileById(id)
            if (deletedCount == 0) {
                throw IllegalStateException("Profile $id not found or cannot be deleted (built-in protection)")
            }
        }
    }

    fun getActiveProfileId(): Flow<String> {
        return appSettingsRepository.settings.map { it.activeProfileId }
    }

    fun getActiveProfile(): Flow<TuningProfile> {
        return combine(
            getActiveProfileId(),
            getAllProfiles()
        ) { activeId, profiles ->
            profiles.find { it.id.equals(activeId, ignoreCase = true) }
                ?: BuiltInProfiles.findById(activeId)
                ?: BuiltInProfiles.BALANCE
        }.flowOn(ioDispatcher)
    }

    suspend fun setActiveProfile(profileId: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            appSettingsRepository.setActiveProfileId(profileId)
        }
    }
}

fun TuningProfile.toEntity(): ProfileEntity {
    val firstCpu = cpuClusters.values.firstOrNull()
    val minFreq = (firstCpu?.minFreqTarget as? FrequencyTarget.AbsoluteKHz)?.freqKHz
    val maxFreq = (firstCpu?.maxFreqTarget as? FrequencyTarget.AbsoluteKHz)?.freqKHz

    val gpuMin = (gpu?.minFreqTarget as? FrequencyTarget.AbsoluteKHz)?.freqKHz
    val gpuMax = (gpu?.maxFreqTarget as? FrequencyTarget.AbsoluteKHz)?.freqKHz

    return ProfileEntity(
        id = id,
        name = name,
        isBuiltIn = isBuiltIn,
        cpuGovernor = firstCpu?.governor,
        cpuMinFreqKHz = minFreq,
        cpuMaxFreqKHz = maxFreq,
        gpuGovernor = gpu?.governor,
        gpuMinFreqKHz = gpuMin,
        gpuMaxFreqKHz = gpuMax,
        zramEnabled = memory?.zramSizeBytes?.let { it > 0 },
        ioScheduler = storage?.scheduler,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs
    )
}

fun ProfileEntity.toDomain(): TuningProfile {
    val builtIn = BuiltInProfiles.findById(id)
    if (builtIn != null) {
        return builtIn
    }

    val cpuMap = mutableMapOf<Int, CpuClusterProfileConfig>()
    cpuGovernor?.let { gov ->
        cpuMap[0] = CpuClusterProfileConfig(
            clusterId = 0,
            governor = gov,
            minFreqTarget = cpuMinFreqKHz?.let { FrequencyTarget.AbsoluteKHz(it) },
            maxFreqTarget = cpuMaxFreqKHz?.let { FrequencyTarget.AbsoluteKHz(it) }
        )
    }

    val gpuConfig = if (gpuGovernor != null || gpuMinFreqKHz != null || gpuMaxFreqKHz != null) {
        GpuProfileConfig(
            governor = gpuGovernor,
            minFreqTarget = gpuMinFreqKHz?.let { FrequencyTarget.AbsoluteKHz(it) },
            maxFreqTarget = gpuMaxFreqKHz?.let { FrequencyTarget.AbsoluteKHz(it) }
        )
    } else null

    val memConfig = if (zramEnabled != null) {
        MemoryProfileConfig(
            zramSizeBytes = if (zramEnabled == true) 2L * 1024 * 1024 * 1024 else 0L
        )
    } else null

    val storageConfig = if (ioScheduler != null) {
        StorageProfileConfig(scheduler = ioScheduler)
    } else null

    return TuningProfile(
        id = id,
        name = name,
        description = "Custom profile: $name",
        mode = ProfileMode.CUSTOM,
        isBuiltIn = false,
        cpuClusters = cpuMap,
        gpu = gpuConfig,
        memory = memConfig,
        storage = storageConfig,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs
    )
}
