package com.mahin.hytune.ui.navigation

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mahin.hytune.ui.components.HyTuneLogo
import com.mahin.hytune.ui.components.PowerMenuDialog
import com.mahin.hytune.ui.dashboard.DashboardViewModel
import com.mahin.hytune.ui.screens.AppsScreen
import com.mahin.hytune.ui.screens.HomeScreen
import com.mahin.hytune.ui.screens.MonitorScreen
import com.mahin.hytune.ui.screens.MoreScreen
import com.mahin.hytune.ui.screens.TuneScreen
import com.mahin.hytune.ui.settings.SettingsBottomSheet
import com.mahin.hytune.ui.theme.BackgroundDark
import com.mahin.hytune.ui.theme.LimePrimaryDark
import com.mahin.hytune.ui.theme.OnSurfaceVariantDark
import com.mahin.hytune.ui.theme.OutlineDark
import com.mahin.hytune.ui.theme.SurfaceVariantDark

@Composable
fun HyTuneAppShell(
    navController: NavHostController = rememberNavController(),
    dashboardViewModel: DashboardViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val currentDestination = HyTuneDestination.fromRoute(currentRoute)
    val uiState by dashboardViewModel.uiState.collectAsStateWithLifecycle()
    val isOverlayActive by dashboardViewModel.isOverlayShowing.collectAsStateWithLifecycle()
    val appSettings by dashboardViewModel.settingsFlow.collectAsStateWithLifecycle(initialValue = com.mahin.hytune.data.local.datastore.AppSettings())

    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }
    var showPowerDialog by rememberSaveable { mutableStateOf(false) }

    fun displayLabel(destination: HyTuneDestination): String = when (destination) {
        HyTuneDestination.Home -> "Overview"
        HyTuneDestination.Tune -> "Tuner"
        HyTuneDestination.Apps -> "Apps"
        HyTuneDestination.Monitor -> "Monitor"
        HyTuneDestination.More -> "Menu"
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BackgroundDark,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BackgroundDark)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Logo & Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HyTuneLogo(size = 36.dp)
                    Column {
                        Text(
                            text = "HyTune",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = displayLabel(currentDestination),
                            style = MaterialTheme.typography.labelSmall,
                            color = LimePrimaryDark,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Right: Action Buttons (HUD Overlay toggle, Power Menu, Settings)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Floating HUD Overlay Toggle
                    IconButton(
                        onClick = {
                            if (!Settings.canDrawOverlays(context)) {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                ).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                try {
                                    context.startActivity(intent)
                                    Toast.makeText(
                                        context,
                                        "Grant 'Display over other apps' to enable HUD Overlay",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } catch (_: Exception) {
                                    // Fallback
                                }
                            } else {
                                dashboardViewModel.toggleOverlay()
                            }
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                if (isOverlayActive) Color(0xFF1E2818) else SurfaceVariantDark,
                                RoundedCornerShape(10.dp)
                            )
                            .border(
                                1.dp,
                                if (isOverlayActive) LimePrimaryDark else OutlineDark,
                                RoundedCornerShape(10.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "HUD Overlay",
                            tint = if (isOverlayActive) LimePrimaryDark else OnSurfaceVariantDark,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Power Menu Dialog Button
                    IconButton(
                        onClick = { showPowerDialog = true },
                        modifier = Modifier
                            .size(38.dp)
                            .background(SurfaceVariantDark, RoundedCornerShape(10.dp))
                            .border(1.dp, OutlineDark, RoundedCornerShape(10.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Power Menu",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Settings Button
                    IconButton(
                        onClick = { showSettingsSheet = true },
                        modifier = Modifier
                            .size(38.dp)
                            .background(SurfaceVariantDark, RoundedCornerShape(10.dp))
                            .border(1.dp, OutlineDark, RoundedCornerShape(10.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = BackgroundDark,
                tonalElevation = 0.dp,
                modifier = Modifier.drawBehind {
                    drawLine(
                        color = OutlineDark,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            ) {
                HyTuneDestination.bottomNavDestinations.forEach { destination ->
                    val selected = currentRoute == destination.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (currentRoute != destination.route) {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.label
                            )
                        },
                        label = {
                            Text(
                                text = displayLabel(destination),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = LimePrimaryDark,
                            selectedTextColor = LimePrimaryDark,
                            unselectedIconColor = OnSurfaceVariantDark,
                            unselectedTextColor = OnSurfaceVariantDark,
                            indicatorColor = Color.Transparent
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = HyTuneDestination.Home.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(HyTuneDestination.Home.route) {
                HomeScreen(
                    onNavigateToTab = { destination ->
                        if (currentRoute != destination.route) {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
            composable(HyTuneDestination.Tune.route) {
                TuneScreen(
                    onNavigateToTab = { destination ->
                        if (currentRoute != destination.route) {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
            composable(HyTuneDestination.Apps.route) {
                AppsScreen()
            }
            composable(HyTuneDestination.Monitor.route) {
                MonitorScreen()
            }
            composable(HyTuneDestination.More.route) {
                MoreScreen(
                    onNavigateToTab = { destination ->
                        if (currentRoute != destination.route) {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        }

        if (showPowerDialog) {
            PowerMenuDialog(
                onDismissRequest = { showPowerDialog = false },
                onExecuteAction = { action ->
                    dashboardViewModel.executePowerAction(action.command)
                    showPowerDialog = false
                }
            )
        }

        if (showSettingsSheet) {
            SettingsBottomSheet(
                onDismissRequest = { showSettingsSheet = false },
                privilegeTier = uiState.privilegeTier,
                themeStyle = appSettings.themeStyle,
                onThemeStyleChanged = { dashboardViewModel.setThemeStyle(it) },
                onRequestRoot = { dashboardViewModel.requestRoot() }
            )
        }
    }
}
