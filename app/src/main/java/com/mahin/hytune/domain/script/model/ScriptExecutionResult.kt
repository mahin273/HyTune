package com.mahin.hytune.domain.script.model

data class ScriptExecutionResult(
    val exitCode: Int,
    val output: String,
    val errorOutput: String,
    val durationMs: Long,
    val isTimedOut: Boolean = false
) {
    val isSuccess: Boolean get() = exitCode == 0 && !isTimedOut
}
