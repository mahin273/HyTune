package com.mahin.hytune.ui.components.home

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahin.hytune.ui.theme.LimePrimaryDark

@Composable
fun DeviceInfoCard(
    socName: String?,
    modifier: Modifier = Modifier
) {
    val model = try {
        val m = Build.MODEL ?: "Android Device"
        val brand = Build.BRAND?.replaceFirstChar { it.uppercase() } ?: ""
        if (m.startsWith(brand, ignoreCase = true)) m else "$brand $m"
    } catch (_: Throwable) {
        "Android Device"
    }

    val androidVersion = try { Build.VERSION.RELEASE ?: "14" } catch (_: Throwable) { "14" }
    val osFlavor = if (Build.MANUFACTURER?.contains("Xiaomi", ignoreCase = true) == true ||
        Build.BRAND?.contains("POCO", ignoreCase = true) == true ||
        Build.BRAND?.contains("Redmi", ignoreCase = true) == true
    ) {
        "HyperOS"
    } else if (Build.MANUFACTURER?.contains("OnePlus", ignoreCase = true) == true) {
        "OxygenOS"
    } else {
        "Stock AOSP"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(LimePrimaryDark.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Smartphone,
                        contentDescription = null,
                        tint = LimePrimaryDark,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column {
                    Text(
                        text = model,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Text(
                        text = "Android $androidVersion · $osFlavor · ${socName ?: "SoC"}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
