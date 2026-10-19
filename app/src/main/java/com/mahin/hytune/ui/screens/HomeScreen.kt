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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mahin.hytune.ui.common.loadingSemantics
import com.mahin.hytune.ui.components.MetricCard
import com.mahin.hytune.ui.components.ProfileModeSelector
import com.mahin.hytune.ui.components.SocInfoCard
import com.mahin.hytune.ui.dashboard.DashboardViewModel
import com.mahin.hytune.ui.theme.AmberTertiaryDark
import com.mahin.hytune.ui.theme.CyanPrimaryDark
import com.mahin.hytune.ui.theme.MintSecondaryDark
import kotlin.math.abs

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel()
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
        if (uiState.hardwareSummary != null) {
            SocInfoCard(summary = uiState.hardwareSummary!!)
        }

        if (uiState.profiles.isNotEmpty()) {
            ProfileModeSelector(
                profiles = uiState.profiles,
                activeProfileId = uiState.activeProfileId,
                onProfileSelected = { viewModel.onProfileSelected(it) }
            )
        }

        val snapshot = uiState.latestSnapshot
        if (snapshot != null) {
            // CPU Metric Card
            MetricCard(
                title = "CPU Total Load",
                primaryValue = "%.1f %%".format(snapshot.cpu.overallUsagePercent),
                secondaryValue = "${snapshot.cpu.coreFrequenciesKHz.size} active cores monitored",
                dataPoints = uiState.cpuLoadHistory,
                lineColor = CyanPrimaryDark,
                baselineMin = 0f,
                baselineMax = 100f
            )

            // RAM Metric Card
            val totalGb = snapshot.memory.totalBytes / (1024f * 1024f * 1024f)
            val availableGb = snapshot.memory.availableBytes / (1024f * 1024f * 1024f)
            val usedGb = totalGb - availableGb
            MetricCard(
                title = "RAM & Memory Pressure",
                primaryValue = "%.1f %%".format(snapshot.memory.usagePercent),
                secondaryValue = "Used: %.1f GB / %.1f GB (ZRAM: %.1f %%)".format(
                    usedGb,
                    totalGb,
                    snapshot.memory.swapUsagePercent
                ),
                dataPoints = uiState.ramUsageHistory,
                lineColor = MintSecondaryDark,
                baselineMin = 0f,
                baselineMax = 100f
            )

            // Thermal Metric Card
            val maxTempC = snapshot.thermals.maxTempC
            val displayTemp = if (uiState.temperatureInCelsius) {
                "%.1f C".format(maxTempC)
            } else {
                "%.1f F".format(maxTempC * 1.8f + 32f)
            }
            val thermalColor = if (maxTempC >= 70f) Color(0xFFE57373) else CyanPrimaryDark
            MetricCard(
                title = "Thermal Sensors",
                primaryValue = displayTemp,
                secondaryValue = "${snapshot.thermals.zones.size} active thermal sensor zones monitored",
                dataPoints = uiState.tempHistory,
                lineColor = thermalColor,
                baselineMin = 25f,
                baselineMax = 95f
            )

            // Battery & Power Card
            if (snapshot.battery != null) {
                val powerSign = if (snapshot.battery.currentAmperes < 0) "+" else "-"
                MetricCard(
                    title = "Battery & Power Flow",
                    primaryValue = "%s%.2f W".format(powerSign, abs(snapshot.battery.powerWatts)),
                    secondaryValue = "%d %% | %.2f V | %.1f C".format(
                        snapshot.battery.percent,
                        snapshot.battery.voltageV,
                        snapshot.battery.temperatureC
                    ),
                    dataPoints = uiState.batteryWattageHistory,
                    lineColor = AmberTertiaryDark
                )
            }
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
