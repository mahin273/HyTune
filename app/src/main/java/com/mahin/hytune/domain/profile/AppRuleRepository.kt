package com.mahin.hytune.domain.profile

import com.mahin.hytune.data.local.db.dao.AppRuleDao
import com.mahin.hytune.data.local.db.entity.AppRuleEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRuleRepository @Inject constructor(
    private val appRuleDao: AppRuleDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    fun getAllRules(): Flow<List<AppRuleEntity>> {
        return appRuleDao.getAllAppRules().flowOn(ioDispatcher)
    }

    fun getRule(packageName: String): Flow<AppRuleEntity?> {
        return appRuleDao.getRuleForPackage(packageName).flowOn(ioDispatcher)
    }

    suspend fun getRuleDirect(packageName: String): AppRuleEntity? = withContext(ioDispatcher) {
        appRuleDao.getRuleForPackageDirect(packageName)
    }

    suspend fun upsertRule(rule: AppRuleEntity): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            appRuleDao.upsertAppRule(rule)
        }
    }

    suspend fun deleteRule(packageName: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            appRuleDao.deleteByPackageName(packageName)
            Unit
        }
    }

    suspend fun setGame(packageName: String, isGame: Boolean): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val existing = appRuleDao.getRuleForPackageDirect(packageName)
            val updated = existing?.copy(isGame = isGame)
                ?: AppRuleEntity(
                    packageName = packageName,
                    profileId = "performance",
                    isGame = isGame
                )
            appRuleDao.upsertAppRule(updated)
        }
    }
}
