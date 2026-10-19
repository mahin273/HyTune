package com.mahin.hytune.domain.benchmark.analytics

import com.mahin.hytune.domain.benchmark.model.BenchmarkHistogramBin
import com.mahin.hytune.domain.benchmark.model.BenchmarkSample
import com.mahin.hytune.domain.benchmark.model.BenchmarkSessionReport
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class BenchmarkAnalyticsCalculator @Inject constructor() {

    fun calculateReport(
        packageName: String,
        appName: String,
        startTimeEpochMs: Long,
        endTimeEpochMs: Long,
        durationSeconds: Int,
        frameTimesMs: List<Double>,
        samples: List<BenchmarkSample>,
        targetRefreshPeriodNs: Long = 16_666_666L // 60 Hz default
    ): BenchmarkSessionReport {
        val totalFrames = frameTimesMs.size
        val effectiveDuration = durationSeconds.coerceAtLeast(1)

        val avgFps = when {
            totalFrames > 0 && effectiveDuration > 0 -> {
                (totalFrames.toDouble() / effectiveDuration.toDouble())
            }
            samples.isNotEmpty() -> {
                val nonZero = samples.map { it.fps }.filter { it > 0.0 }
                if (nonZero.isNotEmpty()) nonZero.average() else 0.0
            }
            else -> 0.0
        }

        val sortedTimes = frameTimesMs.sorted()
        val (onePercentLow, pointOnePercentLow) = if (sortedTimes.isNotEmpty()) {
            val n = sortedTimes.size
            val idx99 = ((n - 1) * 0.99).toInt().coerceIn(0, n - 1)
            val idx999 = ((n - 1) * 0.999).toInt().coerceIn(0, n - 1)
            val time99 = sortedTimes[idx99]
            val time999 = sortedTimes[idx999]
            val low1 = if (time99 > 0.0) 1000.0 / time99 else 0.0
            val low01 = if (time999 > 0.0) 1000.0 / time999 else 0.0
            Pair(low1, low01)
        } else {
            Pair(0.0, 0.0)
        }

        val sampleFpsList = samples.map { it.fps }.filter { it > 0.0 }
        val minFps = sampleFpsList.minOrNull() ?: if (sortedTimes.isNotEmpty() && sortedTimes.last() > 0) {
            1000.0 / sortedTimes.last()
        } else 0.0

        val maxFps = sampleFpsList.maxOrNull() ?: if (sortedTimes.isNotEmpty() && sortedTimes.first() > 0) {
            1000.0 / sortedTimes.first()
        } else 0.0

        val jankThresholdMs = (targetRefreshPeriodNs / 1_000_000.0) * 1.5
        val jankCount = frameTimesMs.count { it > jankThresholdMs }
        val jankPercentage = if (totalFrames > 0) {
            (jankCount.toDouble() / totalFrames.toDouble()) * 100.0
        } else {
            0.0
        }

        val avgFrameTimeMs = if (frameTimesMs.isNotEmpty()) {
            frameTimesMs.average()
        } else {
            0.0
        }

        val avgCpuLoad = if (samples.isNotEmpty()) {
            samples.map { it.cpuLoadPercent }.average().roundToInt()
        } else 0

        val avgGpuLoad = if (samples.isNotEmpty()) {
            samples.map { it.gpuLoadPercent }.average().roundToInt()
        } else 0

        val nonZeroWatts = samples.map { it.batteryPowerWatts }.filter { it > 0f }
        val avgBatteryWatts = if (nonZeroWatts.isNotEmpty()) {
            nonZeroWatts.average().toFloat()
        } else 0f

        val peakTemp = samples.maxOfOrNull { it.temperatureC } ?: 0f

        val histogram = calculateHistogram(frameTimesMs)

        return BenchmarkSessionReport(
            id = 0L,
            packageName = packageName,
            appName = appName,
            startTimeEpochMs = startTimeEpochMs,
            endTimeEpochMs = endTimeEpochMs,
            durationSeconds = effectiveDuration,
            avgFps = (avgFps * 10.0).roundToInt() / 10.0,
            onePercentLowFps = (onePercentLow * 10.0).roundToInt() / 10.0,
            pointOnePercentLowFps = (pointOnePercentLow * 10.0).roundToInt() / 10.0,
            minFps = (minFps * 10.0).roundToInt() / 10.0,
            maxFps = (maxFps * 10.0).roundToInt() / 10.0,
            totalFrames = totalFrames,
            jankCount = jankCount,
            jankPercentage = (jankPercentage * 10.0).roundToInt() / 10.0,
            stutterScore = (jankPercentage * 10.0).roundToInt() / 10.0,
            avgFrameTimeMs = (avgFrameTimeMs * 100.0).roundToInt() / 100.0,
            avgCpuLoadPercent = avgCpuLoad,
            avgGpuLoadPercent = avgGpuLoad,
            avgBatteryWatts = (avgBatteryWatts * 100f).roundToInt() / 100f,
            peakTemperatureC = (peakTemp * 10f).roundToInt() / 10f,
            histogram = histogram,
            telemetrySeries = samples
        )
    }

    fun calculateHistogram(frameTimesMs: List<Double>): List<BenchmarkHistogramBin> {
        val total = frameTimesMs.size
        if (total == 0) return emptyList()

        var binUnder8 = 0
        var bin8To16 = 0
        var bin16To33 = 0
        var bin33To50 = 0
        var binOver50 = 0

        for (time in frameTimesMs) {
            when {
                time < 8.33 -> binUnder8++
                time < 16.67 -> bin8To16++
                time < 33.34 -> bin16To33++
                time < 50.0 -> bin33To50++
                else -> binOver50++
            }
        }

        fun toPct(count: Int): Double = (count.toDouble() / total.toDouble()) * 100.0

        return listOf(
            BenchmarkHistogramBin("< 8.3 ms", binUnder8, (toPct(binUnder8) * 10.0).roundToInt() / 10.0),
            BenchmarkHistogramBin("8.3 - 16.7 ms", bin8To16, (toPct(bin8To16) * 10.0).roundToInt() / 10.0),
            BenchmarkHistogramBin("16.7 - 33.3 ms", bin16To33, (toPct(bin16To33) * 10.0).roundToInt() / 10.0),
            BenchmarkHistogramBin("33.3 - 50.0 ms", bin33To50, (toPct(bin33To50) * 10.0).roundToInt() / 10.0),
            BenchmarkHistogramBin("> 50.0 ms", binOver50, (toPct(binOver50) * 10.0).roundToInt() / 10.0)
        )
    }
}
