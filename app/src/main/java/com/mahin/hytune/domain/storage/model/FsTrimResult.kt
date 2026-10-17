package com.mahin.hytune.domain.storage.model

data class FsTrimResult(
    val mountPoint: String,
    val trimmedBytes: Long,
    val rawOutput: String,
    val isSuccess: Boolean
)
