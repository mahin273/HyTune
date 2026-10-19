package com.mahin.hytune.domain.boot.model

data class BootApplyResult(
    val success: Boolean,
    val message: String,
    val appliedProfileId: String? = null,
    val executedScriptCount: Int = 0,
    val skippedReason: String? = null
)
