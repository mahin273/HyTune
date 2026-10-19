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
import com.mahin.hytune.ui.components.GpuCard
import com.mahin.hytune.ui.components.MemoryCard
import com.mahin.hytune.ui.components.StorageCard
import com.mahin.hytune.ui.cpu.CpuViewModel
import com.mahin.hytune.ui.gpu.GpuViewModel
import com.mahin.hytune.ui.memory.MemoryViewModel
import com.mahin.hytune.ui.storage.StorageViewModel

@Composable
fun TuneScreen(
    modifier: Modifier = Modifier,
    cpuViewModel: CpuViewModel = hiltViewModel(),
    gpuViewModel: GpuViewModel = hiltViewModel(),
    memoryViewModel: MemoryViewModel = hiltViewModel(),
    storageViewModel: StorageViewModel = hiltViewModel(),
    displayViewModel: com.mahin.hytune.ui.display.DisplayViewModel = hiltViewModel()
) {
    val cpuUiState by cpuViewModel.uiState.collectAsStateWithLifecycle()
    val gpuUiState by gpuViewModel.uiState.collectAsStateWithLifecycle()
    val memoryUiState by memoryViewModel.uiState.collectAsStateWithLifecycle()
    val storageUiState by storageViewModel.uiState.collectAsStateWithLifecycle()
    val displayUiState by displayViewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(cpuUiState.userMessage) {
        val msg = cpuUiState.userMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            cpuViewModel.userMessageShown()
        }
    }

    LaunchedEffect(gpuUiState.userMessage) {
        val msg = gpuUiState.userMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            gpuViewModel.userMessageShown()
        }
    }

    LaunchedEffect(memoryUiState.userMessage) {
        val msg = memoryUiState.userMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            memoryViewModel.clearUserMessage()
        }
    }

    LaunchedEffect(memoryUiState.errorMessage) {
        val err = memoryUiState.errorMessage
        if (err != null) {
            snackbarHostState.showSnackbar(err)
            memoryViewModel.clearErrorMessage()
        }
    }

    LaunchedEffect(storageUiState.userMessage) {
        val msg = storageUiState.userMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            storageViewModel.clearUserMessage()
        }
    }

    LaunchedEffect(storageUiState.errorMessage) {
        val err = storageUiState.errorMessage
        if (err != null) {
            snackbarHostState.showSnackbar(err)
            storageViewModel.clearErrorMessage()
        }
    }

    LaunchedEffect(displayUiState.userMessage) {
        val msg = displayUiState.userMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            displayViewModel.dismissUserMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (cpuUiState.isLoading && cpuUiState.clusterPolicies.isEmpty()) {
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
                if (cpuUiState.coreStates.isNotEmpty()) {
                    CpuCoreGrid(
                        coreStates = cpuUiState.coreStates,
                        liveFrequenciesKHz = cpuUiState.liveCoreFrequencies,
                        onCoreToggled = { coreId, online ->
                            cpuViewModel.onCoreOnlineToggled(coreId, online)
                        }
                    )
                }

                uiStateCpuPolicies(
                    cpuUiState = cpuUiState,
                    cpuViewModel = cpuViewModel
                )

                GpuCard(
                    policy = gpuUiState.policy,
                    stagedGovernor = gpuUiState.stagedGovernor,
                    stagedMinFreqKHz = gpuUiState.stagedMinFreqKHz,
                    stagedMaxFreqKHz = gpuUiState.stagedMaxFreqKHz,
                    liveFreqKHz = gpuUiState.liveFreqKHz,
                    isDirty = gpuUiState.isDirty(),
                    isApplying = gpuUiState.isApplying,
                    onGovernorChanged = { gpuViewModel.onGovernorChanged(it) },
                    onMinFreqChanged = { gpuViewModel.onMinFreqChanged(it) },
                    onMaxFreqChanged = { gpuViewModel.onMaxFreqChanged(it) },
                    onApplyClicked = { gpuViewModel.applyGpuSettings() },
                    onResetClicked = { gpuViewModel.resetGpuSettings() }
                )

                MemoryCard(
                    vmTunables = memoryUiState.vmTunables,
                    zramInfo = memoryUiState.zramInfo,
                    stagedSwappiness = memoryUiState.stagedSwappiness,
                    stagedVfsCachePressure = memoryUiState.stagedVfsCachePressure,
                    stagedZramSizeBytes = memoryUiState.stagedZramSizeBytes,
                    stagedAlgorithm = memoryUiState.stagedAlgorithm,
                    isDirty = memoryUiState.isDirty,
                    isApplying = memoryUiState.isApplying,
                    isTrimming = memoryUiState.isTrimming,
                    isCompacting = memoryUiState.isCompacting,
                    onSwappinessChanged = { memoryViewModel.onSwappinessChanged(it) },
                    onVfsCachePressureChanged = { memoryViewModel.onVfsCachePressureChanged(it) },
                    onZramSizeChanged = { memoryViewModel.onZramSizeChanged(it) },
                    onAlgorithmChanged = { memoryViewModel.onAlgorithmChanged(it) },
                    onTrimCachesClicked = { memoryViewModel.trimCaches(it) },
                    onCompactZramClicked = { memoryViewModel.compactZram() },
                    onApplyClicked = { memoryViewModel.applyMemorySettings() }
                )

                StorageCard(
                    blockDevices = storageUiState.blockDevices,
                    stagedSchedulers = storageUiState.stagedSchedulers,
                    stagedReadAhead = storageUiState.stagedReadAhead,
                    fstrimResult = storageUiState.fstrimResult,
                    isDirty = { storageUiState.isDirty(it) },
                    isApplying = storageUiState.isApplying,
                    isTrimming = storageUiState.isTrimming,
                    onSchedulerChanged = { dev, sched -> storageViewModel.onSchedulerChanged(dev, sched) },
                    onReadAheadChanged = { dev, kb -> storageViewModel.onReadAheadChanged(dev, kb) },
                    onApplyClicked = { dev -> storageViewModel.applyStorageSettings(dev) },
                    onTrimClicked = { storageViewModel.runTrim("/data") }
                )

                com.mahin.hytune.ui.components.DisplayCard(
                    uiState = displayUiState,
                    onApplyResolution = { w, h, autoDpi -> displayViewModel.applyResolutionWithCountdown(w, h, autoDpi) },
                    onApplyDensity = { dpi -> displayViewModel.applyDensityWithCountdown(dpi) },
                    onApplyRefreshRate = { minHz, peakHz -> displayViewModel.applyRefreshRate(minHz, peakHz) },
                    onResetRefreshRate = { displayViewModel.resetRefreshRate() },
                    onConfirmChanges = { displayViewModel.confirmChanges() },
                    onRevertChanges = { displayViewModel.revertChangesNow() },
                    onResetAllDefaults = { displayViewModel.resetAllDefaults() }
                )
            }
        }
    }
}

