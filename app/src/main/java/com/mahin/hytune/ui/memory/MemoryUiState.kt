package com.mahin.hytune.ui.memory

import com.mahin.hytune.domain.memory.model.VmTunables
import com.mahin.hytune.domain.memory.model.ZramInfo

data class MemoryUiState(
    val isLoading: Boolean = false,
    val isApplying: Boolean = false,
    val isTrimming: Boolean = false,
    val isCompacting: Boolean = false,
    val vmTunables: VmTunables? = null,
    val zramInfo: ZramInfo? = null,
    val stagedSwappiness: Int? = null,
    val stagedVfsCachePressure: Int? = null,
    val stagedZramSizeBytes: Long? = null,
    val stagedAlgorithm: String? = null,
    val userMessage: String? = null,
    val errorMessage: String? = null
) {
    val isDirty: Boolean
        get() {
            val swappinessDirty = stagedSwappiness != null && stagedSwappiness != vmTunables?.swappiness
            val cachePressureDirty = stagedVfsCachePressure != null && stagedVfsCachePressure != vmTunables?.vfsCachePressure
            val zramSizeDirty = stagedZramSizeBytes != null && stagedZramSizeBytes != zramInfo?.disksizeBytes
            val algorithmDirty = stagedAlgorithm != null && stagedAlgorithm != zramInfo?.activeAlgorithm
            return swappinessDirty || cachePressureDirty || zramSizeDirty || algorithmDirty
        }
}
