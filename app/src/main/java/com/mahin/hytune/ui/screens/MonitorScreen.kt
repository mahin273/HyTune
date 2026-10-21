package com.mahin.hytune.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mahin.hytune.ui.battery.BatteryCard
import com.mahin.hytune.ui.battery.BatteryViewModel
import com.mahin.hytune.ui.battery.ChargeControlCard
import com.mahin.hytune.ui.battery.ChargingSessionsCard
import com.mahin.hytune.ui.benchmark.BenchmarkRecorderCard
import com.mahin.hytune.ui.benchmark.BenchmarkViewModel
import com.mahin.hytune.ui.overlay.FloatingMonitorSettingsCard
import com.mahin.hytune.ui.thermal.ThermalZonesCard

@Composable
fun MonitorScreen(
    batteryViewModel: BatteryViewModel = hiltViewModel(),
    benchmarkViewModel: BenchmarkViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val batteryUiState by batteryViewModel.uiState.collectAsState()
    val benchmarkUiState by benchmarkViewModel.uiState.collectAsState()
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

        // Gaming & App Benchmark Recorder
        BenchmarkRecorderCard(
            uiState = benchmarkUiState,
            onStartRecording = { pkg, name -> benchmarkViewModel.startRecording(pkg, name) },
            onStopRecording = { benchmarkViewModel.stopRecording() },
            onCancelRecording = { benchmarkViewModel.cancelRecording() },
            onDeleteSession = { benchmarkViewModel.deleteSession(it) },
            onClearAllSessions = { benchmarkViewModel.clearAllSessions() },
            onExportCsv = { benchmarkViewModel.exportSessionCsv(it) },
            onDismissCsvDialog = { benchmarkViewModel.clearExportedCsv() }
        )

        // Battery Real-time Telemetry & Power Flow Graph (Item 4)
        BatteryCard(
            batteryState = batteryUiState.batteryState,
            powerHistory = batteryUiState.powerHistory
        )

        // Charge Control & Bypass Charging
        ChargeControlCard(
            status = batteryUiState.chargeStatus,
            config = batteryUiState.chargeConfig,
            onBypassToggle = { batteryViewModel.setBypassCharging(it) },
            onCurrentLimitChange = { batteryViewModel.setChargeCurrentLimit(it) },
            onLimitConfigChange = { enabled, percent -> batteryViewModel.setChargeLimitConfig(enabled, percent) }
        )

        // Thermal Zones & Throttling with Trend Graph (Item 7)
        ThermalZonesCard(
            snapshot = batteryUiState.thermalSnapshot,
            throttleEvents = batteryUiState.throttleEvents,
            onClearEvents = { batteryViewModel.clearThrottleHistory() },
            tempHistory = batteryUiState.tempHistory
        )

        // Historical Charging Sessions
        ChargingSessionsCard(
            sessions = batteryUiState.sessions,
            onClearHistory = { batteryViewModel.clearSessionHistory() }
        )

        // Real-time Telemetry Pulse Status Card (Item 7)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Real-time Telemetry & Engine Pulse",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "Continuous sampling of CPU load, GPU clock, memory pressure, thermal zones, and battery discharge wattage.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "FPS & HUD Active",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ElectricBolt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Power Monitored",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
