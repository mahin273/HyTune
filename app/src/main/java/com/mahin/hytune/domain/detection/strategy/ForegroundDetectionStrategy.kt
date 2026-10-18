package com.mahin.hytune.domain.detection.strategy

import com.mahin.hytune.domain.detection.model.ForegroundInfo

interface ForegroundDetectionStrategy {
    suspend fun detect(): ForegroundInfo?
}
