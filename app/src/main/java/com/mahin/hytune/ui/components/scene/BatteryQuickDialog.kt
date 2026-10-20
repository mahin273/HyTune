package com.mahin.hytune.ui.components.scene

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mahin.hytune.domain.sampler.model.BatterySample
import com.mahin.hytune.domain.sampler.model.ThermalSample
import com.mahin.hytune.ui.theme.LimePrimaryDark
import com.mahin.hytune.ui.theme.OnSurfaceDark
import com.mahin.hytune.ui.theme.OnSurfaceVariantDark
import com.mahin.hytune.ui.theme.OutlineDark
import com.mahin.hytune.ui.theme.SurfaceDark
import com.mahin.hytune.ui.theme.SurfaceVariantDark
import kotlin.math.abs

@Composable
fun BatteryQuickDialog(
    batterySample: BatterySample?,
    thermals: ThermalSample?,
    onDismissRequest: () -> Unit,
    onOpenBatteryMonitor: () -> Unit,
    modifier: Modifier = Modifier
) {
    val percent = batterySample?.percent ?: 80
    val voltageV = batterySample?.voltageV ?: 4.0f
    val powerWatts = batterySample?.powerWatts ?: 0f
    val currentAmperes = batterySample?.currentAmperes ?: 0f
    val isCharging = powerWatts > 0f || currentAmperes < 0f
    val tempC = thermals?.batteryTempC ?: (batterySample?.temperatureC ?: 34.0f)

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.BatteryChargingFull,
                    contentDescription = null,
                    tint = LimePrimaryDark,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Battery & Power",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = OnSurfaceDark
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceVariantDark,
                    border = BorderStroke(1.dp, OutlineDark)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Charge Level",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariantDark
                            )
                            Text(
                                text = "$percent%",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = LimePrimaryDark
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (isCharging) "Charging State" else "Discharging State",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariantDark
                            )
                            Text(
                                text = if (isCharging) "+${"%.2f".format(abs(powerWatts))} W" else "-${"%.2f".format(abs(powerWatts))} W",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isCharging) LimePrimaryDark else OnSurfaceDark
                            )
                        }
                    }
                }

                BatteryDetailRow(
                    icon = Icons.Default.ElectricBolt,
                    label = "Voltage",
                    value = "${"%.2f".format(voltageV)} V",
                    tint = Color(0xFFFFB74D)
                )
                BatteryDetailRow(
                    icon = Icons.Default.Speed,
                    label = "Current Flow",
                    value = "${"%.0f".format(abs(currentAmperes) * 1000f)} mA",
                    tint = Color(0xFF64B5F6)
                )
                BatteryDetailRow(
                    icon = Icons.Default.LocalFireDepartment,
                    label = "Battery Temp",
                    value = "${"%.1f".format(tempC)} C",
                    tint = Color(0xFFEF5350)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onOpenBatteryMonitor,
                colors = ButtonDefaults.buttonColors(containerColor = LimePrimaryDark)
            ) {
                Text(
                    text = "Open Battery & Charge Control",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismissRequest
            ) {
                Text(
                    text = "Close",
                    color = OnSurfaceDark
                )
            }
        }
    )
}

@Composable
private fun BatteryDetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    tint: Color
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
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariantDark
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = OnSurfaceDark
        )
    }
}
