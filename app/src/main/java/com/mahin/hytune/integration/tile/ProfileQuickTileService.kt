package com.mahin.hytune.integration.tile

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.mahin.hytune.domain.engine.ProfileEngine
import com.mahin.hytune.domain.profile.BuiltInProfiles
import com.mahin.hytune.domain.profile.ProfileRepository
import com.mahin.hytune.domain.profile.model.TuningProfile
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ProfileQuickTileService : TileService() {

    @Inject
    lateinit var profileRepository: ProfileRepository

    @Inject
    lateinit var profileEngine: ProfileEngine

    private var serviceScope: CoroutineScope? = null

    override fun onCreate() {
        super.onCreate()
        serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope?.cancel()
        serviceScope = null
    }

    override fun onStartListening() {
        super.onStartListening()
        serviceScope?.launch {
            val active = profileRepository.getActiveProfile().first()
            updateTileDisplay(active)
        }
    }

    override fun onClick() {
        super.onClick()
        serviceScope?.launch {
            val active = profileRepository.getActiveProfile().first()
            val rotatingList = listOf(
                BuiltInProfiles.BALANCE,
                BuiltInProfiles.PERFORMANCE,
                BuiltInProfiles.FAST,
                BuiltInProfiles.POWERSAVE
            )

            val currentIndex = rotatingList.indexOfFirst { it.id.equals(active.id, ignoreCase = true) }
            val nextIndex = if (currentIndex >= 0) (currentIndex + 1) % rotatingList.size else 0
            val nextProfile = rotatingList[nextIndex]

            profileEngine.applyProfile(nextProfile)
            profileRepository.setActiveProfile(nextProfile.id)
            updateTileDisplay(nextProfile)
        }
    }

    fun updateTileDisplay(profile: TuningProfile) {
        val tile = try {
            qsTile
        } catch (_: Throwable) {
            null
        } ?: return
        tile.state = Tile.STATE_ACTIVE
        tile.icon = Icon.createWithResource(this, android.R.drawable.ic_menu_manage)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.label = "HyTune Profile"
            tile.subtitle = profile.name
        } else {
            tile.label = "HyTune: ${profile.name}"
        }

        tile.updateTile()
    }
}
