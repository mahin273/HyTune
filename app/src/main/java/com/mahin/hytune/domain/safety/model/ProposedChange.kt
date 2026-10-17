package com.mahin.hytune.domain.safety.model

data class ProposedChange(
    val subsystem: String,
    val parameter: String,
    val currentValue: String,
    val targetValue: String,
    val riskLevel: RiskLevel,
    val isWhitelisted: Boolean
)
