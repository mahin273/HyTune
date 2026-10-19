package com.mahin.hytune.domain.fps.parser

import com.mahin.hytune.domain.fps.model.FpsSource
import com.mahin.hytune.domain.fps.model.FrameMetricsSnapshot
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class GfxinfoFramestatsParser @Inject constructor() {

    companion object {
        const val DEFAULT_REFRESH_PERIOD_NS = 16_666_666L // 60 Hz
        private const val PROFILE_DATA_HEADER = "---PROFILEDATA---"
    }

    fun parse(
        rawOutput: String,
        packageName: String = "",
        targetRefreshPeriodNs: Long = DEFAULT_REFRESH_PERIOD_NS
    ): FrameMetricsSnapshot {
        val lines = rawOutput.lines()
            .map { it.trim() }

        val startIndex = lines.indexOfFirst { it.startsWith(PROFILE_DATA_HEADER) }
        if (startIndex == -1 || startIndex + 2 >= lines.size) {
            return FrameMetricsSnapshot(
                fps = 0.0,
                targetFps = 60,
                frameTimeMs = 0.0,
                jankCount = 0,
                totalFrames = 0,
                jankPercentage = 0.0,
                refreshPeriodNs = targetRefreshPeriodNs,
                source = FpsSource.GFXINFO,
                packageName = packageName
            )
        }

        val headerTokens = lines[startIndex + 1].split(",")
        val flagsIndex = headerTokens.indexOf("Flags").takeIf { it >= 0 } ?: 0
        val intendedVsyncIndex = headerTokens.indexOf("IntendedVsync").takeIf { it >= 0 } ?: 1
        val frameCompletedIndex = headerTokens.indexOf("FrameCompleted").takeIf { it >= 0 } ?: 13

        val validDurationsNs = mutableListOf<Long>()
        val vsyncTimestamps = mutableListOf<Long>()

        for (i in (startIndex + 2) until lines.size) {
            val line = lines[i]
            if (line.startsWith(PROFILE_DATA_HEADER) || line.isBlank()) break

            val tokens = line.split(",")
            if (tokens.size > maxOf(flagsIndex, intendedVsyncIndex, frameCompletedIndex)) {
                val flags = tokens[flagsIndex].trim().toLongOrNull() ?: 0L
                val intendedVsync = tokens[intendedVsyncIndex].trim().toLongOrNull() ?: 0L
                val frameCompleted = tokens[frameCompletedIndex].trim().toLongOrNull() ?: 0L

                // Flags == 0 indicates normal frame rendered without layout/window hierarchy rebuilds
                if (flags == 0L && intendedVsync > 0L && frameCompleted > intendedVsync) {
                    val durationNs = frameCompleted - intendedVsync
                    validDurationsNs.add(durationNs)
                    vsyncTimestamps.add(intendedVsync)
                }
            }
        }

        if (validDurationsNs.isEmpty()) {
            return FrameMetricsSnapshot(
                fps = 0.0,
                targetFps = 60,
                frameTimeMs = 0.0,
                jankCount = 0,
                totalFrames = 0,
                jankPercentage = 0.0,
                refreshPeriodNs = targetRefreshPeriodNs,
                source = FpsSource.GFXINFO,
                packageName = packageName
            )
        }

        val targetFps = (1_000_000_000.0 / targetRefreshPeriodNs).roundToInt().coerceAtLeast(1)
        val jankThresholdNs = (targetRefreshPeriodNs * 1.5).toLong()
        val jankCount = validDurationsNs.count { it > jankThresholdNs }

        val elapsedNs = if (vsyncTimestamps.size > 1) {
            vsyncTimestamps.last() - vsyncTimestamps.first()
        } else {
            targetRefreshPeriodNs * validDurationsNs.size
        }

        val calculatedFps = if (elapsedNs > 0L) {
            val raw = (validDurationsNs.size.toDouble() * 1_000_000_000.0) / elapsedNs.toDouble()
            raw.coerceAtMost(targetFps * 1.25)
        } else {
            0.0
        }

        val avgFrameTimeMs = validDurationsNs.average() / 1_000_000.0
        val jankPct = (jankCount.toDouble() / validDurationsNs.size.toDouble()) * 100.0

        return FrameMetricsSnapshot(
            fps = (calculatedFps * 10.0).roundToInt() / 10.0,
            targetFps = targetFps,
            frameTimeMs = (avgFrameTimeMs * 100.0).roundToInt() / 100.0,
            jankCount = jankCount,
            totalFrames = validDurationsNs.size,
            jankPercentage = (jankPct * 10.0).roundToInt() / 10.0,
            refreshPeriodNs = targetRefreshPeriodNs,
            source = FpsSource.GFXINFO,
            packageName = packageName
        )
    }
}
