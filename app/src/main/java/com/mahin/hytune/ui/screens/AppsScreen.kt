package com.mahin.hytune.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.mahin.hytune.ui.appmanager.AppManagerTab
import com.mahin.hytune.ui.components.AppIconImage
import com.mahin.hytune.ui.model.AppItemUiModel
import com.mahin.hytune.ui.viewmodel.AppFilterType
import com.mahin.hytune.ui.viewmodel.PerAppRulesViewModel

@Composable
fun AppsScreen(
    modifier: Modifier = Modifier
) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("App Manager", "Per-App Profiles", "Processes")

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        when (selectedTabIndex) {
            0 -> AppManagerTab()
            1 -> PerAppProfilesTab()
            2 -> com.mahin.hytune.ui.process.ProcessManagerTab()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerAppProfilesTab(
    viewModel: PerAppRulesViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedAppForEdit by remember { mutableStateOf<AppItemUiModel?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Automated Per-App Profiles",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Switch profiles, lock refresh rate, and toggle DND when apps launch.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = uiState.perAppProfilesEnabled,
                    onCheckedChange = { viewModel.togglePerAppProfilesEnabled(it) }
                )
            }
        }

        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            label = { Text("Search apps or packages") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = uiState.filterType == AppFilterType.ALL,
                onClick = { viewModel.setFilterType(AppFilterType.ALL) },
                label = { Text("All") }
            )
            FilterChip(
                selected = uiState.filterType == AppFilterType.GAMES,
                onClick = { viewModel.setFilterType(AppFilterType.GAMES) },
                label = { Text("Games") }
            )
            FilterChip(
                selected = uiState.filterType == AppFilterType.CONFIGURED,
                onClick = { viewModel.setFilterType(AppFilterType.CONFIGURED) },
                label = { Text("Configured") }
            )
        }

        if (uiState.isLoading && uiState.apps.isEmpty()) {
            Text(
                text = "Scanning installed applications...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        } else if (uiState.apps.isEmpty()) {
            Text(
                text = "No applications match the filter.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.apps, key = { it.packageName }) { app ->
                    AppRowCard(
                        app = app,
                        onClick = { selectedAppForEdit = app }
                    )
                }
            }
        }
    }

    selectedAppForEdit?.let { app ->
        AppRuleConfigDialog(
            app = app,
            availableProfiles = uiState.availableProfiles.map { it.id },
            onDismiss = { selectedAppForEdit = null },
            onSave = { profileId, isGame, refreshRate, dnd ->
                viewModel.saveAppRule(
                    packageName = app.packageName,
                    profileId = profileId,
                    isGame = isGame,
                    targetRefreshRate = refreshRate,
                    dndEnabled = dnd
                )
                selectedAppForEdit = null
            },
            onDelete = {
                viewModel.removeAppRule(app.packageName)
                selectedAppForEdit = null
            }
        )
    }
}

@Composable
fun AppRowCard(
    app: AppItemUiModel,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (app.configuredProfileId != null) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconImage(
                packageName = app.packageName,
                modifier = Modifier.size(42.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
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
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
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
            }

            Column(horizontalAlignment = Alignment.End) {
                val profileText = app.configuredProfileId?.replaceFirstChar { it.uppercase() } ?: "Default"
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (app.configuredProfileId != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(
                        text = profileText,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (app.configuredProfileId != null) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                if (app.targetRefreshRate != null || app.dndEnabled) {
                    val extras = buildList {
                        if (app.targetRefreshRate != null) add("${app.targetRefreshRate}Hz")
                        if (app.dndEnabled) add("DND")
                    }.joinToString(" | ")

                    Text(
                        text = extras,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRuleConfigDialog(
    app: AppItemUiModel,
    availableProfiles: List<String>,
    onDismiss: () -> Unit,
    onSave: (profileId: String, isGame: Boolean, refreshRate: Int?, dnd: Boolean) -> Unit,
    onDelete: () -> Unit
) {
    var selectedProfile by remember { mutableStateOf(app.configuredProfileId ?: "performance") }
    var isGame by remember { mutableStateOf(app.isGame) }
    var selectedRefreshRate by remember { mutableStateOf(app.targetRefreshRate) }
    var dndEnabled by remember { mutableStateOf(app.dndEnabled) }

    var profileDropdownExpanded by remember { mutableStateOf(false) }
    var refreshRateDropdownExpanded by remember { mutableStateOf(false) }

    val refreshRateOptions = listOf(null, 60, 90, 120, 144)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = app.appLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Profile Dropdown
                ExposedDropdownMenuBox(
                    expanded = profileDropdownExpanded,
                    onExpandedChange = { profileDropdownExpanded = !profileDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedProfile.replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Performance Profile") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = profileDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = profileDropdownExpanded,
                        onDismissRequest = { profileDropdownExpanded = false }
                    ) {
                        availableProfiles.forEach { profile ->
                            DropdownMenuItem(
                                text = { Text(profile.replaceFirstChar { it.uppercase() }) },
                                onClick = {
                                    selectedProfile = profile
                                    profileDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Refresh Rate Dropdown
                ExposedDropdownMenuBox(
                    expanded = refreshRateDropdownExpanded,
                    onExpandedChange = { refreshRateDropdownExpanded = !refreshRateDropdownExpanded }
                ) {
                    val rateLabel = if (selectedRefreshRate == null) "Default (No change)" else "${selectedRefreshRate} Hz"
                    OutlinedTextField(
                        value = rateLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Display Refresh Rate") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = refreshRateDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = refreshRateDropdownExpanded,
                        onDismissRequest = { refreshRateDropdownExpanded = false }
                    ) {
                        refreshRateOptions.forEach { rate ->
                            val text = if (rate == null) "Default (No change)" else "$rate Hz"
                            DropdownMenuItem(
                                text = { Text(text) },
                                onClick = {
                                    selectedRefreshRate = rate
                                    refreshRateDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // DND Checkbox
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = dndEnabled,
                        onCheckedChange = { dndEnabled = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Enable Do Not Disturb while in app",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                // Game override checkbox
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = isGame,
                        onCheckedChange = { isGame = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Mark as Game",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(selectedProfile, isGame, selectedRefreshRate, dndEnabled)
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (app.configuredProfileId != null) {
                    OutlinedButton(onClick = onDelete) {
                        Text("Reset")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
