package com.mahin.hytune.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun SparklineCanvas(
    dataPoints: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xFF4DD0E1),
    fillColor: Color = lineColor.copy(alpha = 0.25f),
    strokeWidth: Dp = 2.dp,
    baselineMin: Float? = null,
    baselineMax: Float? = null
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        if (dataPoints.size < 2) return@Canvas

        val computedMin = baselineMin ?: (dataPoints.minOrNull() ?: 0f)
        val computedMax = baselineMax ?: (dataPoints.maxOrNull() ?: 100f)
        val range = (computedMax - computedMin).coerceAtLeast(1f)

        val width = size.width
        val height = size.height
        val stepX = width / (dataPoints.size - 1)

        val strokePath = Path()
        val fillPath = Path()

        dataPoints.forEachIndexed { index, value ->
            val normalizedY = ((value - computedMin) / range).coerceIn(0f, 1f)
            val x = index * stepX
            val y = height * (1f - normalizedY)

            if (index == 0) {
                strokePath.moveTo(x, y)
                fillPath.moveTo(x, height)
                fillPath.lineTo(x, y)
            } else {
                val prevX = (index - 1) * stepX
                val prevValue = dataPoints[index - 1]
                val prevNormalizedY = ((prevValue - computedMin) / range).coerceIn(0f, 1f)
                val prevY = height * (1f - prevNormalizedY)

                val controlX1 = prevX + (x - prevX) / 2f
                val controlY1 = prevY
                val controlX2 = prevX + (x - prevX) / 2f
                val controlY2 = y

                strokePath.cubicTo(controlX1, controlY1, controlX2, controlY2, x, y)
                fillPath.cubicTo(controlX1, controlY1, controlX2, controlY2, x, y)
            }
        }

        fillPath.lineTo(width, height)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(fillColor, Color.Transparent),
                startY = 0f,
                endY = height
            )
        )

        drawPath(
            path = strokePath,
            color = lineColor,
            style = Stroke(width = strokeWidth.toPx())
        )
    }
}
