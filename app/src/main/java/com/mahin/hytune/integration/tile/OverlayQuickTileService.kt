package com.mahin.hytune.integration.tile

import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.mahin.hytune.domain.overlay.FloatingOverlayManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class OverlayQuickTileService : TileService() {

    @Inject
    lateinit var floatingOverlayManager: FloatingOverlayManager

    private var serviceScope: CoroutineScope? = null

    override fun onCreate() {
        super.onCreate()
        serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope?.cancel()
        serviceScope = null
    }

    override fun onStartListening() {
        super.onStartListening()
        val isShowing = floatingOverlayManager.isShowing.value
        updateTileDisplay(isShowing)
    }

    override fun onClick() {
        super.onClick()
        if (!floatingOverlayManager.canDrawOverlays()) {
            val permissionIntent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+ requires pending intent for startActivityAndCollapse, or standard startActivity
                startActivity(permissionIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(permissionIntent)
            }
            return
        }

        serviceScope?.launch {
            val isNowShowing = floatingOverlayManager.toggleOverlay()
            updateTileDisplay(isNowShowing)
        }
    }

    fun updateTileDisplay(isShowing: Boolean) {
        val tile = try {
            qsTile
        } catch (_: Throwable) {
            null
        } ?: return
        tile.icon = Icon.createWithResource(this, android.R.drawable.ic_menu_view)
        tile.label = "FPS Overlay"

        if (!floatingOverlayManager.canDrawOverlays()) {
            tile.state = Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "Permission Needed"
            }
        } else if (isShowing) {
            tile.state = Tile.STATE_ACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "Active"
            }
        } else {
            tile.state = Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "Off"
            }
        }

        tile.updateTile()
    }
}
