package com.mahin.hytune.domain.fps.parser

import com.mahin.hytune.domain.fps.model.FpsSource
import com.mahin.hytune.domain.fps.model.FrameMetricsSnapshot
import com.mahin.hytune.domain.fps.model.SurfaceFlingerParseResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class SurfaceFlingerLatencyParser @Inject constructor() {

    companion object {
        const val DEFAULT_REFRESH_PERIOD_NS = 16_666_666L // 60 Hz
        private const val SENTINEL_UNPRESENTED = Long.MAX_VALUE
    }

    fun parse(
        rawOutput: String,
        packageName: String = "",
        lastSeenTimestampNs: Long = 0L
    ): SurfaceFlingerParseResult {
        val lines = rawOutput.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (lines.size < 2) {
            return SurfaceFlingerParseResult(
                snapshot = FrameMetricsSnapshot(
                    fps = 0.0,
                    targetFps = 60,
                    frameTimeMs = 0.0,
                    jankCount = 0,
                    totalFrames = 0,
                    jankPercentage = 0.0,
                    refreshPeriodNs = DEFAULT_REFRESH_PERIOD_NS,
                    source = FpsSource.SURFACE_FLINGER,
                    packageName = packageName
                ),
                latestTimestampNs = lastSeenTimestampNs
            )
        }

        // Line 0 is refresh period in nanoseconds
        val refreshPeriodNs = lines[0].toLongOrNull()?.takeIf { it > 0 } ?: DEFAULT_REFRESH_PERIOD_NS
        val targetFps = (1_000_000_000.0 / refreshPeriodNs).roundToInt().coerceAtLeast(1)

        val validTimestamps = mutableListOf<Long>()
        for (i in 1 until lines.size) {
            val tokens = lines[i].split("\\s+".toRegex())
            if (tokens.size >= 2) {
                val actualPresentTimeNs = tokens[1].toLongOrNull() ?: continue
                if (actualPresentTimeNs <= 0L || actualPresentTimeNs >= SENTINEL_UNPRESENTED) {
                    continue
                }
                if (actualPresentTimeNs > lastSeenTimestampNs) {
                    validTimestamps.add(actualPresentTimeNs)
                }
            }
        }

        if (validTimestamps.isEmpty()) {
            return SurfaceFlingerParseResult(
                snapshot = FrameMetricsSnapshot(
                    fps = 0.0,
                    targetFps = targetFps,
                    frameTimeMs = 0.0,
                    jankCount = 0,
                    totalFrames = 0,
                    jankPercentage = 0.0,
                    refreshPeriodNs = refreshPeriodNs,
                    source = FpsSource.SURFACE_FLINGER,
                    packageName = packageName
                ),
                latestTimestampNs = lastSeenTimestampNs
            )
        }

        val sortedTimestamps = validTimestamps.sorted()
        val latestTimestamp = sortedTimestamps.last()

        val deltas = mutableListOf<Long>()
        if (lastSeenTimestampNs > 0L) {
            val firstDelta = sortedTimestamps.first() - lastSeenTimestampNs
            if (firstDelta > 0L) {
                deltas.add(firstDelta)
            }
        }
        for (i in 1 until sortedTimestamps.size) {
            val delta = sortedTimestamps[i] - sortedTimestamps[i - 1]
            if (delta > 0L) {
                deltas.add(delta)
            }
        }

        val jankThresholdNs = (refreshPeriodNs * 1.5).toLong()
        val jankCount = deltas.count { it > jankThresholdNs }

        val elapsedNs = when {
            lastSeenTimestampNs > 0L -> latestTimestamp - lastSeenTimestampNs
            sortedTimestamps.size > 1 -> latestTimestamp - sortedTimestamps.first()
            else -> refreshPeriodNs
        }

        val calculatedFps = if (elapsedNs > 0L) {
            val raw = (sortedTimestamps.size.toDouble() * 1_000_000_000.0) / elapsedNs.toDouble()
            raw.coerceAtMost(targetFps * 1.25)
        } else {
            0.0
        }

        val avgFrameTimeMs = if (deltas.isNotEmpty()) {
            deltas.average() / 1_000_000.0
        } else {
            refreshPeriodNs / 1_000_000.0
        }

        val totalFrames = sortedTimestamps.size
        val jankPct = if (totalFrames > 0) {
            (jankCount.toDouble() / totalFrames.toDouble()) * 100.0
        } else {
            0.0
        }

        val snapshot = FrameMetricsSnapshot(
            fps = (calculatedFps * 10.0).roundToInt() / 10.0,
            targetFps = targetFps,
            frameTimeMs = (avgFrameTimeMs * 100.0).roundToInt() / 100.0,
            jankCount = jankCount,
            totalFrames = totalFrames,
            jankPercentage = (jankPct * 10.0).roundToInt() / 10.0,
            refreshPeriodNs = refreshPeriodNs,
            source = FpsSource.SURFACE_FLINGER,
            packageName = packageName
        )

        return SurfaceFlingerParseResult(
            snapshot = snapshot,
            latestTimestampNs = latestTimestamp
        )
    }
}
