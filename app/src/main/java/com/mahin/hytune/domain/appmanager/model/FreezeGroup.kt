package com.mahin.hytune.domain.appmanager.model

data class FreezeGroup(
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val autoFreezeOnScreenOff: Boolean = false,
    val autoFreezeDelaySeconds: Int = 30,
    val packages: List<String> = emptyList(),
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

data class GroupOperationResult(
    val groupId: Long,
    val groupName: String,
    val operation: AppOperation,
    val totalPackages: Int,
    val successCount: Int,
    val failureCount: Int,
    val failures: Map<String, String> = emptyMap()
)
