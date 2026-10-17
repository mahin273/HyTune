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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mahin.hytune.domain.storage.model.BlockDeviceQueue
import com.mahin.hytune.domain.storage.model.FsTrimResult
import com.mahin.hytune.ui.theme.MonospaceCodeStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StorageCard(
    blockDevices: List<BlockDeviceQueue>,
    stagedSchedulers: Map<String, String>,
    stagedReadAhead: Map<String, Int>,
    fstrimResult: FsTrimResult?,
    isDirty: (String?) -> Boolean,
    isApplying: Boolean,
    isTrimming: Boolean,
    onSchedulerChanged: (deviceName: String, scheduler: String) -> Unit,
    onReadAheadChanged: (deviceName: String, readAheadKb: Int) -> Unit,
    onApplyClicked: (deviceName: String?) -> Unit,
    onTrimClicked: () -> Unit,
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
                        text = "Storage & I/O Schedulers",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Block queue elevator algorithms and read-ahead buffers",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "${blockDevices.size} Devices",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (blockDevices.isEmpty()) {
                Text(
                    text = "No configurable physical block devices detected in /sys/block.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                blockDevices.forEach { dev ->
                    DeviceSection(
                        device = dev,
                        stagedScheduler = stagedSchedulers[dev.name] ?: dev.currentScheduler,
                        stagedReadAheadKb = stagedReadAhead[dev.name] ?: dev.readAheadKb,
                        onSchedulerChanged = { onSchedulerChanged(dev.name, it) },
                        onReadAheadChanged = { onReadAheadChanged(dev.name, it) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }

                // Global Apply Button
                Button(
                    onClick = { onApplyClicked(null) },
                    enabled = isDirty(null) && !isApplying,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isApplying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Apply Storage Settings")
                    }
                }
            }

            // Filesystem TRIM Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Flash Storage Maintenance (fstrim)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Informs flash controllers of freed data blocks, reducing write amplification and restoring flash endurance.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (fstrimResult != null) {
                    Text(
                        text = "Last TRIM: ${formatBytes(fstrimResult.trimmedBytes)} on ${fstrimResult.mountPoint}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonospaceCodeStyle.fontFamily),
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }

                OutlinedButton(
                    onClick = onTrimClicked,
                    enabled = !isTrimming && !isApplying,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isTrimming) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Run fstrim (/data)")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DeviceSection(
    device: BlockDeviceQueue,
    stagedScheduler: String,
    stagedReadAheadKb: Int,
    onSchedulerChanged: (String) -> Unit,
    onReadAheadChanged: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonospaceCodeStyle.fontFamily),
                    color = MaterialTheme.colorScheme.onSurface
                )
                val typeLabel = if (device.isRotational) "Rotational HDD" else "Flash (UFS / eMMC)"
                val sizeLabel = if (device.sizeBytes > 0L) " • ${formatBytes(device.sizeBytes)}" else ""
                Text(
                    text = "$typeLabel$sizeLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Scheduler Selector
        if (device.availableSchedulers.isNotEmpty()) {
            var expanded by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = stagedScheduler,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("I/O Scheduler (${device.name})") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    device.availableSchedulers.forEach { sched ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(sched, style = MaterialTheme.typography.bodyMedium)
                                    val desc = schedulerDescription(sched)
                                    if (desc.isNotEmpty()) {
                                        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            },
                            onClick = {
                                onSchedulerChanged(sched)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        // Read-Ahead KB
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Read-Ahead Buffer",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$stagedReadAheadKb KB",
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonospaceCodeStyle.fontFamily),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            val readAheadPresets = listOf(128, 256, 512, 1024, 2048)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                readAheadPresets.forEach { kb ->
                    val isSelected = stagedReadAheadKb == kb
                    FilterChip(
                        selected = isSelected,
                        onClick = { onReadAheadChanged(kb) },
                        label = { Text("$kb KB") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
        }
    }
}

private fun schedulerDescription(scheduler: String): String {
    return when (scheduler.lowercase()) {
        "none" -> "Direct hardware queue bypass (low CPU latency, optimal for fast UFS/NVMe)"
        "mq-deadline" -> "Guaranteed latency deadlines; prioritizes reads over writes (Android default)"
        "bfq" -> "Budget Fair Queueing; smooth interactive priority during heavy background I/O"
        "kyber" -> "Dynamic queue throttling; enforces target read latencies"
        else -> ""
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
