package com.mahin.hytune.ui.battery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mahin.hytune.domain.battery.model.BatteryState
import com.mahin.hytune.domain.battery.model.BatteryStatus
import com.mahin.hytune.domain.battery.model.PluggedType
import com.mahin.hytune.ui.components.SparklineCanvas
import java.util.Locale

@Composable
fun BatteryCard(
    batteryState: BatteryState,
    powerHistory: List<Float> = emptyList(),
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BatteryChargingFull,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Battery & Power Flow",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                val badgeColor = when {
                    batteryState.status == BatteryStatus.CHARGING -> MaterialTheme.colorScheme.primaryContainer
                    batteryState.temperatureC >= 42f -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.secondaryContainer
                }
                val badgeTextColor = when {
                    batteryState.status == BatteryStatus.CHARGING -> MaterialTheme.colorScheme.onPrimaryContainer
                    batteryState.temperatureC >= 42f -> MaterialTheme.colorScheme.onErrorContainer
                    else -> MaterialTheme.colorScheme.onSecondaryContainer
                }

                val statusLabel = when (batteryState.status) {
                    BatteryStatus.CHARGING -> "Charging (${batteryState.pluggedType.name})"
                    BatteryStatus.DISCHARGING -> "Discharging"
                    BatteryStatus.FULL -> "Full"
                    BatteryStatus.NOT_CHARGING -> "Not Charging"
                    else -> "Battery"
                }

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = badgeColor
                ) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = badgeTextColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Level & Progress Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "${batteryState.levelPercent}%",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = if (batteryState.isCharging) "Power: ${batteryState.powerFormatted}" else "Rate: ${batteryState.powerFormatted}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (batteryState.isCharging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LinearProgressIndicator(
                progress = { batteryState.levelPercent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = if (batteryState.isCharging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            // Power Flow Graph (Item 4)
            val displayPoints = if (powerHistory.size >= 2) {
                powerHistory.map { kotlin.math.abs(it) }
            } else {
                listOf(kotlin.math.abs(batteryState.powerWatts), kotlin.math.abs(batteryState.powerWatts))
            }
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Power Flow Trend",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val latestWatts = powerHistory.lastOrNull() ?: kotlin.math.abs(batteryState.powerWatts)
                        val isCharging = batteryState.isCharging
                        Text(
                            text = String.format(Locale.US, if (isCharging) "+%.2f W" else "-%.2f W", kotlin.math.abs(latestWatts)),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isCharging) Color(0xFF00E5FF) else Color(0xFFFF7043)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(68.dp)
                    ) {
                        SparklineCanvas(
                            dataPoints = displayPoints,
                            lineColor = if (batteryState.isCharging) Color(0xFF00E5FF) else Color(0xFFFF7043),
                            fillColor = (if (batteryState.isCharging) Color(0xFF00E5FF) else Color(0xFFFF7043)).copy(alpha = 0.2f),
                            strokeWidth = 2.dp,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Metrics Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricTile(
                    label = "Voltage",
                    value = batteryState.voltageFormatted,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "Current",
                    value = batteryState.currentFormatted,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricTile(
                    label = "Temperature",
                    value = batteryState.temperatureFormatted,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "Health",
                    value = batteryState.health.name.lowercase().replaceFirstChar { it.uppercase() },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
