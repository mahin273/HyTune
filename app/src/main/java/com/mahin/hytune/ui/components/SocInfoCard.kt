package com.mahin.hytune.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahin.hytune.domain.probe.model.ClusterType
import com.mahin.hytune.domain.probe.model.CpuClusterInfo
import com.mahin.hytune.domain.probe.model.HardwareSummary
import com.mahin.hytune.domain.sampler.model.ThermalSample
import com.mahin.hytune.ui.theme.CyanPrimaryDark
import com.mahin.hytune.ui.theme.MintSecondaryDark
import com.mahin.hytune.ui.theme.MonospaceCodeStyle

@Composable
fun SocInfoCard(
    summary: HardwareSummary,
    modifier: Modifier = Modifier,
    coreFrequencies: Map<Int, Long> = emptyMap(),
    thermals: ThermalSample? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: SoC name and Total Core Count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = summary.socName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Processor Architecture & Topology",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = CyanPrimaryDark.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, CyanPrimaryDark.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "${summary.totalCores} CORES",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = CyanPrimaryDark
                    )
                }
            }

            // Real-time CPU Clusters with animated frequency progress bars
            if (summary.clusters.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    summary.clusters.forEach { cluster ->
                        ClusterFrequencyBar(
                            cluster = cluster,
                            coreFrequencies = coreFrequencies
                        )
                    }
                }
            }

            // GPU and Thermal telemetry footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (summary.gpu != null) {
                    val gpuMaxMhz = summary.gpu.maxFreqKHz / 1_000f
                    Text(
                        text = "GPU: ${summary.gpu.vendor.name} @ ${"%.0f".format(gpuMaxMhz)} MHz",
                        style = MonospaceCodeStyle.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (thermals != null && thermals.socTempC > 0f) {
                    val tempColor = if (thermals.socTempC >= 75f) Color(0xFFFF5252) else MintSecondaryDark
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = tempColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "SoC: %.1f C".format(thermals.socTempC),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MonospaceCodeStyle.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = tempColor
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClusterFrequencyBar(
    cluster: CpuClusterInfo,
    coreFrequencies: Map<Int, Long>
) {
    val maxFreqMhz = (cluster.maxFreqKHz / 1000).toInt()
    val minFreqMhz = (cluster.minFreqKHz / 1000).toInt()

    // Find the current frequency of cores in this cluster
    val activeFreqKHz = cluster.coreIds.mapNotNull { coreFrequencies[it] }.maxOrNull() ?: cluster.minFreqKHz
    val currentFreqMhz = if (activeFreqKHz > 0) (activeFreqKHz / 1000).toInt() else minFreqMhz

    val progress = if (maxFreqMhz > 0) {
        (currentFreqMhz.toFloat() / maxFreqMhz.toFloat()).coerceIn(0.05f, 1f)
    } else 0f
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "ClusterFreqProgress")

    val typeLabel = when (cluster.type) {
        ClusterType.LITTLE -> "Silver"
        ClusterType.MID -> "Gold"
        ClusterType.BIG -> "Gold"
        ClusterType.PRIME -> "Prime"
        ClusterType.UNKNOWN -> "Cluster ${cluster.clusterId}"
    }

    val coreRangeText = if (cluster.coreIds.size > 1) {
        "Cores ${cluster.coreIds.minOrNull()}..${cluster.coreIds.maxOrNull()}"
    } else {
        "Core ${cluster.coreIds.firstOrNull() ?: cluster.clusterId}"
    }

    val barColor = when (cluster.type) {
        ClusterType.PRIME -> Color(0xFFFFB300)
        ClusterType.BIG, ClusterType.MID -> CyanPrimaryDark
        else -> MintSecondaryDark
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = barColor
                    )
                )
                Text(
                    text = "($coreRangeText)",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            Text(
                text = "$currentFreqMhz / $maxFreqMhz MHz",
                style = MonospaceCodeStyle.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
        }

        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = barColor,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    }
}
