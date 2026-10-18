package com.mahin.hytune.domain.detection

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IgnoredPackagesFilter @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val systemUiAndOverlayPackages = setOf(
        "com.android.systemui",
        "com.google.android.inputmethod.latin",
        "com.touchtype.swiftkey",
        "com.samsung.android.honeyboard",
        "com.android.packageinstaller",
        "com.google.android.permissioncontroller",
        "android",
        context.packageName
    )

    private val commonLauncherPackages = setOf(
        "com.google.android.apps.nexuslauncher",
        "com.android.launcher",
        "com.android.launcher3",
        "com.sec.android.app.launcher",
        "com.miui.home",
        "com.oppo.launcher",
        "com.huawei.android.launcher",
        "com.teslacoilsw.launcher",
        "ch.deletescape.lawnchair"
    )

    fun isSystemUiOrOverlay(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return true
        return systemUiAndOverlayPackages.contains(packageName)
    }

    fun isLauncher(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        if (commonLauncherPackages.contains(packageName)) return true
        val homePackage = getHomeLauncherPackage()
        return packageName == homePackage
    }

    fun isIgnored(packageName: String?): Boolean {
        return isSystemUiOrOverlay(packageName)
    }

    private fun getHomeLauncherPackage(): String? {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = try {
            context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        } catch (_: Exception) {
            null
        }
        return resolveInfo?.activityInfo?.packageName
    }
}
