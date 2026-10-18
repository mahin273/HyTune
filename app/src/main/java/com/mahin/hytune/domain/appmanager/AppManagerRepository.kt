package com.mahin.hytune.domain.appmanager

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.mahin.hytune.domain.appmanager.model.AppDetails
import com.mahin.hytune.domain.appmanager.model.AppSummary
import com.mahin.hytune.domain.appmanager.model.ComponentCounts
import com.mahin.hytune.domain.appmanager.model.PermissionItem
import com.mahin.hytune.domain.detection.GameClassifier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppManagerRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameClassifier: GameClassifier,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private var cachedApps: List<AppSummary>? = null

    fun getInstalledApps(forceRefresh: Boolean = false): Flow<List<AppSummary>> = flow {
        if (!forceRefresh && cachedApps != null) {
            emit(cachedApps!!)
            return@flow
        }

        val apps = queryInstalledAppsInternal()
        cachedApps = apps
        emit(apps)
    }.flowOn(ioDispatcher)

    suspend fun getAppDetails(packageName: String): Result<AppDetails> = withContext(ioDispatcher) {
        runCatching {
            val pm = context.packageManager
            val flags = PackageManager.GET_PERMISSIONS or
                    PackageManager.GET_ACTIVITIES or
                    PackageManager.GET_SERVICES or
                    PackageManager.GET_RECEIVERS or
                    PackageManager.GET_PROVIDERS

            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, flags)
            }

            val appInfo = packageInfo.applicationInfo
                ?: throw NoSuchElementException("ApplicationInfo missing for $packageName")

            val summary = cachedApps?.find { it.packageName == packageName }
                ?: mapToSummary(pm, packageInfo, appInfo)

            val permissionsList = mutableListOf<PermissionItem>()
            val requested = packageInfo.requestedPermissions
            val flagsArray = packageInfo.requestedPermissionsFlags
            if (requested != null) {
                for (i in requested.indices) {
                    val perm = requested[i]
                    val isGranted = if (flagsArray != null && i < flagsArray.size) {
                        (flagsArray[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                    } else {
                        false
                    }
                    permissionsList.add(PermissionItem(perm, isGranted))
                }
            }

            val counts = ComponentCounts(
                activityCount = packageInfo.activities?.size ?: 0,
                serviceCount = packageInfo.services?.size ?: 0,
                receiverCount = packageInfo.receivers?.size ?: 0,
                providerCount = packageInfo.providers?.size ?: 0
            )

            val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    pm.getInstallSourceInfo(packageName).installingPackageName
                } catch (_: Exception) {
                    null
                }
            } else {
                @Suppress("DEPRECATION")
                try {
                    pm.getInstallerPackageName(packageName)
                } catch (_: Exception) {
                    null
                }
            }

            AppDetails(
                summary = summary,
                sourceDir = appInfo.sourceDir ?: "",
                dataDir = appInfo.dataDir ?: "",
                installerPackage = installer,
                permissions = permissionsList,
                componentCounts = counts
            )
        }
    }

    private fun queryInstalledAppsInternal(): List<AppSummary> {
        val pm = context.packageManager
        val packages = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(0)
            }
        } catch (_: Exception) {
            emptyList<PackageInfo>()
        }

        return packages.mapNotNull { pkgInfo ->
            val appInfo = pkgInfo.applicationInfo ?: return@mapNotNull null
            mapToSummary(pm, pkgInfo, appInfo)
        }.sortedBy { it.appLabel.lowercase() }
    }

    private fun mapToSummary(
        pm: PackageManager,
        pkgInfo: PackageInfo,
        appInfo: ApplicationInfo
    ): AppSummary {
        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        val isEnabled = appInfo.enabled
        val isSuspended = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                pm.isPackageSuspended(pkgInfo.packageName)
            } catch (_: Exception) {
                false
            }
        } else {
            false
        }
        val isGame = gameClassifier.isApplicationInfoGame(appInfo)

        val label = try {
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            pkgInfo.packageName
        }

        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pkgInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.versionCode.toLong()
        }

        val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            appInfo.minSdkVersion
        } else {
            0
        }

        return AppSummary(
            packageName = pkgInfo.packageName,
            appLabel = label,
            versionName = pkgInfo.versionName ?: "",
            versionCode = versionCode,
            isSystemApp = isSystem,
            isUpdatedSystemApp = isUpdatedSystem,
            isEnabled = isEnabled,
            isSuspended = isSuspended,
            isGame = isGame,
            installTimeEpochMs = pkgInfo.firstInstallTime,
            updateTimeEpochMs = pkgInfo.lastUpdateTime,
            targetSdk = appInfo.targetSdkVersion,
            minSdk = minSdk,
            uid = appInfo.uid
        )
    }
}
