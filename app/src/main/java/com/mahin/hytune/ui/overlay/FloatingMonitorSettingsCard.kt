package com.mahin.hytune.ui.overlay

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mahin.hytune.domain.overlay.model.OverlayConfig

@Composable
fun FloatingMonitorSettingsCard(
    config: OverlayConfig,
    isShowing: Boolean,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onToggleShowing: (Boolean) -> Unit,
    onToggleCompact: (Boolean) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onMetricToggle: (cpu: Boolean, gpu: Boolean, batt: Boolean, temp: Boolean, ram: Boolean, fps: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header & Master Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Floating Monitor Overlay (HUD)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Real-time non-intrusive HUD over games and apps.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (hasPermission) {
                    Switch(
                        checked = isShowing || config.enabled,
                        onCheckedChange = { onToggleShowing(it) }
                    )
                } else {
                    Button(onClick = onRequestPermission) {
                        Text(text = "Grant Permission", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (!hasPermission) {
                Text(
                    text = "Android SYSTEM_ALERT_WINDOW permission is required to display the floating HUD over other apps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                // Mode Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "HUD Layout:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    FilterChip(
                        selected = config.isCompact,
                        onClick = { onToggleCompact(true) },
                        label = { Text("Compact Pill", style = MaterialTheme.typography.labelSmall) }
                    )
                    FilterChip(
                        selected = !config.isCompact,
                        onClick = { onToggleCompact(false) },
                        label = { Text("Expanded Card", style = MaterialTheme.typography.labelSmall) }
                    )
                }

                // Opacity Slider
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val percent = (config.opacity * 100).toInt()
                    Text(
                        text = "Overlay Opacity: $percent%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = config.opacity,
                        onValueChange = { onOpacityChange(it) },
                        valueRange = 0.3f..1.0f,
                        steps = 7
                    )
                }

                // Metrics Selector
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Displayed Telemetry Metrics",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = config.showFps,
                            onClick = { onMetricToggle(config.showCpu, config.showGpu, config.showBattery, config.showTemp, config.showRam, !config.showFps) },
                            label = { Text("FPS", style = MaterialTheme.typography.labelSmall) }
                        )
                        FilterChip(
                            selected = config.showCpu,
                            onClick = { onMetricToggle(!config.showCpu, config.showGpu, config.showBattery, config.showTemp, config.showRam, config.showFps) },
                            label = { Text("CPU", style = MaterialTheme.typography.labelSmall) }
                        )
                        FilterChip(
                            selected = config.showGpu,
                            onClick = { onMetricToggle(config.showCpu, !config.showGpu, config.showBattery, config.showTemp, config.showRam, config.showFps) },
                            label = { Text("GPU", style = MaterialTheme.typography.labelSmall) }
                        )
                        FilterChip(
                            selected = config.showBattery,
                            onClick = { onMetricToggle(config.showCpu, config.showGpu, !config.showBattery, config.showTemp, config.showRam, config.showFps) },
                            label = { Text("Power", style = MaterialTheme.typography.labelSmall) }
                        )
                        FilterChip(
                            selected = config.showTemp,
                            onClick = { onMetricToggle(config.showCpu, config.showGpu, config.showBattery, !config.showTemp, config.showRam, config.showFps) },
                            label = { Text("Thermals", style = MaterialTheme.typography.labelSmall) }
                        )
                        FilterChip(
                            selected = config.showRam,
                            onClick = { onMetricToggle(config.showCpu, config.showGpu, config.showBattery, config.showTemp, !config.showRam, config.showFps) },
                            label = { Text("RAM", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }
    }
}
