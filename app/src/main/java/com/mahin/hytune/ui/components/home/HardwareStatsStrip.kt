package com.mahin.hytune.ui.components.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahin.hytune.domain.probe.model.HardwareSummary
import com.mahin.hytune.domain.sampler.model.SystemSnapshot
import kotlin.math.abs

@Composable
fun HardwareStatsStrip(
    summary: HardwareSummary?,
    snapshot: SystemSnapshot?,
    modifier: Modifier = Modifier
) {
    val totalCores = summary?.totalCores ?: 8
    val maxClockGhz = (summary?.clusters?.maxOfOrNull { it.maxFreqKHz } ?: 2_400_000L) / 1_000_000f

    val totalRamGb = if (snapshot != null) {
        snapshot.memory.totalBytes / (1024f * 1024f * 1024f)
    } else 8.0f
    val availRamGb = if (snapshot != null) {
        snapshot.memory.availableBytes / (1024f * 1024f * 1024f)
    } else 4.0f

    val batteryPercent = snapshot?.battery?.percent ?: 78
    val batteryPower = snapshot?.battery?.let { abs(it.powerWatts) } ?: 0f

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // CPU Column
        StatStripCard(
            label = "CPU",
            primaryValue = "%.1f GHz".format(maxClockGhz),
            subtitle = "($totalCores cores)",
            modifier = Modifier.weight(1f)
        )

        // RAM Column
        StatStripCard(
            label = "RAM",
            primaryValue = "%.1f GB".format(totalRamGb),
            subtitle = "%.1f GB free".format(availRamGb),
            modifier = Modifier.weight(1f)
        )

        // Battery Column
        StatStripCard(
            label = "Battery",
            primaryValue = "$batteryPercent%",
            subtitle = if (batteryPower > 0f) "%.1f W".format(batteryPower) else "+ 2h 14m",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatStripCard(
    label: String,
    primaryValue: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Text(
                text = primaryValue,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
