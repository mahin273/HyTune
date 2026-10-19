package com.mahin.hytune.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mahin.hytune.ui.common.loadingSemantics
import com.mahin.hytune.ui.components.home.RootAccessCard
import com.mahin.hytune.ui.components.scene.SceneBottomStatsCards
import com.mahin.hytune.ui.components.scene.SceneCpuCard
import com.mahin.hytune.ui.components.scene.SceneGpuCard
import com.mahin.hytune.ui.components.scene.SceneMemoryCard
import com.mahin.hytune.ui.dashboard.DashboardViewModel
import com.mahin.hytune.ui.gpu.GpuViewModel
import com.mahin.hytune.ui.memory.MemoryViewModel
import com.mahin.hytune.ui.navigation.HyTuneDestination
import com.mahin.hytune.ui.process.ProcessViewModel

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
    processViewModel: ProcessViewModel = hiltViewModel(),
    memoryViewModel: MemoryViewModel = hiltViewModel(),
    gpuViewModel: GpuViewModel = hiltViewModel(),
    onNavigateToTab: (HyTuneDestination) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val processUiState by processViewModel.uiState.collectAsStateWithLifecycle()
    val gpuUiState by gpuViewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    val snapshot = uiState.latestSnapshot

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Root Access Card (If not granted or warning)
        RootAccessCard(
            tier = uiState.privilegeTier,
            onRequestRoot = { viewModel.requestRoot() }
        )

        // 2. Scene Memory Card (Gauge + RAM & SWAP bars + Trim/Compact buttons)
        SceneMemoryCard(
            memorySample = snapshot?.memory,
            onTrimCaches = { memoryViewModel.trimCaches(com.mahin.hytune.domain.memory.model.DropCacheType.ALL) },
            onCompactZram = { memoryViewModel.compactZram() }
        )

        // 3. Scene GPU Card (Gauge + Frequency + Load + Chipset Specs)
        val liveGpuFreq = gpuUiState.liveFreqKHz ?: 0L
        val maxGpuFreq = (uiState.hardwareSummary?.gpu?.maxFreqKHz ?: 1L).coerceAtLeast(1L)
        val gpuLoad = (liveGpuFreq.toFloat() / maxGpuFreq * 100f).coerceIn(0f, 100f)

        SceneGpuCard(
            gpuInfo = uiState.hardwareSummary?.gpu,
            liveFreqKHz = liveGpuFreq,
            gpuLoadPercent = gpuLoad
        )

        // 4. Scene CPU Card (Top Processes + CPU Load & Temp + 8-Core Visualizer Grid)
        SceneCpuCard(
            cpuSample = snapshot?.cpu,
            hardwareSummary = uiState.hardwareSummary,
            thermals = snapshot?.thermals,
            topProcesses = processUiState.processes
        )

        // 5. Scene Bottom Split Stats (Battery Power Flow & Platform Uptime)
        SceneBottomStatsCards(
            batterySample = snapshot?.battery,
            thermals = snapshot?.thermals,
            onBatteryClicked = { onNavigateToTab(HyTuneDestination.More) },
            onAboutClicked = { onNavigateToTab(HyTuneDestination.More) }
        )

        if (uiState.isLoading && snapshot == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.loadingSemantics(),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
