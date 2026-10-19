package com.mahin.hytune.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahin.hytune.domain.overlay.model.OverlayConfig
import com.mahin.hytune.domain.overlay.model.OverlayTelemetry
import java.util.Locale

@Composable
fun FloatingOverlayView(
    telemetry: OverlayTelemetry,
    config: OverlayConfig,
    onToggleCompact: () -> Unit,
    onDragDelta: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .wrapContentSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDragDelta(dragAmount.x, dragAmount.y)
                    }
                )
            }
    ) {
        if (config.isCompact) {
            CompactOverlayPill(
                telemetry = telemetry,
                config = config,
                onToggleCompact = onToggleCompact
            )
        } else {
            ExpandedOverlayCard(
                telemetry = telemetry,
                config = config,
                onToggleCompact = onToggleCompact,
                onClose = onClose
            )
        }
    }
}

@Composable
private fun CompactOverlayPill(
    telemetry: OverlayTelemetry,
    config: OverlayConfig,
    onToggleCompact: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pillBg = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
        .copy(alpha = config.opacity.coerceIn(0.2f, 1.0f))
    val borderColor = if (telemetry.isThrottling) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    }

    Surface(
        modifier = modifier
            .clickable { onToggleCompact() },
        shape = RoundedCornerShape(20.dp),
        color = pillBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        tonalElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val text = telemetry.toCompactText(config)
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                color = if (telemetry.isThrottling) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ExpandedOverlayCard(
    telemetry: OverlayTelemetry,
    config: OverlayConfig,
    onToggleCompact: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardBg = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp)
        .copy(alpha = config.opacity.coerceIn(0.3f, 1.0f))
    val borderColor = if (telemetry.isThrottling) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        modifier = modifier.width(220.dp),
        shape = RoundedCornerShape(14.dp),
        color = cardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.padding(bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "HyTune HUD",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                if (telemetry.isThrottling) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "THROTTLED",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Pill",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .clickable { onToggleCompact() }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "X",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clickable { onClose() }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            // Grid of Metrics
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (config.showFps && telemetry.fps > 0.0) {
                    val fpsStr = String.format(Locale.US, "%.1f FPS", telemetry.fps)
                    MetricRow(
                        label = "FPS",
                        value = fpsStr
                    )
                }
                if (config.showCpu) {
                    MetricRow(
                        label = "CPU",
                        value = "${telemetry.cpuLoadPercent}% @ ${telemetry.cpuFreqMhz} MHz"
                    )
                }
                if (config.showGpu && telemetry.gpuFreqMhz > 0) {
                    MetricRow(
                        label = "GPU",
                        value = "${telemetry.gpuLoadPercent}% @ ${telemetry.gpuFreqMhz} MHz"
                    )
                }
                if (config.showTemp && telemetry.temperatureC > 0f) {
                    val tempStr = String.format(Locale.US, "%.1f C", telemetry.temperatureC)
                    MetricRow(
                        label = "Thermal",
                        value = tempStr,
                        valueColor = if (telemetry.temperatureC >= 75f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }
                if (config.showBattery && telemetry.batteryPowerWatts > 0f) {
                    val wattStr = String.format(Locale.US, "%.2f W", telemetry.batteryPowerWatts)
                    MetricRow(
                        label = "Battery",
                        value = wattStr
                    )
                }
                if (config.showRam && telemetry.ramTotalMb > 0) {
                    MetricRow(
                        label = "RAM",
                        value = "${telemetry.ramUsedMb} / ${telemetry.ramTotalMb} MB"
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(55.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = valueColor
        )
    }
}

private fun androidx.compose.material3.ColorScheme.surfaceColorAtElevation(elevation: androidx.compose.ui.unit.Dp): Color {
    return surface
}
