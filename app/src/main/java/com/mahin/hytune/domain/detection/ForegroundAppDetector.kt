package com.mahin.hytune.domain.detection

import com.mahin.hytune.core.privilege.PrivilegeManager
import com.mahin.hytune.core.privilege.PrivilegeTier
import com.mahin.hytune.domain.detection.model.ForegroundInfo
import com.mahin.hytune.domain.detection.strategy.CgroupForegroundStrategy
import com.mahin.hytune.domain.detection.strategy.DumpsysForegroundStrategy
import com.mahin.hytune.domain.detection.strategy.UsageStatsForegroundStrategy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ForegroundAppDetector @Inject constructor(
    private val privilegeManager: PrivilegeManager,
    private val cgroupStrategy: CgroupForegroundStrategy,
    private val dumpsysStrategy: DumpsysForegroundStrategy,
    private val usageStatsStrategy: UsageStatsForegroundStrategy,
    private val ignoredPackagesFilter: IgnoredPackagesFilter,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val _currentForegroundApp = MutableStateFlow<ForegroundInfo?>(null)
    val currentForegroundApp: StateFlow<ForegroundInfo?> = _currentForegroundApp.asStateFlow()

    suspend fun detectOnce(): ForegroundInfo? = withContext(ioDispatcher) {
        val tier = privilegeManager.activeBackend.value.tier

        val rawInfo = when (tier) {
            PrivilegeTier.ROOT -> {
                cgroupStrategy.detect()
                    ?: dumpsysStrategy.detect()
                    ?: usageStatsStrategy.detect()
            }
            PrivilegeTier.SHIZUKU -> {
                dumpsysStrategy.detect()
                    ?: usageStatsStrategy.detect()
            }
            PrivilegeTier.NONE -> {
                usageStatsStrategy.detect()
            }
        } ?: return@withContext _currentForegroundApp.value

        // Ignore transient system overlays, notifications, and keyboards
        if (ignoredPackagesFilter.isSystemUiOrOverlay(rawInfo.packageName)) {
            return@withContext _currentForegroundApp.value
        }

        val isLauncher = ignoredPackagesFilter.isLauncher(rawInfo.packageName)
        val finalizedInfo = rawInfo.copy(isHomeLauncher = isLauncher)

        _currentForegroundApp.value = finalizedInfo
        finalizedInfo
    }

    @OptIn(FlowPreview::class)
    fun startDetectionStream(
        pollingIntervalMs: Long = 500L,
        debounceMs: Long = 500L
    ): Flow<ForegroundInfo> = flow {
        while (true) {
            val detected = detectOnce()
            if (detected != null) {
                emit(detected)
            }
            delay(pollingIntervalMs)
        }
    }
        .distinctUntilChangedBy { it.packageName }
        .debounce(debounceMs)
        .flowOn(ioDispatcher)
}
