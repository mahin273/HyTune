package com.mahin.hytune.ui.automation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import com.mahin.hytune.domain.automation.model.AutomationRule
import com.mahin.hytune.domain.automation.model.RuleActionType
import com.mahin.hytune.domain.automation.model.RuleTriggerType
import java.util.Locale

@Composable
fun AutomationRulesCard(
    uiState: AutomationUiState,
    onToggleEngine: (Boolean) -> Unit,
    onToggleRule: (Long, Boolean) -> Unit,
    onDeleteRule: (Long) -> Unit,
    onSaveRule: (AutomationRule) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Automation Rules",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = if (uiState.isEngineRunning) "Engine Active" else "Engine Paused",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (uiState.isEngineRunning) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                Switch(
                    checked = uiState.isEngineRunning,
                    onCheckedChange = onToggleEngine
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Current System State Summary
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Battery", style = MaterialTheme.typography.labelSmall)
                        Text(
                            "${uiState.currentSystemState.batteryPercent}% (${if (uiState.currentSystemState.isCharging) "AC" else "Batt"})",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Thermal", style = MaterialTheme.typography.labelSmall)
                        Text(
                            String.format(Locale.US, "%.1f C", uiState.currentSystemState.temperatureC),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Screen", style = MaterialTheme.typography.labelSmall)
                        Text(
                            if (uiState.currentSystemState.screenOn) "ON" else "OFF",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (uiState.rules.isEmpty()) {
                Text(
                    text = "No automation rules configured. Add a rule to automate profiles based on battery or thermal events.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                uiState.rules.forEach { rule ->
                    RuleRowItem(
                        rule = rule,
                        onToggle = { isEnabled -> onToggleRule(rule.id, isEnabled) },
                        onDelete = { onDeleteRule(rule.id) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add New Automation Rule")
            }

            AnimatedVisibility(visible = showAddDialog) {
                AddRuleInlineEditor(
                    availableProfiles = uiState.availableProfiles.map { it.id },
                    onDismiss = { showAddDialog = false },
                    onConfirm = { newRule ->
                        onSaveRule(newRule)
                        showAddDialog = false
                    }
                )
            }
        }
    }
}

@Composable
fun RuleRowItem(
    rule: AutomationRule,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = rule.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "P: ${rule.priority}",
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Text(
                text = "When: ${formatTriggerLabel(rule.triggerType, rule.triggerValue)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Then: ${formatActionLabel(rule.actionType, rule.actionValue)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = rule.isEnabled,
                onCheckedChange = onToggle
            )
            Spacer(modifier = Modifier.width(4.dp))
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Text("Del")
            }
        }
    }
}

@Composable
fun AddRuleInlineEditor(
    availableProfiles: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (AutomationRule) -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf("Low Battery Saver") }
    var triggerType by remember { mutableStateOf(RuleTriggerType.BATTERY_PERCENT_BELOW) }
    var triggerValue by remember { mutableStateOf("20") }
    var actionType by remember { mutableStateOf(RuleActionType.APPLY_PROFILE) }
    var actionValue by remember { mutableStateOf(availableProfiles.firstOrNull() ?: "powersave") }
    var priorityStr by remember { mutableStateOf("70") }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Create Automation Rule", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Rule Name") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = triggerValue,
                onValueChange = { triggerValue = it },
                label = { Text("Trigger Value (e.g. 20 for 20%, 45 for 45C)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = actionValue,
                onValueChange = { actionValue = it },
                label = { Text("Action Value (Profile ID, e.g. powersave, balance)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = priorityStr,
                onValueChange = { priorityStr = it },
                label = { Text("Priority (1-100, Higher wins)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

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
                        val prio = priorityStr.toIntOrNull() ?: 50
                        val newRule = AutomationRule(
                            name = name,
                            triggerType = triggerType,
                            triggerValue = triggerValue,
                            actionType = actionType,
                            actionValue = actionValue,
                            priority = prio,
                            isEnabled = true
                        )
                        onConfirm(newRule)
                    }
                ) {
                    Text("Save Rule")
                }
            }
        }
    }
}

private fun formatTriggerLabel(type: RuleTriggerType, value: String): String {
    return when (type) {
        RuleTriggerType.BATTERY_PERCENT_BELOW -> "Battery < $value%"
        RuleTriggerType.BATTERY_PERCENT_ABOVE -> "Battery > $value%"
        RuleTriggerType.TEMPERATURE_ABOVE -> "Temp >= $value C"
        RuleTriggerType.CHARGER_CONNECTED -> "Charger Connected"
        RuleTriggerType.CHARGER_DISCONNECTED -> "Charger Disconnected"
        RuleTriggerType.SCREEN_ON -> "Screen On"
        RuleTriggerType.SCREEN_OFF -> "Screen Off"
        RuleTriggerType.FOREGROUND_APP_OPENED -> "App Opened: $value"
    }
}

private fun formatActionLabel(type: RuleActionType, value: String): String {
    return when (type) {
        RuleActionType.APPLY_PROFILE -> "Set Profile to '$value'"
        RuleActionType.FREEZE_BACKGROUND_APPS -> "Freeze Background Apps"
        RuleActionType.SET_CHARGE_CURRENT_LIMIT -> "Limit Charge Current to $value mA"
        RuleActionType.SET_REFRESH_RATE -> "Set Refresh Rate to $value Hz"
    }
}
