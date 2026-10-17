package com.mahin.hytune.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mahin.hytune.domain.probe.model.HardwareSummary

@Composable
fun SocInfoCard(
    summary: HardwareSummary,
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = summary.socName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${summary.totalCores} Cores",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (summary.clusters.isNotEmpty()) {
                val clusterDescs = summary.clusters.map { cluster ->
                    val maxGhz = cluster.maxFreqKHz / 1_000_000f
                    "${cluster.coreIds.size}x ${cluster.type.name.lowercase().replaceFirstChar { it.uppercase() }} (%.1f GHz)".format(maxGhz)
                }
                Text(
                    text = clusterDescs.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (summary.gpu != null) {
                val gpuMaxMhz = summary.gpu.maxFreqKHz / 1_000f
                Text(
                    text = "GPU: ${summary.gpu.vendor.name} (${summary.gpu.basePath}) @ %.0f MHz".format(gpuMaxMhz),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
