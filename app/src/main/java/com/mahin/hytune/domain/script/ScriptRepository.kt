package com.mahin.hytune.domain.script

import com.mahin.hytune.data.local.db.dao.CustomScriptDao
import com.mahin.hytune.data.local.db.entity.CustomScriptEntity
import com.mahin.hytune.domain.script.engine.ScriptExecutionEngine
import com.mahin.hytune.domain.script.model.CustomScript
import com.mahin.hytune.domain.script.model.ScriptExecutionResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScriptRepository @Inject constructor(
    private val customScriptDao: CustomScriptDao,
    private val scriptExecutionEngine: ScriptExecutionEngine,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    fun getAllScripts(): Flow<List<CustomScript>> =
        customScriptDao.getAllScripts()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    suspend fun getScriptById(id: Long): CustomScript? = withContext(ioDispatcher) {
        customScriptDao.getScriptById(id)?.toDomain()
    }

    suspend fun getBootScripts(): List<CustomScript> = withContext(ioDispatcher) {
        customScriptDao.getBootScripts().map { it.toDomain() }
    }

    suspend fun saveScript(script: CustomScript): Long = withContext(ioDispatcher) {
        if (script.id == 0L) {
            customScriptDao.insertScript(CustomScriptEntity.fromDomain(script))
        } else {
            customScriptDao.updateScript(CustomScriptEntity.fromDomain(script))
            script.id
        }
    }

    suspend fun deleteScript(id: Long) = withContext(ioDispatcher) {
        customScriptDao.deleteScriptById(id)
    }

    suspend fun setRunAtBoot(id: Long, runAtBoot: Boolean) = withContext(ioDispatcher) {
        customScriptDao.updateRunAtBoot(id, runAtBoot)
    }

    suspend fun executeScript(id: Long): Result<ScriptExecutionResult> = withContext(ioDispatcher) {
        val script = customScriptDao.getScriptById(id)?.toDomain()
            ?: return@withContext Result.failure(NoSuchElementException("Script with id $id not found"))

        val result = scriptExecutionEngine.executeScript(
            scriptContent = script.scriptContent,
            runAsRoot = script.runAsRoot
        )

        result.onSuccess { execResult ->
            val logOutput = when {
                execResult.output.isNotEmpty() -> execResult.output
                execResult.errorOutput.isNotEmpty() -> execResult.errorOutput
                else -> "Success (exit code 0)"
            }
            customScriptDao.updateExecutionResult(
                id = id,
                exitCode = execResult.exitCode,
                output = logOutput,
                durationMs = execResult.durationMs,
                timestamp = System.currentTimeMillis()
            )
        }

        result
    }

    suspend fun executeRawScript(
        content: String,
        runAsRoot: Boolean = true
    ): Result<ScriptExecutionResult> = withContext(ioDispatcher) {
        scriptExecutionEngine.executeScript(content, runAsRoot)
    }
}
