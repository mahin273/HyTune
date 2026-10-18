package com.mahin.hytune.domain.battery

import com.mahin.hytune.data.local.db.dao.ChargingSessionDao
import com.mahin.hytune.domain.battery.model.BatteryState
import com.mahin.hytune.domain.battery.model.ChargingSession
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BatteryRepository @Inject constructor(
    private val batteryEngine: BatteryEngine,
    private val chargingSessionDao: ChargingSessionDao,
    private val sessionTracker: ChargingSessionTracker,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    fun getBatteryStateFlow(pollIntervalMs: Long = 1000L): Flow<BatteryState> = flow {
        while (currentCoroutineContext().isActive) {
            val state = batteryEngine.sample()
            sessionTracker.onSample(state.powerWatts, state.temperatureC)
            emit(state)
            delay(pollIntervalMs)
        }
    }.flowOn(ioDispatcher)

    suspend fun getBatteryState(): BatteryState = withContext(ioDispatcher) {
        batteryEngine.sample()
    }

    fun getAllChargingSessions(): Flow<List<ChargingSession>> {
        return chargingSessionDao.getAllSessions()
            .map { entities -> entities.map { ChargingSession.fromEntity(it) } }
            .flowOn(ioDispatcher)
    }

    fun getRecentChargingSessions(limit: Int = 10): Flow<List<ChargingSession>> {
        return chargingSessionDao.getRecentSessions(limit)
            .map { entities -> entities.map { ChargingSession.fromEntity(it) } }
            .flowOn(ioDispatcher)
    }

    suspend fun clearSessionHistory() = withContext(ioDispatcher) {
        chargingSessionDao.clearAllSessions()
    }
}
