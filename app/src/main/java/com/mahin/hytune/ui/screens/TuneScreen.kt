package com.mahin.hytune.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mahin.hytune.ui.components.CpuClusterCard
import com.mahin.hytune.ui.components.CpuCoreGrid
import com.mahin.hytune.ui.cpu.CpuViewModel

@Composable
fun TuneScreen(
    modifier: Modifier = Modifier,
    viewModel: CpuViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.userMessage) {
        val msg = uiState.userMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.userMessageShown()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (uiState.isLoading && uiState.clusterPolicies.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (uiState.coreStates.isNotEmpty()) {
                    CpuCoreGrid(
                        coreStates = uiState.coreStates,
                        liveFrequenciesKHz = uiState.liveCoreFrequencies,
                        onCoreToggled = { coreId, online ->
                            viewModel.onCoreOnlineToggled(coreId, online)
                        }
                    )
                }

                uiState.clusterPolicies.forEach { policy ->
                    val stagedGov = uiState.stagedGovernors[policy.clusterId] ?: policy.currentGovernor
                    val stagedMin = uiState.stagedMinFreqs[policy.clusterId] ?: policy.minFreqKHz
                    val stagedMax = uiState.stagedMaxFreqs[policy.clusterId] ?: policy.maxFreqKHz
                    val firstCore = policy.affectedCores.firstOrNull() ?: 0
                    val liveFreq = uiState.liveCoreFrequencies[firstCore] ?: policy.curFreqKHz
                    val isDirty = uiState.isDirty(policy.clusterId)

                    CpuClusterCard(
                        policy = policy,
                        stagedGovernor = stagedGov,
                        stagedMinFreqKHz = stagedMin,
                        stagedMaxFreqKHz = stagedMax,
                        liveFreqKHz = liveFreq,
                        isDirty = isDirty,
                        isApplying = uiState.isApplying,
                        onGovernorChanged = { newGov ->
                            viewModel.onGovernorChanged(policy.clusterId, newGov)
                        },
                        onMinFreqChanged = { newMin ->
                            viewModel.onMinFreqChanged(policy.clusterId, newMin)
                        },
                        onMaxFreqChanged = { newMax ->
                            viewModel.onMaxFreqChanged(policy.clusterId, newMax)
                        },
                        onApplyClicked = {
                            viewModel.applyClusterSettings(policy.clusterId)
                        },
                        onResetClicked = {
                            viewModel.resetClusterSettings(policy.clusterId)
                        }
                    )
                }
            }
        }
    }
}

