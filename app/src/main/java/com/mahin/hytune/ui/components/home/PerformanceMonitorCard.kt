package com.mahin.hytune.ui.components.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahin.hytune.domain.sampler.model.SystemSnapshot
import com.mahin.hytune.ui.theme.AmberTertiaryDark
import com.mahin.hytune.ui.theme.LimePrimaryDark
import com.mahin.hytune.ui.theme.MonospaceCodeStyle

@Composable
fun PerformanceMonitorCard(
    snapshot: SystemSnapshot?,
    cpuHistory: List<Float>,
    ramHistory: List<Float>,
    tempHistory: List<Float>,
    modifier: Modifier = Modifier
) {
    val cpuUsage = snapshot?.cpu?.overallUsagePercent ?: 0f
    val animatedCpuUsage by animateFloatAsState(targetValue = cpuUsage, label = "CpuUsageAnim")

    val maxFreqKHz = snapshot?.cpu?.coreFrequenciesKHz?.values?.maxOrNull() ?: 2_400_000L
    val currentFreqGhz = maxFreqKHz / 1_000_000f

    val maxTempC = snapshot?.thermals?.maxTempC ?: 38.0f
    val ramUsedGb = snapshot?.memory?.let { (it.totalBytes - it.availableBytes) / (1024f * 1024f * 1024f) } ?: 5.2f
    val ramTotalGb = snapshot?.memory?.let { it.totalBytes / (1024f * 1024f * 1024f) } ?: 8.0f

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Performance",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )

            // Circular Gauge + Metrics row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Donut Circular Gauge
                Box(
                    modifier = Modifier.size(110.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(100.dp)) {
                        val strokeWidth = 10.dp.toPx()
                        // Track
                        drawArc(
                            color = Color(0xFF1E2622),
                            startAngle = 135f,
                            sweepAngle = 270f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                        // Progress
                        val sweep = (animatedCpuUsage / 100f).coerceIn(0f, 1f) * 270f
                        drawArc(
                            color = LimePrimaryDark,
                            startAngle = 135f,
                            sweepAngle = sweep,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${animatedCpuUsage.toInt()}%",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Text(
                            text = "CPU Usage",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }

                // Metric Stats Column
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(start = 16.dp)
                ) {
                    MetricRowItem(
                        value = "%.2f GHz".format(currentFreqGhz),
                        label = "Current Frequency"
                    )
                    MetricRowItem(
                        value = "%.1f C".format(maxTempC),
                        label = "Temperature"
                    )
                    MetricRowItem(
                        value = "%.1f GB / %.1f GB".format(ramUsedGb, ramTotalGb),
                        label = "RAM Usage"
                    )
                }
            }

            // Legend dots
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LegendDot(color = LimePrimaryDark, text = "CPU")
                LegendDot(color = Color(0xFF4DD0E1), text = "RAM")
                LegendDot(color = AmberTertiaryDark, text = "Temperature")
            }

            // Multi-line wave chart
            if (cpuHistory.size >= 2 || ramHistory.size >= 2) {
                MultiLineWaveChart(
                    cpuData = cpuHistory,
                    ramData = ramHistory,
                    tempData = tempHistory,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(70.dp)
                )
            }

            // Footer Quick Cards: Battery & Wi-Fi
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickIndicatorCard(
                    icon = Icons.Default.BatteryFull,
                    title = "Battery",
                    value = "${snapshot?.battery?.percent ?: 78}%",
                    modifier = Modifier.weight(1f)
                )

                QuickIndicatorCard(
                    icon = Icons.Default.Wifi,
                    title = "Network",
                    value = "Wi-Fi",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MetricRowItem(value: String, label: String) {
    Column {
        Text(
            text = value,
            style = MonospaceCodeStyle.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

@Composable
private fun MultiLineWaveChart(
    cpuData: List<Float>,
    ramData: List<Float>,
    tempData: List<Float>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        fun drawSmoothLine(data: List<Float>, color: Color, maxVal: Float = 100f) {
            if (data.size < 2) return
            val stepX = width / (data.size - 1).coerceAtLeast(1)
            val path = Path()

            data.forEachIndexed { i, value ->
                val x = i * stepX
                val y = height - (value.coerceIn(0f, maxVal) / maxVal) * height
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        drawSmoothLine(ramData, Color(0xFF4DD0E1), 100f)
        drawSmoothLine(tempData, AmberTertiaryDark, 100f)
        drawSmoothLine(cpuData, LimePrimaryDark, 100f)
    }
}

@Composable
private fun QuickIndicatorCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = LimePrimaryDark,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }
    }
}
