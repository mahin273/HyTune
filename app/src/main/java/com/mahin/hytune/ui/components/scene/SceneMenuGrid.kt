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
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

data class MenuItemData(
    val title: String,
    val icon: ImageVector,
    val iconTint: Color = Color(0xFF64B5F6),
    val actionKey: String
)

@Composable
fun SceneMenuGrid(
    onMenuItemClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section 1: Device Performance
        MenuSection(
            title = "Device Performance",
            items = listOf(
                MenuItemData("Cpu Control", Icons.Default.DeveloperBoard, Color(0xFF42A5F5), "tune_cpu"),
                MenuItemData("SWAP Control", Icons.Default.Memory, Color(0xFF42A5F5), "tune_memory")
            ),
            onItemClick = onMenuItemClick
        )

        // Section 2: Battery&Power
        MenuSection(
            title = "Battery&Power",
            items = listOf(
                MenuItemData("Power Stats", Icons.Default.PieChart, Color(0xFF42A5F5), "battery_stats"),
                MenuItemData("Charging stats", Icons.Default.BatteryChargingFull, Color(0xFF42A5F5), "charging_stats"),
                MenuItemData("FPS Stats", Icons.Default.Speed, Color(0xFF42A5F5), "fps_stats"),
                MenuItemData("Processes", Icons.AutoMirrored.Filled.ListAlt, Color(0xFF42A5F5), "processes")
            ),
            onItemClick = onMenuItemClick
        )

        // Section 3: Apps&Permissions
        MenuSection(
            title = "Apps&Permissions",
            items = listOf(
                MenuItemData("Applications", Icons.Default.Apps, Color(0xFF42A5F5), "app_manager"),
                MenuItemData("Activities", Icons.Default.DeveloperBoard, Color(0xFF42A5F5), "app_activities"),
                MenuItemData("App Freezer", Icons.Default.AcUnit, Color(0xFF42A5F5), "app_freezer")
            ),
            onItemClick = onMenuItemClick
        )

        // Section 4: Magisk / Kernel Modules
        MenuSection(
            title = "Kernel & Modules",
            items = listOf(
                MenuItemData("Root Modules", Icons.Default.CloudDownload, Color(0xFF42A5F5), "root_modules"),
                MenuItemData("HyTune Engine", Icons.Default.Security, Color(0xFF42A5F5), "engine_status")
            ),
            onItemClick = onMenuItemClick
        )

        // Section 5: Advance
        MenuSection(
            title = "Advance",
            items = listOf(
                MenuItemData("Sundry", Icons.Default.Star, Color(0xFF42A5F5), "sundry"),
                MenuItemData("Backup&Restore", Icons.Default.Restore, Color(0xFF42A5F5), "backup_restore"),
                MenuItemData("Display Tweaks", Icons.Default.DisplaySettings, Color(0xFF42A5F5), "tune_display"),
                MenuItemData("Charge Control", Icons.Default.Power, Color(0xFF42A5F5), "charge_control"),
                MenuItemData("Thermal Editor", Icons.Default.Thermostat, Color(0xFF42A5F5), "thermal_editor"),
                MenuItemData("GPU Tuner", Icons.Default.Speed, Color(0xFF42A5F5), "tune_gpu")
            ),
            onItemClick = onMenuItemClick
        )

        // Section 6: Diagnose
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Diagnose",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = OnSurfaceVariantDark
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onMenuItemClick("screen_test") },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(OutlineDark))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = Color(0xFF42A5F5),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = "Hardware Diagnostic & Benchmark",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurfaceDark
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuSection(
    title: String,
    items: List<MenuItemData>,
    onItemClick: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = OnSurfaceVariantDark
        )

        val chunked = items.chunked(2)
        chunked.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowItems.forEach { item ->
                    MenuGridCard(
                        item = item,
                        onClick = { onItemClick(item.actionKey) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MenuGridCard(
    item: MenuItemData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(OutlineDark))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = item.iconTint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = OnSurfaceDark,
                maxLines = 1
            )
        }
    }
}
