package com.mahin.hytune.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
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
import com.mahin.hytune.domain.cpu.model.CpuClusterPolicy
import com.mahin.hytune.ui.theme.MonospaceCodeStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CpuClusterCard(
    policy: CpuClusterPolicy,
    stagedGovernor: String,
    stagedMinFreqKHz: Long,
    stagedMaxFreqKHz: Long,
    liveFreqKHz: Long?,
    isDirty: Boolean,
    isApplying: Boolean,
    onGovernorChanged: (String) -> Unit,
    onMinFreqChanged: (Long) -> Unit,
    onMaxFreqChanged: (Long) -> Unit,
    onApplyClicked: () -> Unit,
    onResetClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    var dropdownExpanded by remember { mutableStateOf(false) }

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
                Column {
                    val clusterTitle = "Cluster ${policy.clusterId}: ${policy.type.name.lowercase().replaceFirstChar { it.uppercase() }}"
                    Text(
                        text = clusterTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Cores: ${policy.affectedCores.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (liveFreqKHz != null && liveFreqKHz > 0) {
                    val ghz = liveFreqKHz / 1_000_000f
                    Text(
                        text = "%.2f GHz".format(ghz),
                        style = MonospaceCodeStyle.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = MaterialTheme.typography.titleMedium.fontSize
                        )
                    )
                }
            }

            if (policy.availableGovernors.isNotEmpty()) {
                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = stagedGovernor,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Scaling Governor") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable),
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                    )

                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        policy.availableGovernors.forEach { gov ->
                            DropdownMenuItem(
                                text = { Text(gov) },
                                onClick = {
                                    onGovernorChanged(gov)
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            FrequencySlider(
                label = "Minimum Frequency",
                currentFreqKHz = stagedMinFreqKHz,
                availableFrequenciesKHz = policy.availableFrequenciesKHz,
                onFreqChanged = onMinFreqChanged
            )

            FrequencySlider(
                label = "Maximum Frequency",
                currentFreqKHz = stagedMaxFreqKHz,
                availableFrequenciesKHz = policy.availableFrequenciesKHz,
                onFreqChanged = onMaxFreqChanged
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onResetClicked,
                    enabled = isDirty && !isApplying,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text("Reset")
                }

                Button(
                    onClick = onApplyClicked,
                    enabled = isDirty && !isApplying
                ) {
                    Text(if (isApplying) "Applying..." else "Apply")
                }
            }
        }
    }
}
