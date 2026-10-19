package com.mahin.hytune.ui.components.scene

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahin.hytune.ui.theme.LimePrimaryDark
import com.mahin.hytune.ui.theme.OnSurfaceDark
import com.mahin.hytune.ui.theme.OnSurfaceVariantDark
import com.mahin.hytune.ui.theme.OutlineDark
import com.mahin.hytune.ui.theme.SurfaceDark
import com.mahin.hytune.ui.theme.SurfaceVariantDark

data class ProfileModeItem(
    val id: String,
    val name: String
)

@Composable
fun SceneCustomModeCard(
    profiles: List<ProfileModeItem>,
    activeProfileId: String,
    onProfileSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var customEnabled by remember { mutableStateOf(true) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val activeProfile = profiles.firstOrNull { it.id == activeProfileId }
    val activeTitle = activeProfile?.name ?: "Balance"

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(OutlineDark))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Custom + Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Custom Profile",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceDark
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1565C0))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "TUNER",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Switch(
                    checked = customEnabled,
                    onCheckedChange = { customEnabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BackgroundDarkColor(),
                        checkedTrackColor = LimePrimaryDark,
                        uncheckedTrackColor = SurfaceVariantDark
                    )
                )
            }

            // Dropdown selector: Global default
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceVariantDark)
                        .border(1.dp, OutlineDark, RoundedCornerShape(12.dp))
                        .clickable { dropdownExpanded = true }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Global default",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurfaceDark
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = activeTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = LimePrimaryDark
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = OnSurfaceVariantDark
                        )
                    }
                }

                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                    modifier = Modifier.background(SurfaceDark)
                ) {
                    profiles.forEach { profile ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = profile.name,
                                    fontWeight = if (profile.id == activeProfileId) FontWeight.Bold else FontWeight.Normal,
                                    color = if (profile.id == activeProfileId) LimePrimaryDark else OnSurfaceDark
                                )
                            },
                            onClick = {
                                onProfileSelected(profile.id)
                                dropdownExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SceneTunerActionTiles(
    onNavigateToAppProfiles: () -> Unit,
    onNavigateToAutoClick: () -> Unit,
    onNavigateToAppFreezer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // App Profile card
        TunerActionCard(
            title = "App profile",
            subtitle = "Set scheduling policies for each app",
            icon = Icons.Default.SportsEsports,
            iconTint = Color(0xFF64B5F6),
            onClick = onNavigateToAppProfiles
        )

        // Auto Boost / Scheduler card
        TunerActionCard(
            title = "Auto Boost",
            subtitle = "Dynamic kernel scheduling & governor booster",
            icon = Icons.Default.FlashOn,
            iconTint = LimePrimaryDark,
            onClick = onNavigateToAutoClick
        )

        // Footer Card: Look for other?
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(OutlineDark))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Look for other?",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariantDark
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "Disable throttle",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF64B5F6),
                        modifier = Modifier.clickable { onNavigateToAutoClick() }
                    )
                    Text(
                        text = "App Freezer",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF64B5F6),
                        modifier = Modifier.clickable { onNavigateToAppFreezer() }
                    )
                }
            }
        }
    }
}

@Composable
private fun TunerActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(OutlineDark))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceVariantDark)
                    .border(1.dp, OutlineDark, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = OnSurfaceDark
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariantDark
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = OnSurfaceVariantDark,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun BackgroundDarkColor(): Color = Color(0xFF0B0B0C)
