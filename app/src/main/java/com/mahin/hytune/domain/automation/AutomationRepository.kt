package com.mahin.hytune.domain.automation

import com.mahin.hytune.data.local.db.dao.AutomationRuleDao
import com.mahin.hytune.data.local.db.entity.AutomationRuleEntity
import com.mahin.hytune.domain.automation.model.AutomationRule
import com.mahin.hytune.domain.automation.model.SystemStateSnapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomationRepository @Inject constructor(
    private val ruleDao: AutomationRuleDao,
    private val rulesEngine: AutomationRulesEngine,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    val isEngineRunning = rulesEngine.isRunning
    val currentSystemState = rulesEngine.currentSystemState
    val lastExecutedRules = rulesEngine.lastExecutedRules

    fun getAllRules(): Flow<List<AutomationRule>> {
        return ruleDao.getAllRules()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(ioDispatcher)
    }

    fun getEnabledRules(): Flow<List<AutomationRule>> {
        return ruleDao.getEnabledRules()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(ioDispatcher)
    }

    suspend fun getRuleById(id: Long): AutomationRule? = withContext(ioDispatcher) {
        ruleDao.getRuleById(id)?.toDomain()
    }

    suspend fun insertRule(rule: AutomationRule): Long = withContext(ioDispatcher) {
        ruleDao.insertRule(AutomationRuleEntity.fromDomain(rule))
    }

    suspend fun updateRule(rule: AutomationRule) = withContext(ioDispatcher) {
        ruleDao.updateRule(AutomationRuleEntity.fromDomain(rule))
    }

    suspend fun deleteRule(id: Long) = withContext(ioDispatcher) {
        ruleDao.deleteRuleById(id)
    }

    suspend fun setRuleEnabled(id: Long, isEnabled: Boolean) = withContext(ioDispatcher) {
        ruleDao.updateEnabled(id, isEnabled)
    }

    fun startEngine() {
        rulesEngine.start()
    }

    fun stopEngine() {
        rulesEngine.stop()
    }

    suspend fun evaluateState(state: SystemStateSnapshot) {
        rulesEngine.evaluateState(state)
    }
}
