package com.mahin.hytune.domain.benchmark.analytics

import com.mahin.hytune.domain.benchmark.model.BenchmarkSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BenchmarkAnalyticsCalculatorTest {

    private lateinit var calculator: BenchmarkAnalyticsCalculator

    @Before
    fun setUp() {
        calculator = BenchmarkAnalyticsCalculator()
    }

    @Test
    fun calculateReport_withEmptyFramesAndEmptySamples_returnsZeroMetrics() {
        val report = calculator.calculateReport(
            packageName = "com.test.game",
            appName = "Test Game",
            startTimeEpochMs = 1000L,
            endTimeEpochMs = 11000L,
            durationSeconds = 10,
            frameTimesMs = emptyList(),
            samples = emptyList()
        )

        assertEquals("com.test.game", report.packageName)
        assertEquals("Test Game", report.appName)
        assertEquals(0.0, report.avgFps, 0.01)
        assertEquals(0.0, report.onePercentLowFps, 0.01)
        assertEquals(0.0, report.pointOnePercentLowFps, 0.01)
        assertEquals(0.0, report.minFps, 0.01)
        assertEquals(0.0, report.maxFps, 0.01)
        assertEquals(0, report.totalFrames)
        assertEquals(0, report.jankCount)
        assertEquals(0.0, report.jankPercentage, 0.01)
        assertEquals(0.0, report.stutterScore, 0.01)
        assertEquals(0.0, report.avgFrameTimeMs, 0.01)
        assertEquals(0, report.avgCpuLoadPercent)
        assertEquals(0, report.avgGpuLoadPercent)
        assertEquals(0f, report.avgBatteryWatts, 0.01f)
        assertEquals(0f, report.peakTemperatureC, 0.01f)
        assertTrue(report.histogram.isEmpty())
        assertTrue(report.telemetrySeries.isEmpty())
    }

    @Test
    fun calculateReport_with1000Frames_computesCorrectPercentilesAndJanks() {
        // 980 frames of 16.66 ms (60 FPS)
        // 19 frames of 33.33 ms (30 FPS, > 25.0 ms threshold -> janks)
        // 1 frame of 50.0 ms (20 FPS -> worst frame)
        val frames = mutableListOf<Double>()
        repeat(980) { frames.add(16.66) }
        repeat(19) { frames.add(33.33) }
        frames.add(50.0)

        val report = calculator.calculateReport(
            packageName = "com.test.game",
            appName = "Test Game",
            startTimeEpochMs = 0L,
            endTimeEpochMs = 17000L,
            durationSeconds = 17,
            frameTimesMs = frames,
            samples = emptyList()
        )

        assertEquals(1000, report.totalFrames)
        // 1000 frames / 17 seconds ~ 58.8 FPS
        assertEquals(58.8, report.avgFps, 0.1)

        // 99th percentile index = 999 * 0.99 = 989 -> 33.33 ms -> 1000 / 33.33 ~ 30.0 FPS
        assertEquals(30.0, report.onePercentLowFps, 0.1)

        // 99.9th percentile index = 999 * 0.999 = 998 -> 33.33 ms
        assertTrue(report.pointOnePercentLowFps > 0.0)
        assertTrue(report.pointOnePercentLowFps <= report.onePercentLowFps)

        // Jank frames: 19 frames of 33.33 + 1 frame of 50.0 = 20 frames > 25.0ms
        assertEquals(20, report.jankCount)
        assertEquals(2.0, report.jankPercentage, 0.01)
        assertEquals(2.0, report.stutterScore, 0.01)
    }

    @Test
    fun calculateHistogram_categorizesIntoFiveBinsCorrectly() {
        val frameTimes = listOf(
            6.0, 7.5,           // < 8.33 ms (2 frames)
            10.0, 12.0, 16.0,   // 8.3 - 16.7 ms (3 frames)
            20.0, 25.0, 30.0,   // 16.7 - 33.3 ms (3 frames)
            35.0, 40.0,         // 33.3 - 50.0 ms (2 frames)
            55.0, 60.0          // > 50.0 ms (2 frames)
        ) // Total 12 frames

        val bins = calculator.calculateHistogram(frameTimes)
        assertEquals(5, bins.size)

        assertEquals("< 8.3 ms", bins[0].rangeLabel)
        assertEquals(2, bins[0].count)
        assertEquals(16.7, bins[0].percentage, 0.1)

        assertEquals("8.3 - 16.7 ms", bins[1].rangeLabel)
        assertEquals(3, bins[1].count)
        assertEquals(25.0, bins[1].percentage, 0.1)

        assertEquals("16.7 - 33.3 ms", bins[2].rangeLabel)
        assertEquals(3, bins[2].count)
        assertEquals(25.0, bins[2].percentage, 0.1)

        assertEquals("33.3 - 50.0 ms", bins[3].rangeLabel)
        assertEquals(2, bins[3].count)
        assertEquals(16.7, bins[3].percentage, 0.1)

        assertEquals("> 50.0 ms", bins[4].rangeLabel)
        assertEquals(2, bins[4].count)
        assertEquals(16.7, bins[4].percentage, 0.1)
    }

    @Test
    fun calculateReport_withTelemetrySeries_aggregatesAveragesAndPeakTemp() {
        val samples = listOf(
            BenchmarkSample(
                timestampEpochMs = 1000L,
                relativeTimeSec = 1,
                fps = 59.0,
                frameTimeMs = 16.9,
                cpuLoadPercent = 40,
                cpuFreqMhz = 2400,
                gpuLoadPercent = 60,
                gpuFreqMhz = 600,
                batteryPowerWatts = 4.5f,
                temperatureC = 38.0f,
                ramUsedMb = 3200
            ),
            BenchmarkSample(
                timestampEpochMs = 2000L,
                relativeTimeSec = 2,
                fps = 61.0,
                frameTimeMs = 16.4,
                cpuLoadPercent = 50,
                cpuFreqMhz = 2800,
                gpuLoadPercent = 80,
                gpuFreqMhz = 800,
                batteryPowerWatts = 5.5f,
                temperatureC = 42.5f,
                ramUsedMb = 3300
            )
        )

        val report = calculator.calculateReport(
            packageName = "com.test.game",
            appName = "Test Game",
            startTimeEpochMs = 1000L,
            endTimeEpochMs = 2000L,
            durationSeconds = 2,
            frameTimesMs = emptyList(),
            samples = samples
        )

        // Fallback to sample average when frameTimesMs is empty
        assertEquals(60.0, report.avgFps, 0.1)
        assertEquals(45, report.avgCpuLoadPercent)
        assertEquals(70, report.avgGpuLoadPercent)
        assertEquals(5.0f, report.avgBatteryWatts, 0.01f)
        assertEquals(42.5f, report.peakTemperatureC, 0.01f)
        assertEquals(59.0, report.minFps, 0.1)
        assertEquals(61.0, report.maxFps, 0.1)
    }
}
