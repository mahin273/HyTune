package com.mahin.hytune.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mahin.hytune.ui.battery.BatteryCard
import com.mahin.hytune.ui.battery.BatteryViewModel
import com.mahin.hytune.ui.battery.ChargeControlCard
import com.mahin.hytune.ui.battery.ChargingSessionsCard
import com.mahin.hytune.ui.thermal.ThermalZonesCard

@Composable
fun MonitorScreen(
    batteryViewModel: BatteryViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val batteryUiState by batteryViewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Battery Real-time Telemetry
        BatteryCard(batteryState = batteryUiState.batteryState)

        // Charge Control & Bypass Charging
        ChargeControlCard(
            status = batteryUiState.chargeStatus,
            config = batteryUiState.chargeConfig,
            onBypassToggle = { batteryViewModel.setBypassCharging(it) },
            onCurrentLimitChange = { batteryViewModel.setChargeCurrentLimit(it) },
            onLimitConfigChange = { enabled, percent -> batteryViewModel.setChargeLimitConfig(enabled, percent) }
        )

        // Thermal Zones & Throttling
        ThermalZonesCard(
            snapshot = batteryUiState.thermalSnapshot,
            throttleEvents = batteryUiState.throttleEvents,
            onClearEvents = { batteryViewModel.clearThrottleHistory() }
        )

        // Historical Charging Sessions
        ChargingSessionsCard(
            sessions = batteryUiState.sessions,
            onClearHistory = { batteryViewModel.clearSessionHistory() }
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Real-time Telemetry & HUD",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Continuous sampling of CPU load, GPU clock, memory pressure, thermal zones, and battery discharge wattage.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
