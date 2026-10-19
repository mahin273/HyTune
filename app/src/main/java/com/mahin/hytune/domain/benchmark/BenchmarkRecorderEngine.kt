package com.mahin.hytune.domain.benchmark

import android.content.Context
import com.mahin.hytune.domain.battery.BatteryRepository
import com.mahin.hytune.domain.benchmark.analytics.BenchmarkAnalyticsCalculator
import com.mahin.hytune.domain.benchmark.model.BenchmarkSample
import com.mahin.hytune.domain.benchmark.model.BenchmarkSessionReport
import com.mahin.hytune.domain.cpu.CpuControlEngine
import com.mahin.hytune.domain.fps.FpsSampler
import com.mahin.hytune.domain.gpu.GpuControlEngine
import com.mahin.hytune.domain.thermal.ThermalRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BenchmarkRecorderEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fpsSampler: FpsSampler,
    private val batteryRepository: BatteryRepository,
    private val thermalRepository: ThermalRepository,
    private val cpuControlEngine: CpuControlEngine,
    private val gpuControlEngine: GpuControlEngine,
    private val analyticsCalculator: BenchmarkAnalyticsCalculator,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    var testScopeOverride: CoroutineScope? = null
    private val defaultScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val recorderScope: CoroutineScope get() = testScopeOverride ?: defaultScope
    private var recordingJob: Job? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDurationSeconds = MutableStateFlow(0)
    val recordingDurationSeconds: StateFlow<Int> = _recordingDurationSeconds.asStateFlow()

    private val _currentLiveSample = MutableStateFlow<BenchmarkSample?>(null)
    val currentLiveSample: StateFlow<BenchmarkSample?> = _currentLiveSample.asStateFlow()

    private val _activePackageName = MutableStateFlow("")
    val activePackageName: StateFlow<String> = _activePackageName.asStateFlow()

    private val _activeAppName = MutableStateFlow("")
    val activeAppName: StateFlow<String> = _activeAppName.asStateFlow()

    private val sampleBuffer = mutableListOf<BenchmarkSample>()
    private val frameTimesBuffer = mutableListOf<Double>()
    private var sessionStartTimeMs: Long = 0L

    private var prevTotalJiffies: Long = 0L
    private var prevIdleJiffies: Long = 0L

    fun startRecording(packageName: String, appName: String) {
        if (_isRecording.value) return
        _isRecording.value = true
        _activePackageName.value = packageName
        _activeAppName.value = appName
        _recordingDurationSeconds.value = 0
        sessionStartTimeMs = System.currentTimeMillis()

        synchronized(sampleBuffer) {
            sampleBuffer.clear()
        }
        synchronized(frameTimesBuffer) {
            frameTimesBuffer.clear()
        }

        fpsSampler.start(1000L)

        recordingJob?.cancel()
        recordingJob = recorderScope.launch {
            var elapsedSec = 0
            while (isActive) {
                delay(1000L)
                elapsedSec++
                _recordingDurationSeconds.value = elapsedSec

                val sample = sampleCurrentState(elapsedSec)
                _currentLiveSample.value = sample

                synchronized(sampleBuffer) {
                    sampleBuffer.add(sample)
                }
            }
        }
    }

    suspend fun stopRecording(): BenchmarkSessionReport? = withContext(ioDispatcher) {
        if (!_isRecording.value) return@withContext null
        _isRecording.value = false
        fpsSampler.stop()

        recordingJob?.cancel()
        recordingJob = null

        val endTimeMs = System.currentTimeMillis()
        val duration = _recordingDurationSeconds.value

        val samplesCopy: List<BenchmarkSample>
        synchronized(sampleBuffer) {
            samplesCopy = sampleBuffer.toList()
        }

        val frameTimesCopy: List<Double>
        synchronized(frameTimesBuffer) {
            frameTimesCopy = frameTimesBuffer.toList()
        }

        if (samplesCopy.isEmpty() && frameTimesCopy.isEmpty()) {
            return@withContext null
        }

        val report = analyticsCalculator.calculateReport(
            packageName = _activePackageName.value,
            appName = _activeAppName.value,
            startTimeEpochMs = sessionStartTimeMs,
            endTimeEpochMs = endTimeMs,
            durationSeconds = duration,
            frameTimesMs = frameTimesCopy,
            samples = samplesCopy
        )

        _activePackageName.value = ""
        _activeAppName.value = ""
        _currentLiveSample.value = null
        _recordingDurationSeconds.value = 0

        report
    }

    fun cancelRecording() {
        fpsSampler.stop()
        _isRecording.value = false
        recordingJob?.cancel()
        recordingJob = null
        _activePackageName.value = ""
        _activeAppName.value = ""
        _currentLiveSample.value = null
        _recordingDurationSeconds.value = 0
        synchronized(sampleBuffer) { sampleBuffer.clear() }
        synchronized(frameTimesBuffer) { frameTimesBuffer.clear() }
    }

    private suspend fun sampleCurrentState(relativeSec: Int): BenchmarkSample {
        val now = System.currentTimeMillis()

        val fpsSnapshot = try {
            fpsSampler.frameMetrics.value ?: fpsSampler.sampleOnce()
        } catch (_: Exception) { null }

        val fpsValue = fpsSnapshot?.fps ?: 0.0
        val frameTime = fpsSnapshot?.frameTimeMs ?: (if (fpsValue > 0) 1000.0 / fpsValue else 0.0)

        // Buffer frame times based on total frames presented during this second
        val framesThisSecond = fpsSnapshot?.totalFrames ?: if (fpsValue > 0) fpsValue.toInt() else 0
        if (framesThisSecond > 0 && frameTime > 0.0) {
            synchronized(frameTimesBuffer) {
                for (i in 0 until framesThisSecond) {
                    frameTimesBuffer.add(frameTime)
                }
            }
        }

        val cpuLoad = readCpuLoadPercent()

        val cpuFreq = try {
            val policies = cpuControlEngine.getClusterPolicies().getOrNull()
            val maxFreqKHz = policies?.maxOfOrNull { it.curFreqKHz } ?: 0L
            (maxFreqKHz / 1000).toInt()
        } catch (_: Exception) { 0 }

        val gpuPolicy = try {
            gpuControlEngine.getGpuPolicy().getOrNull()
        } catch (_: Exception) { null }
        val gpuFreq = ((gpuPolicy?.curFreqKHz ?: 0L) / 1000).toInt()

        val batteryState = try {
            batteryRepository.getBatteryState()
        } catch (_: Exception) { null }

        val thermalSnapshot = try {
            thermalRepository.sampleSnapshot()
        } catch (_: Exception) { null }

        val peakTemp = thermalSnapshot?.hottestZone?.tempC ?: (batteryState?.temperatureC ?: 0f)

        val ramUsedMb = try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            actManager?.getMemoryInfo(memInfo)
            ((memInfo.totalMem - memInfo.availMem) / (1024 * 1024)).toInt()
        } catch (_: Exception) { 0 }

        return BenchmarkSample(
            timestampEpochMs = now,
            relativeTimeSec = relativeSec,
            fps = fpsValue,
            frameTimeMs = frameTime,
            cpuLoadPercent = cpuLoad,
            cpuFreqMhz = cpuFreq,
            gpuLoadPercent = 0,
            gpuFreqMhz = gpuFreq,
            batteryPowerWatts = batteryState?.powerWatts ?: 0f,
            temperatureC = peakTemp,
            ramUsedMb = ramUsedMb
        )
    }

    private fun readCpuLoadPercent(): Int {
        return try {
            val file = File("/proc/stat")
            if (!file.exists()) return 0
            val line = file.bufferedReader().use { it.readLine() } ?: return 0
            if (!line.startsWith("cpu ")) return 0
            val parts = line.removePrefix("cpu ").trim().split("\\s+".toRegex()).mapNotNull { it.toLongOrNull() }
            if (parts.size < 4) return 0
            val user = parts[0]
            val nice = parts[1]
            val system = parts[2]
            val idle = parts[3]
            val iowait = if (parts.size > 4) parts[4] else 0L
            val currentTotal = user + nice + system + idle + iowait + parts.drop(5).sum()
            val currentIdle = idle + iowait
            var load = 0
            if (prevTotalJiffies > 0L) {
                val deltaTotal = currentTotal - prevTotalJiffies
                val deltaIdle = currentIdle - prevIdleJiffies
                if (deltaTotal > 0L) {
                    val active = (deltaTotal - deltaIdle).coerceAtLeast(0L)
                    load = ((active * 100) / deltaTotal).toInt().coerceIn(0, 100)
                }
            }
            prevTotalJiffies = currentTotal
            prevIdleJiffies = currentIdle
            load
        } catch (_: Exception) {
            0
        }
    }
}
