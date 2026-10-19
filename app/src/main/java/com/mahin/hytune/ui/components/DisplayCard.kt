package com.mahin.hytune.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mahin.hytune.domain.display.model.DisplayStateSnapshot
import com.mahin.hytune.ui.display.DisplayUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DisplayCard(
    uiState: DisplayUiState,
    onApplyResolution: (width: Int, height: Int, autoScaleDpi: Boolean) -> Unit,
    onApplyDensity: (dpi: Int) -> Unit,
    onApplyRefreshRate: (minHz: Float, peakHz: Float) -> Unit,
    onResetRefreshRate: () -> Unit,
    onConfirmChanges: () -> Unit,
    onRevertChanges: () -> Unit,
    onResetAllDefaults: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snapshot = uiState.snapshot

    var customWidth by remember(snapshot?.activeWidth) {
        mutableStateOf(snapshot?.activeWidth?.toString() ?: "1080")
    }
    var customHeight by remember(snapshot?.activeHeight) {
        mutableStateOf(snapshot?.activeHeight?.toString() ?: "2400")
    }
    var customDpi by remember(snapshot?.activeDpi) {
        mutableStateOf(snapshot?.activeDpi?.toString() ?: "420")
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Display Tweaks",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Custom resolution downscaling, software DPI, and refresh rate control with safety rollback.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            if (snapshot != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Current State",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Resolution: ${snapshot.activeResolutionString}" +
                                        if (snapshot.overrideWidth != null) " (Scaled)" else " (Native)",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "DPI: ${snapshot.activeDpi}" +
                                        if (snapshot.overrideDpi != null) " (Custom)" else "",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        val refreshText = when {
                            snapshot.peakRefreshRateHz != null && snapshot.minRefreshRateHz != null -> {
                                if (snapshot.minRefreshRateHz == snapshot.peakRefreshRateHz) {
                                    "Locked ${snapshot.peakRefreshRateHz.toInt()}Hz"
                                } else {
                                    "${snapshot.minRefreshRateHz.toInt()}Hz - ${snapshot.peakRefreshRateHz.toInt()}Hz"
                                }
                            }
                            else -> "Dynamic System Default"
                        }
                        Text(
                            text = "Refresh Rate: $refreshText",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Resolution Presets
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Resolution Presets",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val pWidth = snapshot.physicalWidth
                        val pHeight = snapshot.physicalHeight

                        FilterChip(
                            selected = snapshot.overrideWidth == null,
                            onClick = { onApplyResolution(pWidth, pHeight, true) },
                            label = { Text("Native (${pWidth}x${pHeight})") }
                        )

                        val scale75W = (pWidth * 0.75f).toInt()
                        val scale75H = (pHeight * 0.75f).toInt()
                        FilterChip(
                            selected = snapshot.overrideWidth == scale75W,
                            onClick = { onApplyResolution(scale75W, scale75H, true) },
                            label = { Text("75% (${scale75W}x${scale75H})") }
                        )

                        val scale50W = (pWidth * 0.50f).toInt()
                        val scale50H = (pHeight * 0.50f).toInt()
                        FilterChip(
                            selected = snapshot.overrideWidth == scale50W,
                            onClick = { onApplyResolution(scale50W, scale50H, true) },
                            label = { Text("50% (${scale50W}x${scale50H})") }
                        )
                    }
                }

                // Custom Resolution Inputs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = customWidth,
                        onValueChange = { customWidth = it },
                        label = { Text("Width") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = customHeight,
                        onValueChange = { customHeight = it },
                        label = { Text("Height") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val w = customWidth.toIntOrNull() ?: snapshot.activeWidth
                            val h = customHeight.toIntOrNull() ?: snapshot.activeHeight
                            onApplyResolution(w, h, true)
                        },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("Apply")
                    }
                }

                // Density Presets
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Display Density (DPI)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(320, 360, 400, 420, 440, 480).forEach { dpiVal ->
                            FilterChip(
                                selected = snapshot.activeDpi == dpiVal,
                                onClick = { onApplyDensity(dpiVal) },
                                label = { Text("${dpiVal} DPI") }
                            )
                        }
                    }
                }

                // Refresh Rate Selection
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Refresh Rate Lock",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = snapshot.minRefreshRateHz == null && snapshot.peakRefreshRateHz == null,
                            onClick = onResetRefreshRate,
                            label = { Text("Auto / Dynamic") }
                        )
                        listOf(60f, 90f, 120f, 144f).forEach { hzVal ->
                            FilterChip(
                                selected = snapshot.minRefreshRateHz == hzVal && snapshot.peakRefreshRateHz == hzVal,
                                onClick = { onApplyRefreshRate(hzVal, hzVal) },
                                label = { Text("${hzVal.toInt()}Hz Lock") }
                            )
                        }
                    }
                }

                // Reset Action
                OutlinedButton(
                    onClick = onResetAllDefaults,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reset Display to Stock Defaults")
                }
            }
        }
    }

    // 15-Second Safety Countdown Confirmation Dialog
    if (uiState.countdownRemainingSec != null) {
        AlertDialog(
            onDismissRequest = onRevertChanges,
            title = {
                Text("Confirm Display Settings")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("New display configuration applied. To prevent blackouts or unreachable UI, changes will automatically revert unless confirmed.")
                    Text(
                        text = "Reverting in ${uiState.countdownRemainingSec} seconds...",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = onConfirmChanges,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Keep Changes")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onRevertChanges) {
                    Text("Revert Now")
                }
            }
        )
    }
}
