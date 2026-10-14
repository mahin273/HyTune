package com.mahin.hytune.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector

sealed class HyTuneDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    data object Home : HyTuneDestination(
        route = "home",
        label = "Home",
        icon = Icons.Filled.Home
    )

    data object Tune : HyTuneDestination(
        route = "tune",
        label = "Tune",
        icon = Icons.Filled.Tune
    )

    data object Apps : HyTuneDestination(
        route = "apps",
        label = "Apps",
        icon = Icons.Filled.Apps
    )

    data object Monitor : HyTuneDestination(
        route = "monitor",
        label = "Monitor",
        icon = Icons.Filled.Speed
    )

    data object More : HyTuneDestination(
        route = "more",
        label = "More",
        icon = Icons.Filled.MoreHoriz
    )

    companion object {
        val bottomNavDestinations: List<HyTuneDestination> by lazy {
            listOf(Home, Tune, Apps, Monitor, More)
        }

        fun fromRoute(route: String?): HyTuneDestination {
            return bottomNavDestinations.firstOrNull { it.route == route } ?: Home
        }
    }
}
