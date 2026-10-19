package com.mahin.hytune.ui.components.scene

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahin.hytune.domain.sampler.model.MemorySample
import com.mahin.hytune.ui.theme.LimePrimaryDark
import com.mahin.hytune.ui.theme.OnSurfaceDark
import com.mahin.hytune.ui.theme.OnSurfaceVariantDark
import com.mahin.hytune.ui.theme.OutlineDark
import com.mahin.hytune.ui.theme.SurfaceDark
import com.mahin.hytune.ui.theme.SurfaceVariantDark

@Composable
fun SceneMemoryCard(
    memorySample: MemorySample?,
    onTrimCaches: () -> Unit = {},
    onCompactZram: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val totalGb = ((memorySample?.totalBytes ?: 1L) / (1024f * 1024f * 1024f)).coerceAtLeast(0.1f)
    val usedGb = ((memorySample?.usedBytes ?: 0L) / (1024f * 1024f * 1024f)).coerceAtLeast(0f)
    val ramPercent = (memorySample?.usagePercent ?: 0f).coerceIn(0f, 100f)

    val swapTotalGb = ((memorySample?.swapTotalBytes ?: 1L) / (1024f * 1024f * 1024f)).coerceAtLeast(0.1f)
    val swapUsedGb = ((memorySample?.swapUsedBytes ?: 0L) / (1024f * 1024f * 1024f)).coerceAtLeast(0f)
    val swapPercent = (memorySample?.swapUsagePercent ?: 0f).coerceIn(0f, 100f)

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
            // Left: Donut Arc Gauge
            SceneCircularGauge(
                percent = ramPercent,
                title = "Memory",
                indicatorColor = LimePrimaryDark,
                modifier = Modifier.size(80.dp)
            )

            // Right: RAM & SWAP progress bars + action buttons
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // RAM row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "RAM",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = OnSurfaceDark
                            )
                            Text(
                                text = "%.0f%% (%.1f GB)".format(ramPercent, usedGb),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = LimePrimaryDark
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { ramPercent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = LimePrimaryDark,
                            trackColor = SurfaceVariantDark
                        )
                    }
                    IconButton(
                        onClick = onTrimCaches,
                        modifier = Modifier
                            .size(32.dp)
                            .padding(start = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CleaningServices,
                            contentDescription = "Trim Caches",
                            tint = OnSurfaceVariantDark,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // SWAP / ZRAM row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Swaps",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = OnSurfaceDark
                            )
                            Text(
                                text = "%.0f%% (%.1f GB)".format(swapPercent, swapUsedGb),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF64B5F6)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { swapPercent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = Color(0xFF64B5F6),
                            trackColor = SurfaceVariantDark
                        )
                    }
                    IconButton(
                        onClick = onCompactZram,
                        modifier = Modifier
                            .size(32.dp)
                            .padding(start = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Compress,
                            contentDescription = "Compact ZRAM",
                            tint = OnSurfaceVariantDark,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Details footer
                val availableGb = ((memorySample?.availableBytes ?: 0L) / (1024f * 1024f * 1024f)).coerceAtLeast(0f)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Avail: %.1f GB".format(availableGb),
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariantDark
                    )
                    Text(
                        text = "Total: %.1f GB".format(totalGb),
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariantDark
                    )
                }
            }
        }
    }
}

@Composable
fun SceneCircularGauge(
    percent: Float,
    title: String,
    indicatorColor: Color,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = percent.coerceIn(0f, 100f) / 100f,
        label = "gauge"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(72.dp)) {
            val strokeWidth = 8.dp.toPx()
            // Track
            drawArc(
                color = Color(0xFF1E2527),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            // Progress
            drawArc(
                color = indicatorColor,
                startAngle = 135f,
                sweepAngle = 270f * animatedProgress,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = OnSurfaceDark
            )
            Text(
                text = "%.0f%%".format(percent),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = indicatorColor
            )
        }
    }
}
