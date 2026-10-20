package com.mahin.hytune.ui.components.scene

import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mahin.hytune.core.privilege.PrivilegeTier
import com.mahin.hytune.ui.components.HyTuneLogo
import com.mahin.hytune.ui.theme.LimePrimaryDark
import com.mahin.hytune.ui.theme.OnSurfaceDark
import com.mahin.hytune.ui.theme.OnSurfaceVariantDark
import com.mahin.hytune.ui.theme.OutlineDark
import com.mahin.hytune.ui.theme.SurfaceDark
import com.mahin.hytune.ui.theme.SurfaceVariantDark
import java.util.concurrent.TimeUnit

@Composable
fun AboutHyTuneDialog(
    privilegeTier: PrivilegeTier,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uptimeMillis = SystemClock.elapsedRealtime()
    val hours = TimeUnit.MILLISECONDS.toHours(uptimeMillis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(uptimeMillis) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(uptimeMillis) % 60
    val uptimeStr = "%02d:%02d:%02d".format(hours, minutes, seconds)

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HyTuneLogo(size = 32.dp)
                Column {
                    Text(
                        text = "HyTune",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceDark
                    )
                    Text(
                        text = "Version 1.0.0 (Release)",
                        style = MaterialTheme.typography.bodySmall,
                        color = LimePrimaryDark
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Open Source Hardware Optimization & Kernel Tuning Suite for Android.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariantDark
                )

                Spacer(modifier = Modifier.height(4.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceVariantDark,
                    border = BorderStroke(1.dp, OutlineDark)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AboutInfoRow(
                            icon = Icons.Default.PhoneAndroid,
                            label = "Device",
                            value = "${Build.MANUFACTURER} ${Build.MODEL}"
                        )
                        AboutInfoRow(
                            icon = Icons.Default.Memory,
                            label = "Board / Platform",
                            value = Build.BOARD
                        )
                        AboutInfoRow(
                            icon = Icons.Default.Android,
                            label = "Android Version",
                            value = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
                        )
                        AboutInfoRow(
                            icon = Icons.Default.Security,
                            label = "Privilege Backend",
                            value = privilegeTier.name
                        )
                        AboutInfoRow(
                            icon = Icons.Default.Schedule,
                            label = "System Uptime",
                            value = uptimeStr
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismissRequest,
                colors = ButtonDefaults.buttonColors(containerColor = LimePrimaryDark)
            ) {
                Text(
                    text = "Close",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}

@Composable
private fun AboutInfoRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = LimePrimaryDark,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariantDark
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = OnSurfaceDark
        )
    }
}
