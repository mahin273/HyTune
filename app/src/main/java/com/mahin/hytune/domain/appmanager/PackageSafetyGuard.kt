package com.mahin.hytune.domain.appmanager

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PackageSafetyGuard @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val criticalPackages = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.phone",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.mahin.hytune"
    )

    fun isCritical(packageName: String): Boolean {
        val normalized = packageName.trim().lowercase()
        val ownPackage = context.packageName.lowercase()
        return normalized == ownPackage || criticalPackages.contains(normalized)
    }

    fun isSelf(packageName: String): Boolean {
        return packageName.trim().equals(context.packageName, ignoreCase = true)
    }

    fun validateOperation(packageName: String, isDestructive: Boolean) {
        if (isSelf(packageName) && isDestructive) {
            throw SecurityException("HyTune cannot modify its own package state: $packageName")
        }
        if (isCritical(packageName) && isDestructive) {
            throw SecurityException("Cannot perform destructive operation on critical package: $packageName")
        }
    }
}
