package com.mahin.hytune.domain.appmanager.model

data class AppDetails(
    val summary: AppSummary,
    val sourceDir: String,
    val dataDir: String,
    val installerPackage: String?,
    val permissions: List<PermissionItem>,
    val componentCounts: ComponentCounts
)

data class PermissionItem(
    val name: String,
    val isGranted: Boolean
)

data class ComponentCounts(
    val activityCount: Int,
    val serviceCount: Int,
    val receiverCount: Int,
    val providerCount: Int
) {
    val totalComponents: Int
        get() = activityCount + serviceCount + receiverCount + providerCount
}
