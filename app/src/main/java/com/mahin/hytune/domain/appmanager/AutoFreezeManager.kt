package com.mahin.hytune.domain.appmanager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoFreezeManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: FreezeGroupRepository,
    private val operationEngine: AppOperationEngine,
    private val safetyGuard: PackageSafetyGuard,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    var testScopeOverride: CoroutineScope? = null
    private val defaultScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val scope: CoroutineScope get() = testScopeOverride ?: defaultScope
    private var debounceJob: Job? = null
    private var isReceiverRegistered = false

    private val _isAutoFreezePending = MutableStateFlow(false)
    val isAutoFreezePending: StateFlow<Boolean> = _isAutoFreezePending.asStateFlow()

    private val _lastExecutionSummary = MutableStateFlow<String?>(null)
    val lastExecutionSummary: StateFlow<String?> = _lastExecutionSummary.asStateFlow()

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> handleScreenOff()
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_USER_PRESENT -> handleScreenOn()
            }
        }
    }

    fun startMonitoring() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            context.registerReceiver(screenReceiver, filter)
            isReceiverRegistered = true
        }
    }

    fun stopMonitoring() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(screenReceiver)
            } catch (_: IllegalArgumentException) {
                // Ignore if not registered
            }
            isReceiverRegistered = false
        }
        cancelPendingFreeze()
    }

    fun handleScreenOff(overrideDelaySeconds: Int? = null) {
        cancelPendingFreeze()

        debounceJob = scope.launch {
            val autoGroups = repository.getAutoFreezeGroups()
            if (autoGroups.isEmpty()) {
                _isAutoFreezePending.value = false
                return@launch
            }

            _isAutoFreezePending.value = true

            val delaySeconds = overrideDelaySeconds
                ?: autoGroups.minOfOrNull { it.autoFreezeDelaySeconds }?.coerceAtLeast(0)
                ?: 30

            if (delaySeconds > 0) {
                delay(delaySeconds * 1000L)
            }

            executeAutoFreeze(autoGroups)
            _isAutoFreezePending.value = false
        }
    }

    fun handleScreenOn() {
        cancelPendingFreeze()
    }

    fun cancelPendingFreeze() {
        debounceJob?.cancel()
        debounceJob = null
        _isAutoFreezePending.value = false
    }

    private suspend fun executeAutoFreeze(groups: List<com.mahin.hytune.domain.appmanager.model.FreezeGroup>) {
        val uniquePackages = groups.flatMap { it.packages }.distinct()
        var frozenCount = 0
        var skippedCount = 0

        for (pkg in uniquePackages) {
            if (safetyGuard.isCritical(pkg)) {
                skippedCount++
                continue
            }

            val result = operationEngine.freeze(pkg)
            if (result.isSuccess && result.getOrNull()?.success == true) {
                frozenCount++
            } else {
                skippedCount++
            }
        }

        _lastExecutionSummary.value = "Auto-freeze complete: $frozenCount frozen, $skippedCount skipped"
    }
}
