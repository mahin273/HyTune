package com.mahin.hytune.ui.appmanager

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mahin.hytune.domain.appmanager.model.AppOperation
import com.mahin.hytune.domain.appmanager.model.AppSortOrder
import com.mahin.hytune.domain.appmanager.model.AppSummary
import com.mahin.hytune.domain.appmanager.model.AppTypeFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppManagerTab(
    viewModel: AppManagerViewModel = hiltViewModel(),
    freezeGroupViewModel: FreezeGroupViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val freezeGroups by freezeGroupViewModel.groups.collectAsState()
    val operatingGroupId by freezeGroupViewModel.operatingGroupId.collectAsState()
    val groupStatusMessage by freezeGroupViewModel.statusMessage.collectAsState()
    val isCreatingOrEditing by freezeGroupViewModel.isCreatingOrEditing.collectAsState()
    val selectedGroup by freezeGroupViewModel.selectedGroup.collectAsState()

    var sortDropdownExpanded by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (isCreatingOrEditing) {
        FreezeGroupDialog(
            group = selectedGroup,
            onDismiss = { freezeGroupViewModel.dismissDialog() },
            onSave = { id, name, description, autoFreeze, delaySeconds ->
                freezeGroupViewModel.saveGroup(
                    id = id,
                    name = name,
                    description = description,
                    autoFreeze = autoFreeze,
                    delaySeconds = delaySeconds,
                    packages = selectedGroup?.packages ?: emptyList()
                )
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Quick Stats Metric Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            QuickStatBadge("Total", uiState.totalCount, Modifier.weight(1f))
            QuickStatBadge("User", uiState.userAppCount, Modifier.weight(1f))
            QuickStatBadge("System", uiState.systemAppCount, Modifier.weight(1f))
            QuickStatBadge("Disabled", uiState.disabledCount, Modifier.weight(1f))
            QuickStatBadge("Games", uiState.gamesCount, Modifier.weight(1f))
        }

        // Operation Status Banner
        val combinedMessage = uiState.operationMessage ?: groupStatusMessage
        combinedMessage?.let { message ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    TextButton(onClick = {
                        viewModel.clearOperationMessage()
                        freezeGroupViewModel.clearStatusMessage()
                    }) {
                        Text(text = "Dismiss")
                    }
                }
            }
        }

        // Freeze Groups Carousel Section
        FreezeGroupsSection(
            groups = freezeGroups,
            operatingGroupId = operatingGroupId,
            onOpenCreateDialog = { freezeGroupViewModel.openCreateDialog() },
            onOpenEditDialog = { freezeGroupViewModel.openEditDialog(it) },
            onDeleteGroup = { freezeGroupViewModel.deleteGroup(it) },
            onFreezeGroup = { freezeGroupViewModel.freezeGroup(it) },
            onUnfreezeGroup = { freezeGroupViewModel.unfreezeGroup(it) },
            onPinShortcut = { freezeGroupViewModel.pinShortcut(it) }
        )

        // Search Bar
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            label = { Text("Search installed packages") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // Filter and Sort Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val filterScrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(filterScrollState),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AppTypeFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = uiState.filter == filter,
                        onClick = { viewModel.setFilter(filter) },
                        label = { Text(filter.displayName) }
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Sort Dropdown
            ExposedDropdownMenuBox(
                expanded = sortDropdownExpanded,
                onExpandedChange = { sortDropdownExpanded = !sortDropdownExpanded }
            ) {
                Surface(
                    modifier = Modifier.menuAnchor(),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = "Sort",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }

                ExposedDropdownMenu(
                    expanded = sortDropdownExpanded,
                    onDismissRequest = { sortDropdownExpanded = false }
                ) {
                    AppSortOrder.entries.forEach { sort ->
                        DropdownMenuItem(
                            text = { Text(sort.displayName) },
                            onClick = {
                                viewModel.setSortOrder(sort)
                                sortDropdownExpanded = false
                            }
                        )
                    }
                }
            }
        }

        if (uiState.isLoading && uiState.apps.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Querying packages...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (uiState.apps.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "No applications match your filter.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.apps, key = { it.packageName }) { app ->
                    AppInspectionItemCard(
                        app = app,
                        onClick = { viewModel.selectAppForInspection(app.packageName) },
                        onQuickToggleFreeze = {
                            val op = if (app.isEnabled) AppOperation.FREEZE else AppOperation.UNFREEZE
                            viewModel.executeOperation(app.packageName, op)
                        },
                        isOperating = uiState.operationInProgress == app.packageName
                    )
                }
            }
        }
    }

    // Modal Bottom Sheet Inspection
    uiState.selectedAppDetails?.let { details ->
        AppInspectionBottomSheet(
            details = details,
            sheetState = sheetState,
            onDismiss = { viewModel.clearSelectedApp() },
            onExecuteOperation = { operation, keepData ->
                viewModel.executeOperation(
                    packageName = details.summary.packageName,
                    operation = operation,
                    keepData = keepData
                )
            },
            isOperationRunning = uiState.operationInProgress == details.summary.packageName
        )
    }
}

@Composable
private fun QuickStatBadge(
    label: String,
    count: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun AppInspectionItemCard(
    app: AppSummary,
    onClick: () -> Unit,
    onQuickToggleFreeze: (() -> Unit)? = null,
    isOperating: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (!app.isEnabled || app.isSuspended) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = app.appLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (app.isGame) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = "Game",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "v${app.versionName.ifEmpty { "1.0" }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = "SDK ${app.targetSdk}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                val typeText = when {
                    app.isUpdatedSystemApp -> "System (Upd)"
                    app.isSystemApp -> "System"
                    else -> "User"
                }

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (app.isSystemApp) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    }
                ) {
                    Text(
                        text = typeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (app.isSystemApp) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                if (!app.isEnabled || app.isSuspended) {
                    val statusText = if (app.isSuspended) "Suspended" else "Disabled"
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                if (onQuickToggleFreeze != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    if (isOperating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        FilledTonalButton(
                            onClick = onQuickToggleFreeze,
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = if (app.isEnabled) "Freeze" else "Unfreeze",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}
