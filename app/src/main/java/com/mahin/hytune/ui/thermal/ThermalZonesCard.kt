package com.mahin.hytune.ui.thermal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mahin.hytune.domain.thermal.model.ThermalSnapshot
import com.mahin.hytune.domain.thermal.model.ThermalThrottleEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ThermalZonesCard(
    snapshot: ThermalSnapshot,
    throttleEvents: List<ThermalThrottleEvent>,
    onClearEvents: () -> Unit,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Thermal Zones & Throttling",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val zoneCount = snapshot.zones.size
                    Text(
                        text = "$zoneCount thermal zones monitored",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val isThrottling = snapshot.isThrottling
                val badgeContainer = if (isThrottling) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                }
                val badgeColor = if (isThrottling) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                }
                val badgeText = if (isThrottling) "Throttling Active" else "Normal Thermals"

                Box(
                    modifier = Modifier
                        .background(badgeContainer, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Key Sensors Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ThermalMetricTile(
                    label = "CPU",
                    tempC = snapshot.cpuTempC,
                    modifier = Modifier.weight(1f)
                )
                ThermalMetricTile(
                    label = "GPU",
                    tempC = snapshot.gpuTempC,
                    modifier = Modifier.weight(1f)
                )
                ThermalMetricTile(
                    label = "Battery",
                    tempC = snapshot.batteryTempC,
                    modifier = Modifier.weight(1f)
                )
                ThermalMetricTile(
                    label = "SoC",
                    tempC = snapshot.socTempC,
                    modifier = Modifier.weight(1f)
                )
            }

            // Active Cooling Devices
            val activeCooling = snapshot.coolingDevices.filter { it.isThrottling }
            if (activeCooling.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Active Cooling Throttles",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                    activeCooling.forEach { cdev ->
                        Text(
                            text = "${cdev.name}: State ${cdev.curState} / ${cdev.maxState}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Historical Throttle Events
            if (throttleEvents.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Throttle Events Log",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(onClick = onClearEvents) {
                        Text(text = "Clear History", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    throttleEvents.take(5).forEach { event ->
                        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(event.timestampEpochMs))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "${event.zoneName} (${String.format(Locale.US, "%.1f C", event.temperatureC)})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${event.triggerReason} - ${event.coolingDeviceState}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = timeStr,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThermalMetricTile(
    label: String,
    tempC: Float?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val tempText = if (tempC != null) String.format(Locale.US, "%.1f C", tempC) else "N/A"
        Text(
            text = tempText,
            style = MaterialTheme.typography.bodyLarge,
            color = when {
                tempC == null -> MaterialTheme.colorScheme.onSurface
                tempC >= 75f -> MaterialTheme.colorScheme.error
                tempC >= 50f -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onSurface
            },
            fontWeight = FontWeight.Bold
        )
    }
}
