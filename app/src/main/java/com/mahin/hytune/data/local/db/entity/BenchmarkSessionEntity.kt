package com.mahin.hytune.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mahin.hytune.domain.benchmark.model.BenchmarkHistogramBin
import com.mahin.hytune.domain.benchmark.model.BenchmarkSample
import com.mahin.hytune.domain.benchmark.model.BenchmarkSessionReport
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(
    tableName = "benchmark_sessions",
    indices = [
        Index("startTimeEpochMs"),
        Index("packageName")
    ]
)
data class BenchmarkSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val packageName: String,
    val appName: String,
    val startTimeEpochMs: Long,
    val endTimeEpochMs: Long,
    val durationSeconds: Int,
    val avgFps: Double,
    val onePercentLowFps: Double,
    val pointOnePercentLowFps: Double,
    val minFps: Double,
    val maxFps: Double,
    val totalFrames: Int,
    val jankCount: Int,
    val jankPercentage: Double,
    val stutterScore: Double,
    val avgFrameTimeMs: Double,
    val avgCpuLoadPercent: Int,
    val avgGpuLoadPercent: Int,
    val avgBatteryWatts: Float,
    val peakTemperatureC: Float,
    val histogramJson: String,
    val telemetrySeriesJson: String
) {
    fun toDomain(json: Json = Json { ignoreUnknownKeys = true }): BenchmarkSessionReport {
        val histogramList = try {
            json.decodeFromString<List<BenchmarkHistogramBin>>(histogramJson)
        } catch (_: Exception) {
            emptyList()
        }
        val seriesList = try {
            json.decodeFromString<List<BenchmarkSample>>(telemetrySeriesJson)
        } catch (_: Exception) {
            emptyList()
        }

        return BenchmarkSessionReport(
            id = id,
            packageName = packageName,
            appName = appName,
            startTimeEpochMs = startTimeEpochMs,
            endTimeEpochMs = endTimeEpochMs,
            durationSeconds = durationSeconds,
            avgFps = avgFps,
            onePercentLowFps = onePercentLowFps,
            pointOnePercentLowFps = pointOnePercentLowFps,
            minFps = minFps,
            maxFps = maxFps,
            totalFrames = totalFrames,
            jankCount = jankCount,
            jankPercentage = jankPercentage,
            stutterScore = stutterScore,
            avgFrameTimeMs = avgFrameTimeMs,
            avgCpuLoadPercent = avgCpuLoadPercent,
            avgGpuLoadPercent = avgGpuLoadPercent,
            avgBatteryWatts = avgBatteryWatts,
            peakTemperatureC = peakTemperatureC,
            histogram = histogramList,
            telemetrySeries = seriesList
        )
    }

    companion object {
        fun fromDomain(
            report: BenchmarkSessionReport,
            json: Json = Json { ignoreUnknownKeys = true }
        ): BenchmarkSessionEntity {
            return BenchmarkSessionEntity(
                id = report.id,
                packageName = report.packageName,
                appName = report.appName,
                startTimeEpochMs = report.startTimeEpochMs,
                endTimeEpochMs = report.endTimeEpochMs,
                durationSeconds = report.durationSeconds,
                avgFps = report.avgFps,
                onePercentLowFps = report.onePercentLowFps,
                pointOnePercentLowFps = report.pointOnePercentLowFps,
                minFps = report.minFps,
                maxFps = report.maxFps,
                totalFrames = report.totalFrames,
                jankCount = report.jankCount,
                jankPercentage = report.jankPercentage,
                stutterScore = report.stutterScore,
                avgFrameTimeMs = report.avgFrameTimeMs,
                avgCpuLoadPercent = report.avgCpuLoadPercent,
                avgGpuLoadPercent = report.avgGpuLoadPercent,
                avgBatteryWatts = report.avgBatteryWatts,
                peakTemperatureC = report.peakTemperatureC,
                histogramJson = json.encodeToString(report.histogram),
                telemetrySeriesJson = json.encodeToString(report.telemetrySeries)
            )
        }
    }
}
