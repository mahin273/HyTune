package com.mahin.hytune.domain.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.mahin.hytune.data.local.datastore.AppSettingsRepository
import com.mahin.hytune.domain.battery.BatteryRepository
import com.mahin.hytune.domain.cpu.CpuControlEngine
import com.mahin.hytune.domain.fps.FpsSampler
import com.mahin.hytune.domain.gpu.GpuControlEngine
import com.mahin.hytune.domain.memory.MemoryControlEngine
import com.mahin.hytune.domain.overlay.model.OverlayConfig
import com.mahin.hytune.domain.overlay.model.OverlayTelemetry
import com.mahin.hytune.domain.thermal.ThermalRepository
import com.mahin.hytune.ui.overlay.FloatingOverlayView
import com.mahin.hytune.ui.theme.HyTuneTheme
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class FloatingOverlayManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appSettingsRepository: AppSettingsRepository,
    private val cpuControlEngine: CpuControlEngine,
    private val gpuControlEngine: GpuControlEngine,
    private val memoryControlEngine: MemoryControlEngine,
    private val batteryRepository: BatteryRepository,
    private val thermalRepository: ThermalRepository,
    private val fpsSampler: FpsSampler,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val windowManager by lazy {
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    private var composeView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private val managerScope = CoroutineScope(mainDispatcher + SupervisorJob())
    private var telemetryJob: Job? = null

    private val _isShowing = MutableStateFlow(false)
    val isShowing: StateFlow<Boolean> = _isShowing.asStateFlow()

    private val _telemetry = MutableStateFlow(OverlayTelemetry())
    val telemetry: StateFlow<OverlayTelemetry> = _telemetry.asStateFlow()

    private val _config = MutableStateFlow(OverlayConfig())
    val config: StateFlow<OverlayConfig> = _config.asStateFlow()

    init {
        managerScope.launch(ioDispatcher) {
            appSettingsRepository.settings.collect { settings ->
                _config.value = OverlayConfig(
                    enabled = settings.floatingMonitorEnabled,
                    isCompact = settings.overlayCompactMode,
                    opacity = settings.overlayOpacity,
                    showCpu = settings.overlayShowCpu,
                    showGpu = settings.overlayShowGpu,
                    showBattery = settings.overlayShowBattery,
                    showTemp = settings.overlayShowTemp,
                    showRam = settings.overlayShowRam,
                    showFps = settings.overlayShowFps,
                    positionX = settings.overlayPositionX,
                    positionY = settings.overlayPositionY
                )
            }
        }
    }

    fun canDrawOverlays(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    suspend fun showOverlay(): Boolean = withContext(mainDispatcher) {
        if (!canDrawOverlays()) {
            return@withContext false
        }

        if (composeView != null && composeView?.isAttachedToWindow == true) {
            return@withContext true
        }

        val currentConfig = _config.value
        val params = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.START
            x = currentConfig.positionX
            y = currentConfig.positionY
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
        }
        layoutParams = params

        val owner = OverlayLifecycleOwner()
        owner.onCreate()
        owner.onStart()
        lifecycleOwner = owner

        val view = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                HyTuneTheme {
                    val currentTelemetry by telemetry.collectAsState()
                    val activeConfig by config.collectAsState()

                    FloatingOverlayView(
                        telemetry = currentTelemetry,
                        config = activeConfig,
                        onToggleCompact = { managerScope.launch { toggleCompactMode() } },
                        onDragDelta = { dx, dy -> updateOverlayPosition(dx, dy) },
                        onDragEnd = { saveCurrentPosition() },
                        onClose = { hideOverlay() }
                    )
                }
            }
        }

        try {
            windowManager.addView(view, params)
            composeView = view
            _isShowing.value = true
            startTelemetrySampling()
            appSettingsRepository.setFloatingMonitorEnabled(true)
            true
        } catch (_: Exception) {
            owner.onDestroy()
            lifecycleOwner = null
            false
        }
    }

    fun hideOverlay() {
        managerScope.launch(mainDispatcher) {
            stopTelemetrySampling()
            val view = composeView
            if (view != null && view.isAttachedToWindow) {
                try {
                    windowManager.removeView(view)
                } catch (_: Exception) {
                    // Ignore detachment race condition
                }
            }
            lifecycleOwner?.onDestroy()
            lifecycleOwner = null
            composeView = null
            layoutParams = null
            _isShowing.value = false
            appSettingsRepository.setFloatingMonitorEnabled(false)
        }
    }

    suspend fun toggleOverlay(): Boolean {
        return if (_isShowing.value) {
            hideOverlay()
            false
        } else {
            showOverlay()
        }
    }

    fun updateOverlayPosition(dx: Float, dy: Float) {
        val params = layoutParams ?: return
        val view = composeView ?: return
        if (!view.isAttachedToWindow) return

        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        params.x = (params.x + dx.roundToInt()).coerceIn(0, (screenWidth - 80).coerceAtLeast(0))
        params.y = (params.y + dy.roundToInt()).coerceIn(0, (screenHeight - 80).coerceAtLeast(0))

        try {
            windowManager.updateViewLayout(view, params)
        } catch (_: Exception) {
            // View layout update failure
        }
    }

    fun saveCurrentPosition() {
        val params = layoutParams ?: return
        managerScope.launch(ioDispatcher) {
            appSettingsRepository.setOverlayPosition(params.x, params.y)
        }
    }

    suspend fun toggleCompactMode() = withContext(ioDispatcher) {
        val currentCompact = appSettingsRepository.settings.first().overlayCompactMode
        appSettingsRepository.setOverlayCompactMode(!currentCompact)
    }

    private fun startTelemetrySampling() {
        fpsSampler.start(1000L)
        telemetryJob?.cancel()
        telemetryJob = managerScope.launch(ioDispatcher) {
            while (isActive) {
                sampleTelemetry()
                delay(1000L)
            }
        }
    }

    private fun stopTelemetrySampling() {
        fpsSampler.stop()
        telemetryJob?.cancel()
        telemetryJob = null
    }
    private var prevTotalJiffies: Long = 0L
    private var prevIdleJiffies: Long = 0L

    private fun readCpuLoadPercent(): Int {
        return try {
            val file = java.io.File("/proc/stat")
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

    suspend fun sampleTelemetry() = withContext(ioDispatcher) {
        val cpuLoad = readCpuLoadPercent()

        val cpuFreq = try {
            val policies = cpuControlEngine.getClusterPolicies().getOrNull()
            val maxFreqKHz = policies?.maxOfOrNull { it.curFreqKHz } ?: 0L
            (maxFreqKHz / 1000).toInt()
        } catch (_: Exception) { 0 }

        val gpuPolicy = try {
            gpuControlEngine.getGpuPolicy().getOrNull()
        } catch (_: Exception) { null }

        val gpuLoad = 0

        val gpuFreq = try {
            val freqKHz = gpuPolicy?.curFreqKHz ?: 0L
            (freqKHz / 1000).toInt()
        } catch (_: Exception) { 0 }

        val (usedMb, totalMb) = try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            actManager?.getMemoryInfo(memInfo)
            val total = (memInfo.totalMem / (1024 * 1024)).toInt()
            val avail = (memInfo.availMem / (1024 * 1024)).toInt()
            Pair((total - avail).coerceAtLeast(0), total)
        } catch (_: Exception) { Pair(0, 0) }

        val batteryState = try {
            batteryRepository.getBatteryState()
        } catch (_: Exception) { null }

        val thermalSnapshot = try {
            thermalRepository.sampleSnapshot()
        } catch (_: Exception) { null }

        val peakTemp = thermalSnapshot?.hottestZone?.tempC ?: (batteryState?.temperatureC ?: 0f)
        val isThrottling = thermalSnapshot?.isThrottling ?: false

        val fpsSnapshot = try {
            fpsSampler.frameMetrics.value ?: fpsSampler.sampleOnce()
        } catch (_: Exception) { null }
        val currentFps = fpsSnapshot?.fps ?: 0.0

        _telemetry.value = OverlayTelemetry(
            cpuLoadPercent = cpuLoad,
            cpuFreqMhz = cpuFreq,
            gpuLoadPercent = gpuLoad,
            gpuFreqMhz = gpuFreq,
            ramUsedMb = usedMb,
            ramTotalMb = totalMb,
            batteryPowerWatts = batteryState?.powerWatts ?: 0f,
            temperatureC = peakTemp,
            isThrottling = isThrottling,
            fps = currentFps
        )
    }
}
