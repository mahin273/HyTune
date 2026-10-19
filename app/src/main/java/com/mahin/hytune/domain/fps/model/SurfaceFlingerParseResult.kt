package com.mahin.hytune.domain.fps.model

data class SurfaceFlingerParseResult(
    val snapshot: FrameMetricsSnapshot,
    val latestTimestampNs: Long
)
