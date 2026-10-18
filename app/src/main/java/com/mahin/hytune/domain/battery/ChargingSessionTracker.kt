package com.mahin.hytune.domain.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.mahin.hytune.data.local.db.dao.ChargingSessionDao
import com.mahin.hytune.data.local.db.entity.ChargingSessionEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class ChargingSessionTracker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chargingSessionDao: ChargingSessionDao,
    private val batteryEngine: BatteryEngine,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    var testScopeOverride: CoroutineScope? = null
    private val defaultScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val scope: CoroutineScope get() = testScopeOverride ?: defaultScope

    private var activeSessionId: Long? = null
    private var accumulatedWatts: Double = 0.0
    private var sampleCount: Int = 0
    private var peakTempC: Float = 0f
    private var startPercent: Int = 0
    private var isReceiverRegistered: Boolean = false

    private val _isCharging = MutableStateFlow(false)
    val isCharging: StateFlow<Boolean> = _isCharging.asStateFlow()

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_POWER_CONNECTED -> handlePowerConnected()
                Intent.ACTION_POWER_DISCONNECTED -> handlePowerDisconnected()
            }
        }
    }

    fun startMonitoring() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
            context.registerReceiver(powerReceiver, filter)
            isReceiverRegistered = true

            // Check if already charging when starting
            scope.launch {
                val state = batteryEngine.sample()
                if (state.isCharging) {
                    handlePowerConnected()
                }
            }
        }
    }

    fun stopMonitoring() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(powerReceiver)
            } catch (_: IllegalArgumentException) {
                // Ignore if not registered
            }
            isReceiverRegistered = false
        }
        handlePowerDisconnected()
    }

    fun handlePowerConnected() {
        scope.launch {
            if (activeSessionId != null) return@launch // Already in session

            val state = batteryEngine.sample()
            startPercent = state.levelPercent
            peakTempC = state.temperatureC
            accumulatedWatts = state.powerWatts.toDouble()
            sampleCount = 1

            val entity = ChargingSessionEntity(
                startTimestampEpochMs = System.currentTimeMillis(),
                startPercent = startPercent,
                peakTemperatureC = peakTempC,
                avgPowerWatts = state.powerWatts
            )

            activeSessionId = chargingSessionDao.insertSession(entity)
            _isCharging.value = true
        }
    }

    fun onSample(powerWatts: Float, temperatureC: Float) {
        if (activeSessionId != null) {
            accumulatedWatts += powerWatts
            sampleCount++
            peakTempC = max(peakTempC, temperatureC)
        }
    }

    fun handlePowerDisconnected() {
        scope.launch {
            val sessionId = activeSessionId ?: return@launch
            val state = batteryEngine.sample()

            val avgWatts = if (sampleCount > 0) {
                (accumulatedWatts / sampleCount).toFloat()
            } else {
                state.powerWatts
            }

            val existing = chargingSessionDao.getSessionById(sessionId)
            if (existing != null) {
                val updated = existing.copy(
                    endTimestampEpochMs = System.currentTimeMillis(),
                    endPercent = state.levelPercent,
                    avgPowerWatts = avgWatts,
                    peakTemperatureC = max(peakTempC, state.temperatureC)
                )
                chargingSessionDao.updateSession(updated)
            }

            activeSessionId = null
            accumulatedWatts = 0.0
            sampleCount = 0
            _isCharging.value = false
        }
    }

    suspend fun getActiveSessionId(): Long? = withContext(ioDispatcher) {
        activeSessionId
    }
}
