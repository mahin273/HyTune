package com.mahin.hytune.ui.components

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
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.mahin.hytune.ui.theme.BackgroundDark
import com.mahin.hytune.ui.theme.LimePrimaryDark
import com.mahin.hytune.ui.theme.OnSurfaceDark
import com.mahin.hytune.ui.theme.OnSurfaceVariantDark
import com.mahin.hytune.ui.theme.OutlineDark
import com.mahin.hytune.ui.theme.SurfaceDark
import com.mahin.hytune.ui.theme.SurfaceVariantDark

sealed class PowerAction(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val command: String
) {
    data object Reboot : PowerAction(
        title = "Reboot System",
        description = "Standard system restart",
        icon = Icons.Default.Refresh,
        command = "reboot"
    )

    data object Recovery : PowerAction(
        title = "Reboot to Recovery",
        description = "Boot into TWRP, OrangeFox, or Recovery",
        icon = Icons.Default.SystemUpdate,
        command = "reboot recovery"
    )

    data object Bootloader : PowerAction(
        title = "Reboot to Bootloader",
        description = "Fastboot and flashing mode",
        icon = Icons.Default.RestartAlt,
        command = "reboot bootloader"
    )

    data object SoftReboot : PowerAction(
        title = "Restart SystemUI",
        description = "Soft restart interface without kernel reboot",
        icon = Icons.Default.Refresh,
        command = "pkill -f com.android.systemui"
    )

    data object PowerOff : PowerAction(
        title = "Power Off",
        description = "Safely shut down device",
        icon = Icons.Default.PowerSettingsNew,
        command = "reboot -p"
    )
}

@Composable
fun PowerMenuDialog(
    onDismissRequest: () -> Unit,
    onExecuteAction: (PowerAction) -> Unit
) {
    var pendingAction by remember { mutableStateOf<PowerAction?>(null) }

    val actions = remember {
        listOf(
            PowerAction.Reboot,
            PowerAction.Recovery,
            PowerAction.Bootloader,
            PowerAction.SoftReboot,
            PowerAction.PowerOff
        )
    }

    if (pendingAction != null) {
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            containerColor = SurfaceDark,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = LimePrimaryDark,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Confirm ${pendingAction?.title}",
                        color = OnSurfaceDark,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Text(
                    text = "Are you sure you want to execute this power action? Any unsaved application state will be lost.",
                    color = OnSurfaceVariantDark
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val act = pendingAction
                        pendingAction = null
                        if (act != null) {
                            onExecuteAction(act)
                        }
                    }
                ) {
                    Text("Execute", color = LimePrimaryDark, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) {
                    Text("Cancel", color = OnSurfaceVariantDark)
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = SurfaceDark,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(SurfaceVariantDark, RoundedCornerShape(10.dp))
                        .border(1.dp, OutlineDark, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = null,
                        tint = LimePrimaryDark,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "Power Menu",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceDark
                    )
                    Text(
                        text = "Root execution required",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariantDark
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                actions.forEach { action ->
                    PowerActionItem(
                        action = action,
                        onClick = { pendingAction = action }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Close", color = OnSurfaceVariantDark)
            }
        }
    )
}

@Composable
private fun PowerActionItem(
    action: PowerAction,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceVariantDark)
            .border(1.dp, OutlineDark, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = null,
            tint = LimePrimaryDark,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = action.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = OnSurfaceDark
            )
            Text(
                text = action.description,
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceVariantDark
            )
        }
    }
}
