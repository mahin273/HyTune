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
import com.mahin.hytune.ui.components.ProfileModeSelector
import com.mahin.hytune.ui.components.SocInfoCard
import com.mahin.hytune.ui.components.home.DeviceInfoCard
import com.mahin.hytune.ui.components.home.HardwareStatsStrip
import com.mahin.hytune.ui.components.home.PerformanceMonitorCard
import com.mahin.hytune.ui.components.home.QuickActionsGrid
import com.mahin.hytune.ui.components.home.RootAccessCard
import com.mahin.hytune.ui.dashboard.DashboardViewModel
import com.mahin.hytune.ui.navigation.HyTuneDestination

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
    onNavigateToTab: (HyTuneDestination) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Root Access Card (Screen 1 in mockup)
        RootAccessCard(
            tier = uiState.privilegeTier,
            onRequestRoot = { viewModel.requestRoot() }
        )

        // 2. Device Info Card (Screen 1 in mockup)
        DeviceInfoCard(
            socName = uiState.hardwareSummary?.socName
        )

        // 3. 3-Column Hardware Stats Strip (CPU | RAM | Battery)
        HardwareStatsStrip(
            summary = uiState.hardwareSummary,
            snapshot = uiState.latestSnapshot
        )

        // 4. Quick Actions 2x2 Grid (System Tweaks, App Manager, Performance, ADB Shell)
        QuickActionsGrid(
            onNavigate = onNavigateToTab
        )

        // 5. Performance Monitor Card (Donut Gauge + Multi-line Chart + Quick Cards)
        val snapshot = uiState.latestSnapshot
        PerformanceMonitorCard(
            snapshot = snapshot,
            cpuHistory = uiState.cpuLoadHistory,
            ramHistory = uiState.ramUsageHistory,
            tempHistory = uiState.tempHistory
        )

        // 6. Profiles Selector
        if (uiState.profiles.isNotEmpty()) {
            ProfileModeSelector(
                profiles = uiState.profiles,
                activeProfileId = uiState.activeProfileId,
                onProfileSelected = { viewModel.onProfileSelected(it) }
            )
        }

        // 7. Real-time CPU Cluster Frequency Visualizer
        if (uiState.hardwareSummary != null) {
            SocInfoCard(
                summary = uiState.hardwareSummary!!,
                coreFrequencies = snapshot?.cpu?.coreFrequenciesKHz ?: emptyMap(),
                thermals = snapshot?.thermals
            )
        } else if (uiState.isLoading) {
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
