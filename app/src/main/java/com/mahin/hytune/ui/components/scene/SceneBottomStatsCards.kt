package com.mahin.hytune.ui.components.scene

import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahin.hytune.domain.sampler.model.BatterySample
import com.mahin.hytune.domain.sampler.model.ThermalSample
import com.mahin.hytune.ui.theme.LimePrimaryDark
import com.mahin.hytune.ui.theme.OnSurfaceDark
import com.mahin.hytune.ui.theme.OnSurfaceVariantDark
import com.mahin.hytune.ui.theme.OutlineDark
import com.mahin.hytune.ui.theme.SurfaceDark
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@Composable
fun SceneBottomStatsCards(
    batterySample: BatterySample?,
    thermals: ThermalSample?,
    onBatteryClicked: () -> Unit = {},
    onAboutClicked: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uptimeMillis = remember { SystemClock.elapsedRealtime() }
    val hours = TimeUnit.MILLISECONDS.toHours(uptimeMillis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(uptimeMillis) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(uptimeMillis) % 60
    val formattedUptime = "%02d:%02d:%02d".format(hours, minutes, seconds)

    val batteryPercent = batterySample?.percent ?: 80
    val voltageV = batterySample?.voltageV ?: 4.0f
    val powerWatts = batterySample?.powerWatts ?: -1.25f
    val isCharging = powerWatts > 0f || (batterySample?.currentAmperes ?: 0f) < 0f
    val batteryTempC = thermals?.batteryTempC ?: (batterySample?.temperatureC ?: 34.0f)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Left Card: Battery & Power
        Card(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onBatteryClicked),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(OutlineDark))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Wattage
                StatRow(
                    icon = Icons.Default.ElectricBolt,
                    iconTint = if (isCharging) LimePrimaryDark else Color(0xFFFFB74D),
                    label = if (isCharging) "+%.2fW".format(abs(powerWatts)) else "-%.2fW".format(abs(powerWatts)),
                    valueColor = if (isCharging) LimePrimaryDark else OnSurfaceDark
                )

                // Battery Percent & Voltage
                StatRow(
                    icon = Icons.Default.BatteryChargingFull,
                    iconTint = LimePrimaryDark,
                    label = "$batteryPercent%  %.1fv".format(voltageV)
                )

                // Thermal Temp
                StatRow(
                    icon = Icons.Default.LocalFireDepartment,
                    iconTint = Color(0xFFEF5350),
                    label = "%.1f°C".format(batteryTempC)
                )
            }
        }

        // Right Card: System & Platform
        Card(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onAboutClicked),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(OutlineDark))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Android & SDK
                StatRow(
                    icon = Icons.Default.Android,
                    iconTint = Color(0xFF81C784),
                    label = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
                )

                // Uptime
                StatRow(
                    icon = Icons.Default.Schedule,
                    iconTint = Color(0xFF64B5F6),
                    label = "Up time  $formattedUptime"
                )

                // About HyTune
                StatRow(
                    icon = Icons.Default.Info,
                    iconTint = LimePrimaryDark,
                    label = "About HyTune"
                )
            }
        }
    }
}

@Composable
private fun StatRow(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    valueColor: Color = OnSurfaceDark
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            fontSize = 12.sp
        )
    }
}