@Composable
private fun uiStateCpuPolicies(
    cpuUiState: com.mahin.hytune.ui.cpu.CpuUiState,
    cpuViewModel: CpuViewModel
) {
    cpuUiState.clusterPolicies.forEach { policy ->
        val stagedGov = cpuUiState.stagedGovernors[policy.clusterId] ?: policy.currentGovernor
        val stagedMin = cpuUiState.stagedMinFreqs[policy.clusterId] ?: policy.minFreqKHz
        val stagedMax = cpuUiState.stagedMaxFreqs[policy.clusterId] ?: policy.maxFreqKHz
        val firstCore = policy.affectedCores.firstOrNull() ?: 0
        val liveFreq = cpuUiState.liveCoreFrequencies[firstCore] ?: policy.curFreqKHz
        val isDirty = cpuUiState.isDirty(policy.clusterId)

        CpuClusterCard(
            policy = policy,
            stagedGovernor = stagedGov,
            stagedMinFreqKHz = stagedMin,
            stagedMaxFreqKHz = stagedMax,
            liveFreqKHz = liveFreq,
            isDirty = isDirty,
            isApplying = cpuUiState.isApplying,
            onGovernorChanged = { newGov ->
                cpuViewModel.onGovernorChanged(policy.clusterId, newGov)
            },
            onMinFreqChanged = { newMin ->
                cpuViewModel.onMinFreqChanged(policy.clusterId, newMin)
            },
            onMaxFreqChanged = { newMax ->
                cpuViewModel.onMaxFreqChanged(policy.clusterId, newMax)
            },
            onApplyClicked = {
                cpuViewModel.applyClusterSettings(policy.clusterId)
            },
            onResetClicked = {
                cpuViewModel.resetClusterSettings(policy.clusterId)
            }
        )
    }
}


