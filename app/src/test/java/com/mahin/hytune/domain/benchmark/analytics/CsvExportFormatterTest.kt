package com.mahin.hytune.domain.benchmark.analytics

import com.mahin.hytune.domain.benchmark.model.BenchmarkSample
import com.mahin.hytune.domain.benchmark.model.BenchmarkSessionReport
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CsvExportFormatterTest {

    private lateinit var formatter: CsvExportFormatter

    @Before
    fun setUp() {
        formatter = CsvExportFormatter()
    }

    @Test
    fun formatReportToCsv_generatesHeadersAndSampleRows() {
        val sample = BenchmarkSample(
            timestampEpochMs = 1718000000000L,
            relativeTimeSec = 1,
            fps = 59.8,
            frameTimeMs = 16.72,
            cpuLoadPercent = 35,
            cpuFreqMhz = 2200,
            gpuLoadPercent = 55,
            gpuFreqMhz = 650,
            batteryPowerWatts = 4.25f,
            temperatureC = 39.5f,
            ramUsedMb = 3100
        )

        val report = BenchmarkSessionReport(
            id = 1L,
            packageName = "com.test.pkg",
            appName = "Test App",
            startTimeEpochMs = 1718000000000L,
            endTimeEpochMs = 1718000060000L,
            durationSeconds = 60,
            avgFps = 59.5,
            onePercentLowFps = 45.2,
            pointOnePercentLowFps = 31.0,
            minFps = 25.0,
            maxFps = 61.0,
            totalFrames = 3570,
            jankCount = 12,
            jankPercentage = 0.3,
            stutterScore = 0.3,
            avgFrameTimeMs = 16.8,
            avgCpuLoadPercent = 35,
            avgGpuLoadPercent = 55,
            avgBatteryWatts = 4.25f,
            peakTemperatureC = 39.5f,
            histogram = emptyList(),
            telemetrySeries = listOf(sample)
        )

        val csv = formatter.formatReportToCsv(report)

        assertTrue(csv.contains("# HyTune Benchmark Report"))
        assertTrue(csv.contains("# App: Test App (com.test.pkg)"))
        assertTrue(csv.contains("TimestampMs,RelativeTimeSec,FPS,FrameTimeMs,CpuLoadPercent,CpuFreqMhz,GpuLoadPercent,GpuFreqMhz,BatteryPowerWatts,TemperatureC,RamUsedMb"))
        assertTrue(csv.contains("1718000000000,1,59.8,16.72,35,2200,55,650,4.25,39.5,3100"))
    }
}
