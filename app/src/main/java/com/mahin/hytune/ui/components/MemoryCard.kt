package com.mahin.hytune.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mahin.hytune.domain.memory.model.DropCacheType
import com.mahin.hytune.domain.memory.model.VmTunables
import com.mahin.hytune.domain.memory.model.ZramInfo
import com.mahin.hytune.ui.theme.MonospaceCodeStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MemoryCard(
    vmTunables: VmTunables?,
    zramInfo: ZramInfo?,
    stagedSwappiness: Int?,
    stagedVfsCachePressure: Int?,
    stagedZramSizeBytes: Long?,
    stagedAlgorithm: String?,
    isDirty: Boolean,
    isApplying: Boolean,
    isTrimming: Boolean,
    isCompacting: Boolean,
    onSwappinessChanged: (Int) -> Unit,
    onVfsCachePressureChanged: (Int) -> Unit,
    onZramSizeChanged: (Long) -> Unit,
    onAlgorithmChanged: (String) -> Unit,
    onTrimCachesClicked: (DropCacheType) -> Unit,
    onCompactZramClicked: () -> Unit,
    onApplyClicked: () -> Unit,
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
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Memory & ZRAM Swap",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Virtual memory parameters and compressed swap",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (zramInfo != null) {
                    val statusText = if (zramInfo.enabled) "Active" else "Disabled"
                    val statusColor = if (zramInfo.enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ZRAM Section
            if (zramInfo != null) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ZRAM Disk Size",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val effectiveSize = stagedZramSizeBytes ?: zramInfo.disksizeBytes
                        Text(
                            text = formatBytes(effectiveSize),
                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonospaceCodeStyle.fontFamily),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Compression Statistics
                    if (zramInfo.enabled && zramInfo.comprSizeBytes > 0L) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Compressed Pool",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${formatBytes(zramInfo.origSizeBytes)} -> ${formatBytes(zramInfo.comprSizeBytes)}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonospaceCodeStyle.fontFamily),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Efficiency",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = String.format(Locale.US, "%.1fx (%d%% saved)", zramInfo.compressionRatio, zramInfo.spaceSavingsPercent),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonospaceCodeStyle.fontFamily),
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }

                    // Discrete ZRAM Size Chips
                    val currentTarget = stagedZramSizeBytes ?: zramInfo.disksizeBytes
                    val sizeOptions = listOf(
                        0L to "Off",
                        1L * 1024 * 1024 * 1024 to "1 GB",
                        2L * 1024 * 1024 * 1024 to "2 GB",
                        3L * 1024 * 1024 * 1024 to "3 GB",
                        4L * 1024 * 1024 * 1024 to "4 GB",
                        6L * 1024 * 1024 * 1024 to "6 GB",
                        8L * 1024 * 1024 * 1024 to "8 GB"
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        sizeOptions.forEach { (bytes, label) ->
                            val isSelected = currentTarget == bytes
                            FilterChip(
                                selected = isSelected,
                                onClick = { onZramSizeChanged(bytes) },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }

                    // Algorithm Selector
                    if (zramInfo.availableAlgorithms.isNotEmpty()) {
                        var algoDropdownExpanded by remember { mutableStateOf(false) }
                        val currentAlgo = stagedAlgorithm ?: zramInfo.activeAlgorithm

                        ExposedDropdownMenuBox(
                            expanded = algoDropdownExpanded,
                            onExpandedChange = { algoDropdownExpanded = !algoDropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = currentAlgo,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Compression Algorithm") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = algoDropdownExpanded) },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            )
                            ExposedDropdownMenu(
                                expanded = algoDropdownExpanded,
                                onDismissRequest = { algoDropdownExpanded = false }
                            ) {
                                zramInfo.availableAlgorithms.forEach { algo ->
                                    DropdownMenuItem(
                                        text = { Text(algo) },
                                        onClick = {
                                            onAlgorithmChanged(algo)
                                            algoDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            // Linux VM Tunables Section
            val swappinessVal = stagedSwappiness ?: vmTunables?.swappiness ?: 60
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Swappiness",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$swappinessVal",
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonospaceCodeStyle.fontFamily),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = if (swappinessVal < 60) "Prefers dropping filesystem page caches" else if (swappinessVal > 100) "Aggressively swaps anonymous memory" else "Balanced swap vs cache reclaim",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = swappinessVal.toFloat(),
                    onValueChange = { onSwappinessChanged(it.toInt()) },
                    valueRange = 0f..200f,
                    steps = 19
                )
            }

            val vfsPressureVal = stagedVfsCachePressure ?: vmTunables?.vfsCachePressure ?: 100
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "VFS Cache Pressure",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$vfsPressureVal",
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonospaceCodeStyle.fontFamily),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = if (vfsPressureVal < 100) "Retains directory/inode trees longer" else "Reclaims directory inodes faster to free RAM",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = vfsPressureVal.toFloat(),
                    onValueChange = { onVfsCachePressureChanged(it.toInt()) },
                    valueRange = 0f..200f,
                    steps = 19
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Quick Actions: Drop Caches and Compact ZRAM
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onTrimCachesClicked(DropCacheType.ALL) },
                    enabled = !isTrimming && !isApplying,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isTrimming) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Drop Caches")
                    }
                }

                if (zramInfo?.enabled == true) {
                    OutlinedButton(
                        onClick = onCompactZramClicked,
                        enabled = !isCompacting && !isApplying,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isCompacting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Compact ZRAM")
                        }
                    }
                }
            }

            // Apply Button
            Button(
                onClick = onApplyClicked,
                enabled = isDirty && !isApplying,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isApplying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Apply Memory Settings")
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes / (1024L * 1024L)
    return if (mb >= 1024L) {
        String.format(Locale.US, "%.1f GB", mb / 1024.0)
    } else {
        "$mb MB"
    }
}
