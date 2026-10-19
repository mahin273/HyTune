package com.mahin.hytune.integration.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mahin.hytune.domain.display.DisplayControlEngine
import com.mahin.hytune.domain.engine.ProfileEngine
import com.mahin.hytune.domain.overlay.FloatingOverlayManager
import com.mahin.hytune.domain.profile.BuiltInProfiles
import com.mahin.hytune.domain.profile.ProfileRepository
import com.mahin.hytune.integration.widget.HyTuneWidgetProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class HyTuneAutomationReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "HyTuneAutomation"

        const val ACTION_APPLY_PROFILE = "com.mahin.hytune.action.APPLY_PROFILE"
        const val ACTION_TOGGLE_OVERLAY = "com.mahin.hytune.action.TOGGLE_OVERLAY"
        const val ACTION_RESET_DISPLAY = "com.mahin.hytune.action.RESET_DISPLAY"

        const val EXTRA_PROFILE_ID = "profile_id"
        const val EXTRA_PROFILE_MODE = "profile_mode"
        const val EXTRA_ENABLE = "enable"
    }

    @Inject
    lateinit var profileRepository: ProfileRepository

    @Inject
    lateinit var profileEngine: ProfileEngine

    @Inject
    lateinit var floatingOverlayManager: FloatingOverlayManager

    @Inject
    lateinit var displayControlEngine: DisplayControlEngine

    var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    fun onReceiveAsync(context: Context, intent: Intent): kotlinx.coroutines.Job? {
        val action = intent.action ?: return null
        val pendingResult = try {
            goAsync()
        } catch (_: Throwable) {
            null
        }

        return CoroutineScope(ioDispatcher).launch {
            try {
                when (action) {
                    ACTION_APPLY_PROFILE -> handleApplyProfile(context, intent)
                    ACTION_TOGGLE_OVERLAY -> handleToggleOverlay(intent)
                    ACTION_RESET_DISPLAY -> handleResetDisplay()
                    else -> Log.w(TAG, "Unhandled broadcast action: $action")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error executing automation action: $action", t)
            } finally {
                pendingResult?.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        onReceiveAsync(context, intent)
    }

    private suspend fun handleApplyProfile(context: Context, intent: Intent) {
        val targetId = intent.getStringExtra(EXTRA_PROFILE_ID)
            ?: intent.getStringExtra(EXTRA_PROFILE_MODE)
            ?: "balance"

        val targetProfile = BuiltInProfiles.findById(targetId)
            ?: profileRepository.getProfileById(targetId).getOrNull()
            ?: BuiltInProfiles.BALANCE

        val result = profileEngine.applyProfile(targetProfile)
        val profileResult = result.getOrNull()
        if (result.isSuccess && profileResult?.isSuccess == true) {
            profileRepository.setActiveProfile(targetProfile.id)
            Log.i(TAG, "Successfully applied profile: ${targetProfile.name}")
            try {
                HyTuneWidgetProvider.updateAllWidgets(context, ioDispatcher)
            } catch (_: Throwable) {
                // Ignore widget update errors in headless or testing environments
            }
        } else {
            val errorMsg = profileResult?.errorMessage ?: result.exceptionOrNull()?.message
            Log.e(TAG, "Failed to apply profile: ${targetProfile.name} - $errorMsg")
        }
    }

    private suspend fun handleToggleOverlay(intent: Intent) {
        if (!floatingOverlayManager.canDrawOverlays()) {
            Log.w(TAG, "Cannot toggle overlay: SYSTEM_ALERT_WINDOW permission not granted")
            return
        }

        if (intent.hasExtra(EXTRA_ENABLE)) {
            val enable = intent.getBooleanExtra(EXTRA_ENABLE, false)
            if (enable) {
                floatingOverlayManager.showOverlay()
            } else {
                floatingOverlayManager.hideOverlay()
            }
        } else {
            floatingOverlayManager.toggleOverlay()
        }
    }

    private suspend fun handleResetDisplay() {
        displayControlEngine.resetResolution()
        displayControlEngine.resetDensity()
        displayControlEngine.resetRefreshRate()
        Log.i(TAG, "Reset display resolution, density, and refresh rate to stock defaults")
    }
}
