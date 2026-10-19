package com.mahin.hytune.ui.benchmark

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mahin.hytune.domain.benchmark.BenchmarkRepository
import com.mahin.hytune.domain.benchmark.model.BenchmarkSample
import com.mahin.hytune.domain.benchmark.model.BenchmarkSessionReport
import com.mahin.hytune.domain.detection.ForegroundAppDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BenchmarkUiState(
    val isRecording: Boolean = false,
    val durationSeconds: Int = 0,
    val liveSample: BenchmarkSample? = null,
    val activePackageName: String = "",
    val detectedForegroundPackage: String = "",
    val sessions: List<BenchmarkSessionReport> = emptyList(),
    val exportedCsvContent: String? = null
)

@HiltViewModel
class BenchmarkViewModel @Inject constructor(
    private val benchmarkRepository: BenchmarkRepository,
    private val foregroundAppDetector: ForegroundAppDetector
) : ViewModel() {

    private val _exportedCsv = MutableStateFlow<String?>(null)

    private data class RecorderState(
        val isRecording: Boolean,
        val durationSeconds: Int,
        val liveSample: BenchmarkSample?,
        val activePackageName: String
    )

    private val recorderState = combine(
        benchmarkRepository.isRecording,
        benchmarkRepository.recordingDurationSeconds,
        benchmarkRepository.currentLiveSample,
        benchmarkRepository.activePackageName
    ) { isRecording, duration, liveSample, activePkg ->
        RecorderState(isRecording, duration, liveSample, activePkg)
    }

    val uiState: StateFlow<BenchmarkUiState> = combine(
        recorderState,
        foregroundAppDetector.currentForegroundApp,
        benchmarkRepository.getAllSessions(),
        _exportedCsv
    ) { rec, foreground, sessions, exportedCsv ->
        BenchmarkUiState(
            isRecording = rec.isRecording,
            durationSeconds = rec.durationSeconds,
            liveSample = rec.liveSample,
            activePackageName = rec.activePackageName,
            detectedForegroundPackage = foreground?.packageName.orEmpty(),
            sessions = sessions,
            exportedCsvContent = exportedCsv
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = BenchmarkUiState()
    )

    fun startRecording(packageName: String? = null, appName: String? = null) {
        val targetPkg = packageName?.takeIf { it.isNotBlank() }
            ?: uiState.value.detectedForegroundPackage.takeIf { it.isNotBlank() }
            ?: "all.apps"
        val targetName = appName?.takeIf { it.isNotBlank() } ?: targetPkg
        benchmarkRepository.startRecording(targetPkg, targetName)
    }

    fun stopRecording() {
        viewModelScope.launch {
            benchmarkRepository.stopAndSaveRecording()
        }
    }

    fun cancelRecording() {
        benchmarkRepository.cancelRecording()
    }

    fun deleteSession(id: Long) {
        viewModelScope.launch {
            benchmarkRepository.deleteSession(id)
        }
    }

    fun clearAllSessions() {
        viewModelScope.launch {
            benchmarkRepository.clearAllSessions()
        }
    }

    fun exportSessionCsv(id: Long) {
        viewModelScope.launch {
            val csv = benchmarkRepository.exportSessionCsv(id)
            _exportedCsv.value = csv
        }
    }

    fun clearExportedCsv() {
        _exportedCsv.value = null
    }
}
