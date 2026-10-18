package com.mahin.hytune.domain.thermal

import com.mahin.hytune.data.local.db.dao.ThermalThrottleDao
import com.mahin.hytune.data.local.db.entity.toEntity
import com.mahin.hytune.domain.thermal.model.ThermalSnapshot
import com.mahin.hytune.domain.thermal.model.ThermalThrottleEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThermalRepository @Inject constructor(
    private val thermalEngine: ThermalEngine,
    private val thermalThrottleDao: ThermalThrottleDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun sampleSnapshot(): ThermalSnapshot = withContext(ioDispatcher) {
        thermalEngine.sampleThermals()
    }

    fun observeThermalSnapshot(intervalMs: Long = 1000L): Flow<ThermalSnapshot> = flow {
        while (true) {
            emit(thermalEngine.sampleThermals())
            delay(intervalMs)
        }
    }.flowOn(ioDispatcher)

    fun observeRecentThrottleEvents(limit: Int = 20): Flow<List<ThermalThrottleEvent>> {
        return thermalThrottleDao.getRecentEvents(limit).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun logThrottleEvent(event: ThermalThrottleEvent): Long = withContext(ioDispatcher) {
        thermalThrottleDao.insertEvent(event.toEntity())
    }

    suspend fun clearThrottleHistory() = withContext(ioDispatcher) {
        thermalThrottleDao.clearAllEvents()
    }
}
