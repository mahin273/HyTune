package com.mahin.hytune.domain.appmanager

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import com.mahin.hytune.domain.appmanager.model.FreezeGroup
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FreezeShortcutManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun canPinShortcuts(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val shortcutManager = context.getSystemService(ShortcutManager::class.java)
            return shortcutManager?.isRequestPinShortcutSupported == true
        }
        return false
    }

    fun requestPinGroupShortcut(group: FreezeGroup): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return false
            if (!shortcutManager.isRequestPinShortcutSupported) return false

            val shortcutId = "freeze_group_${group.id}"
            val launchIntent = Intent(Intent.ACTION_VIEW).apply {
                setPackage(context.packageName)
                putExtra("action", "unfreeze_group")
                putExtra("group_id", group.id)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            val icon = Icon.createWithResource(context, android.R.drawable.sym_def_app_icon)

            val shortcut = ShortcutInfo.Builder(context, shortcutId)
                .setShortLabel(group.name.take(10))
                .setLongLabel("Unfreeze ${group.name}")
                .setIcon(icon)
                .setIntent(launchIntent)
                .build()

            return shortcutManager.requestPinShortcut(shortcut, null)
        }
        return false
    }
}
