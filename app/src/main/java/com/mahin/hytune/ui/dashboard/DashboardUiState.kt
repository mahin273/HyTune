package com.mahin.hytune.ui.dashboard

import com.mahin.hytune.data.local.db.entity.ProfileEntity
import com.mahin.hytune.domain.probe.model.HardwareSummary
import com.mahin.hytune.domain.sampler.model.SystemSnapshot

data class DashboardUiState(
    val hardwareSummary: HardwareSummary? = null,
    val latestSnapshot: SystemSnapshot? = null,
    val activeProfileId: String = "balance",
    val profiles: List<ProfileEntity> = emptyList(),
    val temperatureInCelsius: Boolean = true,
    val cpuLoadHistory: List<Float> = emptyList(),
    val ramUsageHistory: List<Float> = emptyList(),
    val tempHistory: List<Float> = emptyList(),
    val batteryWattageHistory: List<Float> = emptyList(),
    val isLoading: Boolean = true
) {
    companion object {
        const val MAX_HISTORY_POINTS = 30
    }
}
