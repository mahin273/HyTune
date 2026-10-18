package com.mahin.hytune.ui.process

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mahin.hytune.domain.process.model.ProcessFilter
import com.mahin.hytune.domain.process.model.ProcessSignal
import com.mahin.hytune.domain.process.model.ProcessSortBy

@Composable
fun ProcessManagerTab(
    viewModel: ProcessViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.selectedProcess != null) {
        ProcessDetailBottomSheet(
            process = uiState.selectedProcess!!,
            currentOomAdj = uiState.selectedProcessOomAdj,
            onDismiss = { viewModel.selectProcess(null) },
            onSendSignal = { signal ->
                uiState.selectedProcess?.let { viewModel.sendSignal(it, signal) }
            },
            onSetNice = { nice ->
                uiState.selectedProcess?.let { viewModel.setNice(it, nice) }
            },
            onSetOomScoreAdj = { adj ->
                uiState.selectedProcess?.let { viewModel.setOomScoreAdj(it, adj) }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Status Message Banner
        uiState.statusMessage?.let { message ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    TextButton(onClick = { viewModel.clearStatusMessage() }) {
                        Text(text = "Dismiss")
                    }
                }
            }
        }

        // Search Bar
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = { Text(text = "Search process name, PID, or package") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Filter and Sort Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = uiState.filter == ProcessFilter.ALL,
                onClick = { viewModel.setFilter(ProcessFilter.ALL) },
                label = { Text("All") }
            )
            FilterChip(
                selected = uiState.filter == ProcessFilter.USER_APPS,
                onClick = { viewModel.setFilter(ProcessFilter.USER_APPS) },
                label = { Text("Apps") }
            )
            FilterChip(
                selected = uiState.filter == ProcessFilter.SYSTEM,
                onClick = { viewModel.setFilter(ProcessFilter.SYSTEM) },
                label = { Text("System") }
            )

            Spacer(modifier = Modifier.width(4.dp))

            FilterChip(
                selected = uiState.sortBy == ProcessSortBy.CPU,
                onClick = { viewModel.setSortBy(ProcessSortBy.CPU) },
                label = {
                    val orderSuffix = if (uiState.sortBy == ProcessSortBy.CPU) {
                        if (uiState.sortAscending) " (Asc)" else " (Desc)"
                    } else ""
                    Text("CPU$orderSuffix")
                }
            )
            FilterChip(
                selected = uiState.sortBy == ProcessSortBy.RAM,
                onClick = { viewModel.setSortBy(ProcessSortBy.RAM) },
                label = {
                    val orderSuffix = if (uiState.sortBy == ProcessSortBy.RAM) {
                        if (uiState.sortAscending) " (Asc)" else " (Desc)"
                    } else ""
                    Text("RAM$orderSuffix")
                }
            )
            FilterChip(
                selected = uiState.sortBy == ProcessSortBy.PID,
                onClick = { viewModel.setSortBy(ProcessSortBy.PID) },
                label = {
                    val orderSuffix = if (uiState.sortBy == ProcessSortBy.PID) {
                        if (uiState.sortAscending) " (Asc)" else " (Desc)"
                    } else ""
                    Text("PID$orderSuffix")
                }
            )
            FilterChip(
                selected = uiState.sortBy == ProcessSortBy.NAME,
                onClick = { viewModel.setSortBy(ProcessSortBy.NAME) },
                label = {
                    val orderSuffix = if (uiState.sortBy == ProcessSortBy.NAME) {
                        if (uiState.sortAscending) " (Asc)" else " (Desc)"
                    } else ""
                    Text("Name$orderSuffix")
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Control / Count Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${uiState.filteredProcesses.size} processes",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(
                onClick = { viewModel.refresh() },
                enabled = !uiState.isLoading,
                modifier = Modifier.height(36.dp),
                colors = ButtonDefaults.outlinedButtonColors()
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(text = "Refresh")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Processes List
        if (uiState.filteredProcesses.isEmpty() && !uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No processes found matching your criteria",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = uiState.filteredProcesses,
                    key = { it.pid }
                ) { process ->
                    ProcessCard(
                        process = process,
                        onClick = { viewModel.selectProcess(process) },
                        onKill = { viewModel.sendSignal(process, ProcessSignal.KILL) }
                    )
                }
            }
        }
    }
}
