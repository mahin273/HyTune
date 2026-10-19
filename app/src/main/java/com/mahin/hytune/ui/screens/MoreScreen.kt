package com.mahin.hytune.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.mahin.hytune.service.model.DaemonState
import com.mahin.hytune.ui.daemon.DaemonViewModel
import com.mahin.hytune.ui.safety.SafetyViewModel

@Composable
fun MoreScreen(
    safetyViewModel: SafetyViewModel = hiltViewModel(),
    daemonViewModel: DaemonViewModel = hiltViewModel(),
    automationViewModel: com.mahin.hytune.ui.automation.AutomationViewModel = hiltViewModel(),
    scriptViewModel: com.mahin.hytune.ui.tools.ScriptViewModel = hiltViewModel(),
    sysfsViewModel: com.mahin.hytune.ui.tools.SysfsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val safetyState by safetyViewModel.safetyState.collectAsState()
    val isPanicRestoring by safetyViewModel.isPanicRestoring.collectAsState()
    val statusMessage by safetyViewModel.statusMessage.collectAsState()

    val daemonState by daemonViewModel.daemonState.collectAsState()
    val isBatteryOptimizationIgnored by daemonViewModel.isBatteryOptimizationIgnored.collectAsState()
    val automationState by automationViewModel.uiState.collectAsState()
    val scriptState by scriptViewModel.uiState.collectAsState()
    val sysfsState by sysfsViewModel.uiState.collectAsState()

    var showPanicConfirmDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Automation Rules Card
        com.mahin.hytune.ui.automation.AutomationRulesCard(
            uiState = automationState,
            onToggleEngine = { automationViewModel.toggleEngine(it) },
            onToggleRule = { id, enabled -> automationViewModel.toggleRuleEnabled(id, enabled) },
            onDeleteRule = { automationViewModel.deleteRule(it) },
            onSaveRule = { automationViewModel.saveRule(it) }
        )

        // Custom Script Library Card
        com.mahin.hytune.ui.tools.ScriptLibraryCard(
            uiState = scriptState,
            onRunScript = { scriptViewModel.executeScript(it) },
            onSaveScript = { scriptViewModel.saveScript(it) },
            onDeleteScript = { scriptViewModel.deleteScript(it) },
            onToggleBoot = { id, boot -> scriptViewModel.toggleRunAtBoot(id, boot) },
            onDismissResult = { scriptViewModel.clearExecutionResult() }
        )

        // Raw Sysfs Browser & Editor Card
        com.mahin.hytune.ui.tools.SysfsEditorCard(
            uiState = sysfsState,
            onNavigateTo = { sysfsViewModel.navigateTo(it) },
            onNavigateUp = { sysfsViewModel.navigateUp() },
            onSelectNode = { sysfsViewModel.selectNode(it) },
            onWriteNode = { path, value -> sysfsViewModel.writeNodeValue(path, value) },
            onClearSelectedNode = { sysfsViewModel.clearSelectedNode() }
        )

        // Daemon Foreground Service Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = when (daemonState) {
                    DaemonState.RUNNING -> MaterialTheme.colorScheme.primaryContainer
                    DaemonState.STARTING, DaemonState.STOPPING -> MaterialTheme.colorScheme.secondaryContainer
                    DaemonState.STOPPED -> MaterialTheme.colorScheme.surfaceContainerHigh
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Background Daemon Service",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = when (daemonState) {
                                DaemonState.RUNNING -> MaterialTheme.colorScheme.onPrimaryContainer
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                        Text(
                            text = "Status: ${daemonState.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = when (daemonState) {
                                DaemonState.RUNNING -> MaterialTheme.colorScheme.onPrimaryContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    Switch(
                        checked = daemonState == DaemonState.RUNNING || daemonState == DaemonState.STARTING,
                        onCheckedChange = { isChecked ->
                            daemonViewModel.toggleDaemon(isChecked)
                        }
                    )
                }

                Text(
                    text = "Runs a persistent foreground service with ongoing notification to maintain profile enforcement, thermal polling, and per-app switching across app suspension.",
                    style = MaterialTheme.typography.bodySmall,
                    color = when (daemonState) {
                        DaemonState.RUNNING -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                if (!isBatteryOptimizationIgnored) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Battery Optimization Active",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "Android Doze may suspend daemon background operations when the screen is turned off. Whitelisting HyTune ensures uninterrupted hardware control.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Button(
                                onClick = {
                                    try {
                                        context.startActivity(daemonViewModel.createRequestIgnoreBatteryOptimizationsIntent())
                                    } catch (_: Exception) {
                                        // Ignore activity launch issues on restricted environments
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("Exempt App from Doze")
                            }
                        }
                    }
                }
            }
        }

        // Safety & Panic Recovery Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (safetyState.isSafeRecoveryModeActive) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Safety & Recovery System",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (safetyState.isSafeRecoveryModeActive) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )

                if (safetyState.isSafeRecoveryModeActive) {
                    Text(
                        text = "SAFE RECOVERY MODE ACTIVE: Consecutive crashes detected during tuning. Hardware profiles locked down to protect system stability.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Bootloop Crash Counter",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Consecutive crashes: ${safetyState.bootloopCrashCount} / 2",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (safetyState.bootloopCrashCount > 0 || safetyState.isSafeRecoveryModeActive) {
                        OutlinedButton(
                            onClick = { safetyViewModel.resetCrashCounter() }
                        ) {
                            Text("Reset")
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Dry-Run Safety Preview",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Inspect all proposed sysfs changes before applying",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = safetyState.dryRunPreviewEnabled,
                        onCheckedChange = { safetyViewModel.setDryRunPreviewEnabled(it) }
                    )
                }

                Button(
                    onClick = { showPanicConfirmDialog = true },
                    enabled = !isPanicRestoring,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isPanicRestoring) "Restoring Factory Baseline..." else "Panic Restore (Factory Baseline)",
                        fontWeight = FontWeight.Bold
                    )
                }

                statusMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Tools & Automation Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Tools & Automation",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Shell script executor, event-based automation, privilege status, and application preferences.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Configuration Options",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Privilege Backend Selector\nBoot Automation & Script Runner\nAMOLED Dark Mode Toggle\nBackup & Restore Configuration",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showPanicConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showPanicConfirmDialog = false },
            title = {
                Text("Confirm Panic Restore")
            },
            text = {
                Text("This will immediately revert all CPU, GPU, Memory, and Storage tunables back to the stock hardware baseline captured at first launch. Do you want to proceed?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPanicConfirmDialog = false
                        safetyViewModel.panicRestore()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Restore Baseline")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showPanicConfirmDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
