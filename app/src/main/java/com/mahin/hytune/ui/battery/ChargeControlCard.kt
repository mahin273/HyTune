package com.mahin.hytune.ui.battery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Power
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mahin.hytune.domain.battery.model.ChargeControlConfig
import com.mahin.hytune.domain.battery.model.ChargeControlStatus

@Composable
fun ChargeControlCard(
    status: ChargeControlStatus,
    config: ChargeControlConfig,
    onBypassToggle: (Boolean) -> Unit,
    onCurrentLimitChange: (Int) -> Unit,
    onLimitConfigChange: (enabled: Boolean, percent: Int) -> Unit,
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Power,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Charge Control",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (status.isSupported) "Hardware PMIC control supported" else "Root required for charge control",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (status.isSupported) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Bypass Charging Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Bypass Charging",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Power phone directly from USB without charging battery. Eliminates charging heat during heavy load.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = status.isBypassActive || config.bypassChargingEnabled,
                    onCheckedChange = { onBypassToggle(it) },
                    enabled = status.isSupported
                )
            }

            // Charge Current Limit
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Charge Current Limit",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Cap charging speed to reduce Joule heating (P = I^2 * R) during charging.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val limitOptions = listOf(0, 1000, 1500, 2000, 2500)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    limitOptions.forEach { limit ->
                        val isSelected = config.chargeCurrentLimitMa == limit
                        val label = if (limit == 0) "Unlimited" else "$limit mA"
                        FilterChip(
                            selected = isSelected,
                            onClick = { onCurrentLimitChange(limit) },
                            label = { Text(text = label, style = MaterialTheme.typography.labelSmall) },
                            enabled = status.isSupported
                        )
                    }
                }
            }

            // Smart Battery Protection (Cutoff at X%)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Battery Health Cutoff",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Automatically stops charging at ${config.chargeLimitPercent}% (resumes at ${config.chargeLimitPercent - 3}%).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = config.chargeLimitEnabled,
                        onCheckedChange = { onLimitConfigChange(it, config.chargeLimitPercent) },
                        enabled = status.isSupported
                    )
                }

                if (config.chargeLimitEnabled) {
                    Slider(
                        value = config.chargeLimitPercent.toFloat(),
                        onValueChange = { onLimitConfigChange(true, it.toInt()) },
                        valueRange = 50f..95f,
                        steps = 8,
                        enabled = status.isSupported
                    )
                }
            }
        }
    }
}
