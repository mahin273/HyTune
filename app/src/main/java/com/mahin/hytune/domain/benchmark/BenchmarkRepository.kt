package com.mahin.hytune.domain.benchmark

import com.mahin.hytune.data.local.db.dao.BenchmarkSessionDao
import com.mahin.hytune.data.local.db.entity.BenchmarkSessionEntity
import com.mahin.hytune.domain.benchmark.analytics.CsvExportFormatter
import com.mahin.hytune.domain.benchmark.model.BenchmarkSessionReport
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BenchmarkRepository @Inject constructor(
    private val sessionDao: BenchmarkSessionDao,
    private val recorderEngine: BenchmarkRecorderEngine,
    private val csvFormatter: CsvExportFormatter,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    val isRecording = recorderEngine.isRecording
    val recordingDurationSeconds = recorderEngine.recordingDurationSeconds
    val currentLiveSample = recorderEngine.currentLiveSample
    val activePackageName = recorderEngine.activePackageName

    fun getAllSessions(): Flow<List<BenchmarkSessionReport>> {
        return sessionDao.getAllSessions()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(ioDispatcher)
    }

    fun getSessionsForPackage(packageName: String): Flow<List<BenchmarkSessionReport>> {
        return sessionDao.getSessionsForPackage(packageName)
            .map { list -> list.map { it.toDomain() } }
            .flowOn(ioDispatcher)
    }

    suspend fun getSessionById(id: Long): BenchmarkSessionReport? = withContext(ioDispatcher) {
        sessionDao.getSessionById(id)?.toDomain()
    }

    suspend fun saveSession(report: BenchmarkSessionReport): Long = withContext(ioDispatcher) {
        val entity = BenchmarkSessionEntity.fromDomain(report)
        sessionDao.insertSession(entity)
    }

    suspend fun deleteSession(id: Long) = withContext(ioDispatcher) {
        sessionDao.deleteSessionById(id)
    }

    suspend fun clearAllSessions() = withContext(ioDispatcher) {
        sessionDao.clearAllSessions()
    }

    fun startRecording(packageName: String, appName: String) {
        recorderEngine.startRecording(packageName, appName)
    }

    suspend fun stopAndSaveRecording(): BenchmarkSessionReport? = withContext(ioDispatcher) {
        val report = recorderEngine.stopRecording() ?: return@withContext null
        val id = saveSession(report)
        report.copy(id = id)
    }

    fun cancelRecording() {
        recorderEngine.cancelRecording()
    }

    suspend fun exportSessionCsv(id: Long): String? = withContext(ioDispatcher) {
        val session = getSessionById(id) ?: return@withContext null
        csvFormatter.formatReportToCsv(session)
    }

    fun exportReportCsv(report: BenchmarkSessionReport): String {
        return csvFormatter.formatReportToCsv(report)
    }
}
