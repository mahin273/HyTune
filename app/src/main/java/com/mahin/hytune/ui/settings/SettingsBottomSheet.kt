package com.mahin.hytune.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahin.hytune.core.privilege.PrivilegeTier
import com.mahin.hytune.ui.theme.BackgroundDark
import com.mahin.hytune.ui.theme.LimePrimaryDark
import com.mahin.hytune.ui.theme.OnSurfaceDark
import com.mahin.hytune.ui.theme.OnSurfaceVariantDark
import com.mahin.hytune.ui.theme.OutlineDark
import com.mahin.hytune.ui.theme.SurfaceDark
import com.mahin.hytune.ui.theme.SurfaceVariantDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    onDismissRequest: () -> Unit,
    privilegeTier: PrivilegeTier = PrivilegeTier.ROOT,
    floatingOverlayEnabled: Boolean = false,
    onToggleFloatingOverlay: (Boolean) -> Unit = {},
    onRequestRoot: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()

    var simplifiedNotification by remember { mutableStateOf(false) }
    var languagePackage by remember { mutableStateOf(false) }
    var debugWindow by remember { mutableStateOf(false) }
    var frametimeMonitor by remember { mutableStateOf(true) }
    var keepMonitorOn by remember { mutableStateOf(false) }
    var precisionMonitor by remember { mutableStateOf(true) }
    var accessibilityDaemon by remember { mutableStateOf(true) }
    var monitorInDaemon by remember { mutableStateOf(true) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = BackgroundDark,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header (Screen 2: Back arrow + "Other Settings")
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = OnSurfaceDark
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Other Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = OnSurfaceDark
                )
            }

            // Card 1: Appearance & Notification
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(OutlineDark))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Theme Color
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Theme Color",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = OnSurfaceDark
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF0B0B0C))
                                    .border(1.5.dp, LimePrimaryDark, RoundedCornerShape(6.dp))
                            )
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF616161))
                            )
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFEDEDED))
                            )
                        }
                    }

                    HorizontalDivider(color = OutlineDark, thickness = 0.5.dp)

                    // Simplified notification
                    SettingSwitchRow(
                        title = "Simplified notification",
                        subtitle = "Performance tuning notifications use system's own notification style",
                        checked = simplifiedNotification,
                        onCheckedChange = { simplifiedNotification = it }
                    )

                    HorizontalDivider(color = OutlineDark, thickness = 0.5.dp)

                    // Language Package
                    SettingSwitchRow(
                        title = "Language Package",
                        subtitle = "Load strings.xml from external storage",
                        checked = languagePackage,
                        onCheckedChange = { languagePackage = it }
                    )
                }
            }

            // Card 2: Monitoring & Statistics
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(OutlineDark))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    SettingSwitchRow(
                        title = "Debug Window",
                        subtitle = "Display monitor foreground application process log",
                        checked = debugWindow,
                        onCheckedChange = { debugWindow = it }
                    )

                    HorizontalDivider(color = OutlineDark, thickness = 0.5.dp)

                    SettingSwitchRow(
                        title = "Frametime Monitor",
                        subtitle = "Frametime statistics are displayed on screen when FPS stats is activated",
                        checked = frametimeMonitor,
                        onCheckedChange = { frametimeMonitor = it }
                    )
                }
            }

            // Card 3: Daemon & Suspension Window
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(OutlineDark))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    SettingSwitchRow(
                        title = "Keep monitor on",
                        subtitle = "The monitor suspension window remains open in standby, which may increase power consumption",
                        checked = keepMonitorOn,
                        onCheckedChange = { keepMonitorOn = it }
                    )

                    HorizontalDivider(color = OutlineDark, thickness = 0.5.dp)

                    SettingSwitchRow(
                        title = "Precision monitor",
                        subtitle = "Improved performance monitor hover window refresh rate",
                        checked = precisionMonitor,
                        onCheckedChange = { precisionMonitor = it }
                    )

                    HorizontalDivider(color = OutlineDark, thickness = 0.5.dp)

                    SettingSwitchRow(
                        title = "Accessibility Daemon",
                        subtitle = "Use Daemon to keep AccessibilityService active",
                        checked = accessibilityDaemon,
                        onCheckedChange = { accessibilityDaemon = it }
                    )

                    HorizontalDivider(color = OutlineDark, thickness = 0.5.dp)

                    SettingSwitchRow(
                        title = "Monitor in daemon",
                        subtitle = "Enabling window monitors in scene-daemon",
                        checked = monitorInDaemon,
                        onCheckedChange = { monitorInDaemon = it }
                    )
                }
            }

            // Card 4: Configuration backup
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(OutlineDark))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Configuration backup",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceDark
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceVariantDark)
                                .border(1.dp, OutlineDark, RoundedCornerShape(8.dp))
                                .clickable { }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "UPLOAD",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64B5F6)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceVariantDark)
                                .border(1.dp, OutlineDark, RoundedCornerShape(8.dp))
                                .clickable { }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "DOWNLOAD",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64B5F6)
                            )
                        }
                    }
                }
            }

            // Footer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "HyTune System Engine v1.0.0",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariantDark
                )
                Text(
                    text = "Open Source Android Kernel Tuning Platform",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariantDark,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = OnSurfaceDark
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceVariantDark,
                lineHeight = 14.sp
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF0B0B0C),
                checkedTrackColor = LimePrimaryDark,
                uncheckedTrackColor = SurfaceVariantDark
            )
        )
    }
}
