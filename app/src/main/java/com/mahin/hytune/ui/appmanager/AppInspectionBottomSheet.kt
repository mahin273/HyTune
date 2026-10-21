package com.mahin.hytune.ui.appmanager

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mahin.hytune.domain.appmanager.model.AppDetails
import com.mahin.hytune.domain.appmanager.model.AppOperation
import com.mahin.hytune.domain.appmanager.model.FreezeGroup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppInspectionBottomSheet(
    details: AppDetails,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    freezeGroups: List<FreezeGroup> = emptyList(),
    onTogglePackageInGroup: (groupId: Long, packageName: String, inGroup: Boolean) -> Unit = { _, _, _ -> },
    onExecuteOperation: (AppOperation, Boolean) -> Unit = { _, _ -> },
    isOperationRunning: Boolean = false,
    modifier: Modifier = Modifier
) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    var pendingConfirmationOperation by remember { mutableStateOf<AppOperation?>(null) }

    if (pendingConfirmationOperation != null) {
        val op = pendingConfirmationOperation!!
        AlertDialog(
            onDismissRequest = { pendingConfirmationOperation = null },
            title = { Text(text = "Confirm ${op.displayName}") },
            text = {
                val warning = if (op == AppOperation.CLEAR_DATA) {
                    "This will permanently erase all data, caches, and login states for ${details.summary.appLabel}."
                } else if (op == AppOperation.UNINSTALL_USER_0) {
                    "This will remove ${details.summary.appLabel} for user 0. System APKs will remain in system partition."
                } else {
                    "Are you sure you want to proceed with ${op.displayName} on ${details.summary.appLabel}?"
                }
                Text(text = warning)
            },
            confirmButton = {
                Button(
                    onClick = {
                        val operationToRun = op
                        pendingConfirmationOperation = null
                        onExecuteOperation(operationToRun, false)
                    },
                    colors = if (op.isDestructive) {
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    } else {
                        ButtonDefaults.buttonColors()
                    }
                ) {
                    Text(text = "Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirmationOperation = null }) {
                    Text(text = "Cancel")
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = details.summary.appLabel,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = details.summary.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (details.summary.isSystemApp) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    }
                ) {
                    Text(
                        text = if (details.summary.isSystemApp) "System" else "User",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (details.summary.isSystemApp) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Section 0: Quick Operations Bar
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Package Operations",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (isOperationRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (details.summary.isEnabled) {
                            FilledTonalButton(
                                onClick = { onExecuteOperation(AppOperation.FREEZE, false) },
                                enabled = !isOperationRunning,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Freeze")
                            }

                            OutlinedButton(
                                onClick = { onExecuteOperation(AppOperation.SUSPEND, false) },
                                enabled = !isOperationRunning,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Suspend")
                            }
                        } else {
                            Button(
                                onClick = { onExecuteOperation(AppOperation.UNFREEZE, false) },
                                enabled = !isOperationRunning,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Unfreeze")
                            }
                        }

                        if (details.summary.isSuspended) {
                            Button(
                                onClick = { onExecuteOperation(AppOperation.UNSUSPEND, false) },
                                enabled = !isOperationRunning,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Unsuspend")
                            }
                        }

                        OutlinedButton(
                            onClick = { onExecuteOperation(AppOperation.FORCE_STOP, false) },
                            enabled = !isOperationRunning,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Force Stop")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { pendingConfirmationOperation = AppOperation.CLEAR_DATA },
                            enabled = !isOperationRunning,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Clear Data")
                        }

                        OutlinedButton(
                            onClick = { pendingConfirmationOperation = AppOperation.UNINSTALL_USER_0 },
                            enabled = !isOperationRunning,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Uninstall (User 0)")
                        }

                        if (details.summary.isSystemApp) {
                            OutlinedButton(
                                onClick = { onExecuteOperation(AppOperation.REINSTALL_EXISTING, false) },
                                enabled = !isOperationRunning,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Reinstall")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Freeze Groups Membership
                if (freezeGroups.isNotEmpty()) {
                    item {
                        Text(
                            text = "Freeze Groups Membership",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                freezeGroups.forEach { group ->
                                    val isInGroup = group.packages.contains(details.summary.packageName)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onTogglePackageInGroup(group.id, details.summary.packageName, !isInGroup)
                                            },
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = group.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = "${group.packages.size} apps in group",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Checkbox(
                                            checked = isInGroup,
                                            onCheckedChange = { checked ->
                                                onTogglePackageInGroup(group.id, details.summary.packageName, checked)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Section 1: Version & SDK Targets
                item {
                    Text(
                        text = "Version & Identity",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            InfoRow(label = "Version Name", value = details.summary.versionName.ifEmpty { "N/A" })
                            InfoRow(label = "Version Code", value = details.summary.versionCode.toString())
                            InfoRow(label = "Target SDK", value = "API ${details.summary.targetSdk}")
                            InfoRow(label = "Min SDK", value = "API ${details.summary.minSdk}")
                            InfoRow(label = "UID", value = details.summary.uid.toString())
                            details.installerPackage?.let {
                                InfoRow(label = "Installer", value = it)
                            }
                        }
                    }
                }

                // Section 2: Storage & Filesystem Paths
                item {
                    Text(
                        text = "Filesystem & Installation",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            InfoRow(label = "APK Path", value = details.sourceDir)
                            InfoRow(label = "Data Path", value = details.dataDir)
                            InfoRow(
                                label = "Installed",
                                value = if (details.summary.installTimeEpochMs > 0) {
                                    dateFormat.format(Date(details.summary.installTimeEpochMs))
                                } else "N/A"
                            )
                            InfoRow(
                                label = "Updated",
                                value = if (details.summary.updateTimeEpochMs > 0) {
                                    dateFormat.format(Date(details.summary.updateTimeEpochMs))
                                } else "N/A"
                            )
                        }
                    }
                }

                // Section 3: Declared Components Breakdown
                item {
                    Text(
                        text = "Declared Components (${details.componentCounts.totalComponents})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ComponentBadge(title = "Activities", count = details.componentCounts.activityCount, modifier = Modifier.weight(1f))
                        ComponentBadge(title = "Services", count = details.componentCounts.serviceCount, modifier = Modifier.weight(1f))
                        ComponentBadge(title = "Receivers", count = details.componentCounts.receiverCount, modifier = Modifier.weight(1f))
                        ComponentBadge(title = "Providers", count = details.componentCounts.providerCount, modifier = Modifier.weight(1f))
                    }
                }

                // Section 4: Permissions Audit
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Requested Permissions (${details.permissions.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        val grantedCount = details.permissions.count { it.isGranted }
                        Text(
                            text = "$grantedCount Granted",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (details.permissions.isEmpty()) {
                    item {
                        Text(
                            text = "No permissions requested",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(details.permissions) { perm ->
                        PermissionItemRow(perm = perm)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ComponentBadge(
    title: String,
    count: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PermissionItemRow(
    perm: com.mahin.hytune.domain.appmanager.model.PermissionItem
) {
    val simpleName = perm.name.substringAfterLast('.')
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (perm.isGranted) {
                MaterialTheme.colorScheme.surfaceContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = simpleName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = perm.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = if (perm.isGranted) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                }
            ) {
                Text(
                    text = if (perm.isGranted) "Granted" else "Denied",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (perm.isGranted) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    },
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
