package com.mahin.hytune.ui.components.scene

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahin.hytune.domain.probe.model.GpuInfo
import com.mahin.hytune.domain.probe.model.GpuVendor
import com.mahin.hytune.ui.theme.LimePrimaryDark
import com.mahin.hytune.ui.theme.OnSurfaceDark
import com.mahin.hytune.ui.theme.OnSurfaceVariantDark
import com.mahin.hytune.ui.theme.OutlineDark
import com.mahin.hytune.ui.theme.SurfaceDark

@Composable
fun SceneGpuCard(
    gpuInfo: GpuInfo?,
    liveFreqKHz: Long = 0L,
    gpuLoadPercent: Float = 0f,
    modifier: Modifier = Modifier
) {
    val displayFreqKHz = if (liveFreqKHz > 0L) {
        liveFreqKHz
    } else {
        gpuInfo?.minFreqKHz ?: 0L
    }
    val freqMHz = displayFreqKHz / 1000

    val vendorName = when (gpuInfo?.vendor) {
        GpuVendor.ADRENO -> "Qualcomm Adreno GPU"
        GpuVendor.MALI -> "ARM Mali GPU"
        else -> "Hardware Accelerated GPU"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(OutlineDark))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left: Circular Gauge
            SceneCircularGauge(
                percent = gpuLoadPercent,
                title = "GPU",
                indicatorColor = Color(0xFF64B5F6),
                modifier = Modifier.size(80.dp)
            )

            // Right: Frequency, Load, and Hardware Driver Specs
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (freqMHz > 0) "$freqMHz MHz" else "Dynamic",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceDark
                    )
                    Text(
                        text = "Load: %.0f%%".format(gpuLoadPercent),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (gpuLoadPercent > 80f) LimePrimaryDark else OnSurfaceVariantDark
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = vendorName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = LimePrimaryDark
                )

                Spacer(modifier = Modifier.height(2.dp))

                val gov = gpuInfo?.currentGovernor?.takeIf { it.isNotBlank() } ?: "msm-adreno-tz"
                Text(
                    text = "Governor: $gov | Vulkan 1.3 / OpenGL ES 3.2",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariantDark,
                    fontSize = 11.sp
                )
            }
        }
    }
}
