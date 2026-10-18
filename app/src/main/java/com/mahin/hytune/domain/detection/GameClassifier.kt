package com.mahin.hytune.domain.detection

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.mahin.hytune.data.local.db.dao.AppRuleDao
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GameClassifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appRuleDao: AppRuleDao
) {

    suspend fun isGame(packageName: String): Boolean {
        val rule = appRuleDao.getRuleForPackageDirect(packageName)
        if (rule != null && rule.isGame) {
            return true
        }

        return try {
            val pm = context.packageManager
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            isApplicationInfoGame(appInfo)
        } catch (_: Exception) {
            false
        }
    }

    fun isApplicationInfoGame(appInfo: ApplicationInfo): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (appInfo.category == ApplicationInfo.CATEGORY_GAME) {
                return true
            }
        }
        @Suppress("DEPRECATION")
        if ((appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0) {
            return true
        }
        return false
    }
}
