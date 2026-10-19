package com.mahin.hytune.ui.tools

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mahin.hytune.domain.script.model.CustomScript
import com.mahin.hytune.domain.script.model.ScriptExecutionResult

@Composable
fun ScriptLibraryCard(
    uiState: ScriptUiState,
    onRunScript: (Long) -> Unit,
    onSaveScript: (CustomScript) -> Unit,
    onDeleteScript: (Long) -> Unit,
    onToggleBoot: (Long, Boolean) -> Unit,
    onDismissResult: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingScript by remember { mutableStateOf<CustomScript?>(null) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Custom Script Library",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Automate shell tasks and kernel tweaks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                Button(onClick = { showAddDialog = true }) {
                    Text("New Script")
                }
            }

            AnimatedVisibility(visible = uiState.errorMessage != null) {
                uiState.errorMessage?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (uiState.scripts.isEmpty()) {
                Text(
                    text = "No custom scripts saved. Tap New Script to add one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                uiState.scripts.forEach { script ->
                    ScriptRowItem(
                        script = script,
                        isExecuting = uiState.isExecuting && uiState.executingScriptId == script.id,
                        onRun = { onRunScript(script.id) },
                        onEdit = { editingScript = script },
                        onDelete = { onDeleteScript(script.id) },
                        onToggleBoot = { enabled -> onToggleBoot(script.id, enabled) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                }
            }
        }
    }

    if (showAddDialog) {
        ScriptEditDialog(
            script = null,
            onDismiss = { showAddDialog = false },
            onSave = {
                onSaveScript(it)
                showAddDialog = false
            }
        )
    }

    editingScript?.let { script ->
        ScriptEditDialog(
            script = script,
            onDismiss = { editingScript = null },
            onSave = {
                onSaveScript(it)
                editingScript = null
            }
        )
    }

    uiState.lastExecutionResult?.let { result ->
        ExecutionLogDialog(
            result = result,
            onDismiss = onDismissResult
        )
    }
}

@Composable
private fun ScriptRowItem(
    script: CustomScript,
    isExecuting: Boolean,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleBoot: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = script.name,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = if (script.runAsRoot) "Root privilege" else "Standard shell",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Boot",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(end = 4.dp)
                )
                Switch(
                    checked = script.runAtBoot,
                    onCheckedChange = onToggleBoot
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onRun,
                    enabled = !isExecuting
                ) {
                    Text(if (isExecuting) "Running..." else "Run")
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = script.scriptContent,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                modifier = Modifier.padding(6.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (script.lastExecutedEpochMs > 0) {
                Text(
                    text = "Exit: ${script.lastExitCode ?: 0} (${script.lastDurationMs}ms)",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (script.lastExitCode == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            } else {
                Text(
                    text = "Never executed",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }

            Row {
                OutlinedButton(onClick = onEdit) {
                    Text("Edit")
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(onClick = onDelete) {
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun ScriptEditDialog(
    script: CustomScript?,
    onDismiss: () -> Unit,
    onSave: (CustomScript) -> Unit
) {
    var name by remember { mutableStateOf(script?.name ?: "") }
    var content by remember { mutableStateOf(script?.scriptContent ?: "") }
    var runAtBoot by remember { mutableStateOf(script?.runAtBoot ?: false) }
    var runAsRoot by remember { mutableStateOf(script?.runAsRoot ?: true) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (script == null) "Create Shell Script" else "Edit Shell Script",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Script Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Shell Script Content") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Run as Root", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = runAsRoot, onCheckedChange = { runAsRoot = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Run at Device Boot", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = runAtBoot, onCheckedChange = { runAtBoot = it })
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank() && content.isNotBlank()) {
                                onSave(
                                    CustomScript(
                                        id = script?.id ?: 0L,
                                        name = name.trim(),
                                        scriptContent = content.trim(),
                                        runAtBoot = runAtBoot,
                                        runAsRoot = runAsRoot,
                                        lastExecutedEpochMs = script?.lastExecutedEpochMs ?: 0L,
                                        lastExitCode = script?.lastExitCode,
                                        lastOutput = script?.lastOutput,
                                        lastDurationMs = script?.lastDurationMs ?: 0L,
                                        createdAtEpochMs = script?.createdAtEpochMs ?: System.currentTimeMillis()
                                    )
                                )
                            }
                        },
                        enabled = name.isNotBlank() && content.isNotBlank()
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
private fun ExecutionLogDialog(
    result: ScriptExecutionResult,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (result.isSuccess) "Execution Successful" else "Execution Failed",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (result.isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )

                Text(
                    text = "Exit Code: ${result.exitCode} | Duration: ${result.durationMs}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                ) {
                    SelectionContainer {
                        val displayLog = when {
                            result.output.isNotEmpty() -> result.output
                            result.errorOutput.isNotEmpty() -> result.errorOutput
                            result.isTimedOut -> "Execution timed out"
                            else -> "No output produced"
                        }
                        Text(
                            text = displayLog,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}
