package com.mahin.hytune.domain.script.model

data class CustomScript(
    val id: Long = 0,
    val name: String,
    val scriptContent: String,
    val runAtBoot: Boolean = false,
    val runAsRoot: Boolean = true,
    val lastExecutedEpochMs: Long = 0,
    val lastExitCode: Int? = null,
    val lastOutput: String? = null,
    val lastDurationMs: Long = 0,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)
