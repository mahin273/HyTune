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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahin.hytune.domain.overlay.model.OverlayConfig
import com.mahin.hytune.domain.overlay.model.OverlayTelemetry
import java.util.Locale

private val CyanHudAccent = Color(0xFF00E5FF)
private val LimeHudAccent = Color(0xFFA6FF00)
private val PurpleHudAccent = Color(0xFFB388FF)
private val AmberHudAccent = Color(0xFFFF9100)
private val MintHudAccent = Color(0xFF64FFDA)
private val BlueHudAccent = Color(0xFF82B1FF)
private val RedHudThrottled = Color(0xFFFF3D00)
private val GlassBackgroundDark = Color(0xF20B0F17)

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
    val pillBg = GlassBackgroundDark.copy(alpha = config.opacity.coerceIn(0.2f, 1.0f))
    val borderColor = if (telemetry.isThrottling) RedHudThrottled else CyanHudAccent.copy(alpha = 0.45f)
    val statusDotColor = if (telemetry.isThrottling) RedHudThrottled else LimeHudAccent

    Surface(
        modifier = modifier.clickable { onToggleCompact() },
        shape = RoundedCornerShape(20.dp),
        color = pillBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Status LED dot
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(statusDotColor)
            )

            // Metrics Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (config.showFps && telemetry.fps > 0.0) {
                    Text(
                        text = String.format(Locale.US, "%.0f FPS", telemetry.fps),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        color = CyanHudAccent
                    )
                }

                if (config.showCpu) {
                    if (config.showFps && telemetry.fps > 0.0) {
                        Text(text = "|", color = Color(0x44FFFFFF), fontSize = 10.sp)
                    }
                    Text(
                        text = "CPU ${telemetry.cpuLoadPercent}%",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        color = LimeHudAccent
                    )
                }

                if (config.showTemp && telemetry.temperatureC > 0f) {
                    Text(text = "|", color = Color(0x44FFFFFF), fontSize = 10.sp)
                    Text(
                        text = String.format(Locale.US, "%.0fC", telemetry.temperatureC),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        color = if (telemetry.temperatureC >= 75f) RedHudThrottled else AmberHudAccent
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.UnfoldMore,
                contentDescription = "Expand HUD",
                tint = Color(0x88FFFFFF),
                modifier = Modifier.size(14.dp)
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
    val cardBg = GlassBackgroundDark.copy(alpha = config.opacity.coerceIn(0.3f, 1.0f))
    val borderColor = if (telemetry.isThrottling) RedHudThrottled else CyanHudAccent.copy(alpha = 0.35f)

    Surface(
        modifier = modifier.width(230.dp),
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        tonalElevation = 10.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Drag Handle Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(Color(0x44FFFFFF))
                )
            }

            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (telemetry.isThrottling) RedHudThrottled else LimeHudAccent)
                    )
                    Text(
                        text = "HYTUNE HUD",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        ),
                        color = CyanHudAccent
                    )
                }

                if (telemetry.isThrottling) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = RedHudThrottled.copy(alpha = 0.25f),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, RedHudThrottled)
                    ) {
                        Text(
                            text = "THROTTLED",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 8.sp
                            ),
                            color = RedHudThrottled,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Icon(
                    imageVector = Icons.Default.UnfoldLess,
                    contentDescription = "Collapse HUD",
                    tint = CyanHudAccent,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onToggleCompact() }
                )

                Spacer(modifier = Modifier.width(8.dp))

                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close HUD",
                    tint = Color(0xAAFFFFFF),
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onClose() }
                )
            }

            // Metrics Section
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (config.showFps && telemetry.fps > 0.0) {
                    val fpsStr = String.format(Locale.US, "%.1f FPS", telemetry.fps)
                    SleekMetricRow(
                        label = "FPS",
                        value = fpsStr,
                        badgeColor = CyanHudAccent,
                        progress = (telemetry.fps.toFloat() / 120f).coerceIn(0f, 1f)
                    )
                }

                if (config.showCpu) {
                    val cpuStr = "${telemetry.cpuLoadPercent}% @ ${telemetry.cpuFreqMhz} MHz"
                    SleekMetricRow(
                        label = "CPU",
                        value = cpuStr,
                        badgeColor = LimeHudAccent,
                        progress = (telemetry.cpuLoadPercent / 100f).coerceIn(0f, 1f)
                    )
                }

                if (config.showGpu && telemetry.gpuFreqMhz > 0) {
                    val gpuStr = "${telemetry.gpuLoadPercent}% @ ${telemetry.gpuFreqMhz} MHz"
                    SleekMetricRow(
                        label = "GPU",
                        value = gpuStr,
                        badgeColor = PurpleHudAccent,
                        progress = (telemetry.gpuLoadPercent / 100f).coerceIn(0f, 1f)
                    )
                }

                if (config.showTemp && telemetry.temperatureC > 0f) {
                    val tempStr = String.format(Locale.US, "%.1f C", telemetry.temperatureC)
                    val isHot = telemetry.temperatureC >= 75f
                    SleekMetricRow(
                        label = "TEMP",
                        value = tempStr,
                        badgeColor = if (isHot) RedHudThrottled else AmberHudAccent,
                        progress = (telemetry.temperatureC / 100f).coerceIn(0f, 1f)
                    )
                }

                if (config.showBattery && telemetry.batteryPowerWatts > 0f) {
                    val wattStr = String.format(Locale.US, "%.2f W", telemetry.batteryPowerWatts)
                    SleekMetricRow(
                        label = "BATT",
                        value = wattStr,
                        badgeColor = MintHudAccent
                    )
                }

                if (config.showRam && telemetry.ramTotalMb > 0) {
                    val ramStr = "${telemetry.ramUsedMb} / ${telemetry.ramTotalMb} MB"
                    val ramProgress = (telemetry.ramUsedMb.toFloat() / telemetry.ramTotalMb.toFloat()).coerceIn(0f, 1f)
                    SleekMetricRow(
                        label = "RAM",
                        value = ramStr,
                        badgeColor = BlueHudAccent,
                        progress = ramProgress
                    )
                }
            }
        }
    }
}

@Composable
private fun SleekMetricRow(
    label: String,
    value: String,
    badgeColor: Color,
    progress: Float? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = badgeColor.copy(alpha = 0.18f)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp
                    ),
                    color = badgeColor,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }

            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                ),
                color = Color(0xFFEEEEEE)
            )
        }

        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .clip(CircleShape),
                color = badgeColor,
                trackColor = Color(0x33FFFFFF)
            )
        }
    }
}
