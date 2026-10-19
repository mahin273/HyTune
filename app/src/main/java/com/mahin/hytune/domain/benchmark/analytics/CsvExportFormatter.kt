package com.mahin.hytune.domain.benchmark.analytics

import com.mahin.hytune.domain.benchmark.model.BenchmarkSessionReport
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CsvExportFormatter @Inject constructor() {

    fun formatReportToCsv(report: BenchmarkSessionReport): String {
        val sb = StringBuilder()
        sb.append("# HyTune Benchmark Report\n")
        sb.append("# App: ${report.appName} (${report.packageName})\n")
        sb.append(
            String.format(
                Locale.US,
                "# Duration: %ds | Avg FPS: %.1f | 1%% Low: %.1f | 0.1%% Low: %.1f | Stutter: %.1f%%\n",
                report.durationSeconds,
                report.avgFps,
                report.onePercentLowFps,
                report.pointOnePercentLowFps,
                report.stutterScore
            )
        )
        sb.append("TimestampMs,RelativeTimeSec,FPS,FrameTimeMs,CpuLoadPercent,CpuFreqMhz,GpuLoadPercent,GpuFreqMhz,BatteryPowerWatts,TemperatureC,RamUsedMb\n")

        for (sample in report.telemetrySeries) {
            sb.append(
                String.format(
                    Locale.US,
                    "%d,%d,%.1f,%.2f,%d,%d,%d,%d,%.2f,%.1f,%d\n",
                    sample.timestampEpochMs,
                    sample.relativeTimeSec,
                    sample.fps,
                    sample.frameTimeMs,
                    sample.cpuLoadPercent,
                    sample.cpuFreqMhz,
                    sample.gpuLoadPercent,
                    sample.gpuFreqMhz,
                    sample.batteryPowerWatts,
                    sample.temperatureC,
                    sample.ramUsedMb
                )
            )
        }

        return sb.toString()
    }
}
