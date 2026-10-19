package com.mahin.hytune.domain.benchmark.model

import kotlinx.serialization.Serializable

@Serializable
data class BenchmarkHistogramBin(
    val rangeLabel: String,
    val count: Int,
    val percentage: Double
)
