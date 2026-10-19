package com.mahin.hytune.domain.script.engine

import com.mahin.hytune.core.privilege.PrivilegeBackend
import com.mahin.hytune.domain.script.model.ScriptExecutionResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScriptExecutionEngine @Inject constructor(
    private val privilegeBackend: PrivilegeBackend,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    companion object {
        const val DEFAULT_TIMEOUT_MS = 30_000L

        private val DANGEROUS_PATTERNS = listOf(
            Pattern.compile("""\brm\s+(-[a-zA-Z]*r[a-zA-Z]*f?|-rf|-fr)\s+(/|/\*|/system|/data|/vendor|/boot|/recovery)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bdd\s+.*of=/dev/block""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bmkfs(\.[a-zA-Z0-9]+)?\s+/dev/block""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bfdisk\s+/dev/block""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bformat\s+/dev/block""", Pattern.CASE_INSENSITIVE)
        )
    }

    fun isSafeCommand(scriptContent: String): Boolean {
        for (pattern in DANGEROUS_PATTERNS) {
            if (pattern.matcher(scriptContent).find()) {
                return false
            }
        }
        return true
    }

    suspend fun executeScript(
        scriptContent: String,
        runAsRoot: Boolean = true,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): Result<ScriptExecutionResult> = withContext(ioDispatcher) {
        val trimmed = scriptContent.trim()
        if (trimmed.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Script content cannot be empty"))
        }

        if (!isSafeCommand(trimmed)) {
            return@withContext Result.failure(
                SecurityException("Blocked potentially destructive command pattern targeting root directory or block storage")
            )
        }

        val startTime = System.currentTimeMillis()

        val execResult = withTimeoutOrNull(timeoutMs) {
            try {
                privilegeBackend.exec(trimmed)
            } catch (e: Exception) {
                null
            }
        }

        val duration = System.currentTimeMillis() - startTime

        if (execResult == null) {
            val timeoutResult = ScriptExecutionResult(
                exitCode = -1,
                output = "",
                errorOutput = "Execution timed out after ${timeoutMs}ms",
                durationMs = duration,
                isTimedOut = true
            )
            return@withContext Result.success(timeoutResult)
        }

        val result = ScriptExecutionResult(
            exitCode = execResult.exitCode,
            output = execResult.output.trim(),
            errorOutput = execResult.errorOutput.trim(),
            durationMs = duration,
            isTimedOut = false
        )
        Result.success(result)
    }
}
