package com.mahin.hytune.domain.profile.model

data class StorageProfileConfig(
    val scheduler: String? = null,
    val readAheadKb: Int? = null,
    val runTrimBeforeApply: Boolean = false
)
