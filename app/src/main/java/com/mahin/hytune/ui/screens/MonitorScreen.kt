package com.mahin.hytune.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mahin.hytune.ui.battery.BatteryCard
import com.mahin.hytune.ui.battery.BatteryViewModel
import com.mahin.hytune.ui.battery.ChargeControlCard
import com.mahin.hytune.ui.battery.ChargingSessionsCard
import com.mahin.hytune.ui.overlay.FloatingMonitorSettingsCard
import com.mahin.hytune.ui.thermal.ThermalZonesCard

@Composable
fun MonitorScreen(
    batteryViewModel: BatteryViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val batteryUiState by batteryViewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    val requestOverlayPermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Floating Monitor Overlay HUD Controls
        FloatingMonitorSettingsCard(
            config = batteryUiState.overlayConfig,
            isShowing = batteryUiState.isOverlayShowing,
            hasPermission = batteryUiState.canDrawOverlays,
            onRequestPermission = requestOverlayPermission,
            onToggleShowing = { batteryViewModel.toggleOverlay(it) },
            onToggleCompact = { batteryViewModel.setOverlayCompactMode(it) },
            onOpacityChange = { batteryViewModel.setOverlayOpacity(it) },
            onMetricToggle = { cpu, gpu, batt, temp, ram, fps ->
                batteryViewModel.setOverlayMetricVisibility(cpu, gpu, batt, temp, ram, fps)
            }
        )

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
