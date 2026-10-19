package com.mahin.hytune.domain.fps

import com.mahin.hytune.core.privilege.Capability
import com.mahin.hytune.core.privilege.PrivilegeManager
import com.mahin.hytune.domain.detection.ForegroundAppDetector
import com.mahin.hytune.domain.fps.model.FpsSource
import com.mahin.hytune.domain.fps.model.FrameMetricsSnapshot
import com.mahin.hytune.domain.fps.parser.GfxinfoFramestatsParser
import com.mahin.hytune.domain.fps.parser.SurfaceFlingerLatencyParser
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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FpsSampler @Inject constructor(
    private val privilegeManager: PrivilegeManager,
    private val foregroundAppDetector: ForegroundAppDetector,
    private val surfaceFlingerParser: SurfaceFlingerLatencyParser,
    private val gfxinfoParser: GfxinfoFramestatsParser,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val samplerScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private var samplingJob: Job? = null

    private val _frameMetrics = MutableStateFlow<FrameMetricsSnapshot?>(null)
    val frameMetrics: StateFlow<FrameMetricsSnapshot?> = _frameMetrics.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private var lastSeenTimestampNs: Long = 0L
    private var lastTrackedPackage: String = ""

    fun start(pollingIntervalMs: Long = 1000L) {
        if (_isRunning.value) return
        _isRunning.value = true

        samplingJob?.cancel()
        samplingJob = samplerScope.launch {
            while (isActive) {
                sampleOnce()
                delay(pollingIntervalMs)
            }
        }
    }

    fun stop() {
        _isRunning.value = false
        samplingJob?.cancel()
        samplingJob = null
        lastSeenTimestampNs = 0L
        lastTrackedPackage = ""
    }

    suspend fun sampleOnce(): FrameMetricsSnapshot = withContext(ioDispatcher) {
        val backend = privilegeManager.activeBackend.value
        if (!backend.hasCapability(Capability.DUMPSYS)) {
            val empty = FrameMetricsSnapshot(source = FpsSource.NONE)
            _frameMetrics.value = empty
            return@withContext empty
        }

        val foreground = foregroundAppDetector.currentForegroundApp.value
            ?: foregroundAppDetector.detectOnce()

        val packageName = foreground?.packageName.orEmpty()
        if (packageName.isBlank() || foreground?.isHomeLauncher == true) {
            val idle = FrameMetricsSnapshot(
                fps = 0.0,
                targetFps = 60,
                frameTimeMs = 0.0,
                jankCount = 0,
                totalFrames = 0,
                jankPercentage = 0.0,
                source = FpsSource.NONE,
                packageName = packageName
            )
            _frameMetrics.value = idle
            return@withContext idle
        }

        // Reset timestamp baseline if foreground package switched
        if (packageName != lastTrackedPackage) {
            lastSeenTimestampNs = 0L
            lastTrackedPackage = packageName
        }

        val candidateLayer = findActiveSurfaceFlingerLayer(packageName)
        val snapshot = if (candidateLayer != null) {
            val latencyResult = backend.exec("dumpsys SurfaceFlinger --latency \"$candidateLayer\"")
            if (latencyResult.isSuccess && latencyResult.output.isNotBlank()) {
                val parseResult = surfaceFlingerParser.parse(
                    rawOutput = latencyResult.output,
                    packageName = packageName,
                    lastSeenTimestampNs = lastSeenTimestampNs
                )
                if (parseResult.snapshot.totalFrames > 0 || parseResult.latestTimestampNs > lastSeenTimestampNs) {
                    lastSeenTimestampNs = parseResult.latestTimestampNs
                    parseResult.snapshot
                } else {
                    sampleGfxinfoFallback(packageName)
                }
            } else {
                sampleGfxinfoFallback(packageName)
            }
        } else {
            sampleGfxinfoFallback(packageName)
        }

        _frameMetrics.value = snapshot
        snapshot
    }

    private suspend fun sampleGfxinfoFallback(packageName: String): FrameMetricsSnapshot {
        val backend = privilegeManager.activeBackend.value
        val result = backend.exec("dumpsys gfxinfo $packageName framestats")
        return if (result.isSuccess && result.output.isNotBlank()) {
            gfxinfoParser.parse(result.output, packageName)
        } else {
            FrameMetricsSnapshot(
                fps = 0.0,
                targetFps = 60,
                frameTimeMs = 0.0,
                jankCount = 0,
                totalFrames = 0,
                jankPercentage = 0.0,
                source = FpsSource.NONE,
                packageName = packageName
            )
        }
    }

    suspend fun findActiveSurfaceFlingerLayer(packageName: String): String? {
        val backend = privilegeManager.activeBackend.value
        val listResult = backend.exec("dumpsys SurfaceFlinger --list")
        if (!listResult.isSuccess || listResult.output.isBlank()) return null

        val lines = listResult.output.lines().map { it.trim() }
        val matchingLayers = lines.filter { it.contains(packageName, ignoreCase = true) }

        if (matchingLayers.isEmpty()) return null

        // Priority 1: SurfaceView (Game view / Video view)
        matchingLayers.firstOrNull { it.contains("SurfaceView[", ignoreCase = true) }?.let { return it }
        matchingLayers.firstOrNull { it.contains("SurfaceView -", ignoreCase = true) }?.let { return it }

        // Priority 2: Main Activity window
        matchingLayers.firstOrNull { it.contains("MainActivity", ignoreCase = true) }?.let { return it }

        // Priority 3: Visible numbered layer
        matchingLayers.firstOrNull { it.contains("#") }?.let { return it }

        return matchingLayers.firstOrNull()
    }
}
