package com.mahin.hytune.domain.cpu.model

data class CpuCoreState(
    val coreId: Int,
    val isOnline: Boolean,
    val isConfigurable: Boolean
)
