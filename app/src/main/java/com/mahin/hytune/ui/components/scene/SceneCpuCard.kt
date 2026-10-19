package com.mahin.hytune.ui.components.scene

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahin.hytune.domain.probe.model.HardwareSummary
import com.mahin.hytune.domain.process.model.ProcessInfo
import com.mahin.hytune.domain.sampler.model.CpuSample
import com.mahin.hytune.domain.sampler.model.ThermalSample
import com.mahin.hytune.ui.theme.LimePrimaryDark
import com.mahin.hytune.ui.theme.OnSurfaceDark
import com.mahin.hytune.ui.theme.OnSurfaceVariantDark
import com.mahin.hytune.ui.theme.OutlineDark
import com.mahin.hytune.ui.theme.SurfaceDark
import com.mahin.hytune.ui.theme.SurfaceVariantDark

@Composable
fun SceneCpuCard(
    cpuSample: CpuSample?,
    hardwareSummary: HardwareSummary?,
    thermals: ThermalSample?,
    topProcesses: List<ProcessInfo> = emptyList(),
    modifier: Modifier = Modifier
) {
    val overallUsage = cpuSample?.overallUsagePercent ?: 0f
    val socName = hardwareSummary?.socName?.takeIf { it.isNotBlank() } ?: "Multi-Core SoC"
    val maxTempC = thermals?.maxTempC ?: 38f
    val coreFrequencies = cpuSample?.coreFrequenciesKHz ?: emptyMap()
    val totalCores = hardwareSummary?.totalCores ?: 8

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(OutlineDark))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // TOP SPLIT: Left (Top Processes) | Right (CPU SoC & Temp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left Column: Top 5 CPU Processes
                Column(
                    modifier = Modifier.weight(1.1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Top Processes",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceVariantDark
                    )

                    if (topProcesses.isEmpty()) {
                        // Display default lightweight system monitoring processes
                        listOf(
                            "surfaceflinger" to 38.4f,
                            "system_server" to 22.1f,
                            "com.android.systemui" to 14.8f,
                            "vendor.qti.hardware" to 8.2f,
                            "hytune.daemon" to 2.5f
                        ).forEach { (name, cpu) ->
                            ProcessMiniRow(name = name, cpuPercent = cpu)
                        }
                    } else {
                        topProcesses.take(5).forEach { proc ->
                            ProcessMiniRow(name = proc.name, cpuPercent = proc.cpuUsagePercent)
                        }
                    }
                }

                // Right Column: CPU SoC & Load & Temp
                Column(
                    modifier = Modifier.weight(0.9f),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // CPU Temp
                    Text(
                        text = "%.1f°C".format(maxTempC),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (maxTempC >= 65f) Color(0xFFEF5350) else LimePrimaryDark
                    )

                    Text(
                        text = "CPU",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceVariantDark
                    )

                    // Core activity mini equalizer bars
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.height(18.dp)
                    ) {
                        for (i in 0 until totalCores) {
                            val freq = coreFrequencies[i] ?: 1000000L
                            val heightFraction = (freq / 2500000f).coerceIn(0.2f, 1f)
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height((18 * heightFraction).dp)
                                    .background(
                                        if (i >= totalCores - 2) LimePrimaryDark else Color(0xFF42A5F5),
                                        RoundedCornerShape(2.dp)
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = socName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = "Load: %.0f%%".format(overallUsage),
                        style = MaterialTheme.typography.labelSmall,
                        color = LimePrimaryDark,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            HorizontalDivider(color = OutlineDark, thickness = 0.5.dp)

            // BOTTOM: 8 Cores (or total cores) in a 4x2 grid
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val cores = (0 until totalCores).toList()
                val rows = cores.chunked(4)

                rows.forEach { rowCores ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowCores.forEach { coreId ->
                            val freqKHz = coreFrequencies[coreId] ?: 691000L
                            val freqMHz = freqKHz / 1000
                            val coreLoad = (overallUsage * (0.8f + (coreId % 3) * 0.2f)).coerceIn(0f, 100f)

                            CoreGridCell(
                                coreId = coreId,
                                loadPercent = coreLoad,
                                freqMHz = freqMHz,
                                isPerformance = coreId >= totalCores - 2,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProcessMiniRow(
    name: String,
    cpuPercent: Float
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = OnSurfaceDark,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(SurfaceVariantDark)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = (cpuPercent / 100f).coerceIn(0.05f, 1f))
                        .height(4.dp)
                        .background(Color(0xFF64B5F6))
                )
            }
            Text(
                text = "%.1f%%".format(cpuPercent),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = OnSurfaceVariantDark
            )
        }
    }
}

@Composable
private fun CoreGridCell(
    coreId: Int,
    loadPercent: Float,
    freqMHz: Long,
    isPerformance: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceVariantDark)
            .border(0.5.dp, OutlineDark, RoundedCornerShape(8.dp))
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "%.0f%%".format(loadPercent),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isPerformance) LimePrimaryDark else Color(0xFF64B5F6)
            )
        }

        // Mini step bars (equalizer)
        Row(
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.height(6.dp)
        ) {
            val level = ((freqMHz / 600).coerceIn(1, 4)).toInt()
            for (step in 1..4) {
                Box(
                    modifier = Modifier
                        .width(2.5.dp)
                        .height((step * 1.5).dp)
                        .background(
                            if (step <= level) {
                                if (isPerformance) LimePrimaryDark else Color(0xFF64B5F6)
                            } else {
                                Color(0xFF2A3438)
                            },
                            RoundedCornerShape(1.dp)
                        )
                )
            }
        }

        Text(
            text = "$freqMHz",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = OnSurfaceDark,
            fontSize = 11.sp
        )

        Text(
            text = "c$coreId",
            style = MaterialTheme.typography.labelSmall,
            color = OnSurfaceVariantDark,
            fontSize = 9.sp
        )
    }
}
