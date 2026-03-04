package com.golftracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 2D shot dispersion visual showing on-target in center with directional misses around it.
 *
 * Layout: a target-style diagram with center = On Target, and directional zones for
 * Left, Right, Short, Long surrounding appropriately.
 */
@Composable
fun ShotDispersionVisual(
    onTargetPct: Double,
    missLeftPct: Double,
    missRightPct: Double,
    missShortPct: Double,
    missLongPct: Double,
    modifier: Modifier = Modifier,
    title: String = "Outcome"
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.2f)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val cx = w / 2f
                val cy = h / 2f

                // Zone dimensions
                val centerW = w * 0.36f
                val centerH = h * 0.32f
                val sideW = (w - centerW) / 2f
                val topBottomH = (h - centerH) / 2f

                // Colors
                val greenCenter = Color(0xFF4CAF50)
                val orangeLeft = Color(0xFFFF9800)
                val redRight = Color(0xFFFF5722)
                val greyShort = Color(0xFF78909C)
                val blueLong = Color(0xFF42A5F5)

                // Draw Long (top)
                drawZone(
                    left = cx - centerW / 2f,
                    top = 0f,
                    width = centerW,
                    height = topBottomH,
                    color = blueLong,
                    label = "Long",
                    pct = missLongPct
                )

                // Draw Short (bottom)
                drawZone(
                    left = cx - centerW / 2f,
                    top = cy + centerH / 2f,
                    width = centerW,
                    height = topBottomH,
                    color = greyShort,
                    label = "Short",
                    pct = missShortPct
                )

                // Draw Left (left)
                drawZone(
                    left = 0f,
                    top = cy - centerH / 2f,
                    width = sideW,
                    height = centerH,
                    color = orangeLeft,
                    label = "Left",
                    pct = missLeftPct
                )

                // Draw Right (right)
                drawZone(
                    left = cx + centerW / 2f,
                    top = cy - centerH / 2f,
                    width = sideW,
                    height = centerH,
                    color = redRight,
                    label = "Right",
                    pct = missRightPct
                )

                // Draw Center (on-target)
                drawZone(
                    left = cx - centerW / 2f,
                    top = cy - centerH / 2f,
                    width = centerW,
                    height = centerH,
                    color = greenCenter,
                    label = "On Target",
                    pct = onTargetPct,
                    isCenter = true
                )
            }
        }
    }
}

private fun DrawScope.drawZone(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    color: Color,
    label: String,
    pct: Double,
    isCenter: Boolean = false
) {
    val cornerRadius = 12f
    drawRoundRect(
        color = color.copy(alpha = if (isCenter) 0.85f else 0.55f),
        topLeft = Offset(left + 2f, top + 2f),
        size = Size(width - 4f, height - 4f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius)
    )

    val paint = android.graphics.Paint().apply {
        this.color = android.graphics.Color.WHITE
        this.textAlign = android.graphics.Paint.Align.CENTER
        this.typeface = if (isCenter) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
        this.textSize = if (isCenter) 38f else 28f
        this.isAntiAlias = true
    }

    val labelPaint = android.graphics.Paint().apply {
        this.color = android.graphics.Color.WHITE
        this.textAlign = android.graphics.Paint.Align.CENTER
        this.textSize = if (isCenter) 26f else 22f
        this.isAntiAlias = true
    }

    val centerX = left + width / 2f
    val centerY = top + height / 2f

    drawContext.canvas.nativeCanvas.drawText(
        "${String.format("%.0f", pct)}%",
        centerX,
        centerY + if (isCenter) -4f else 2f,
        paint
    )

    drawContext.canvas.nativeCanvas.drawText(
        label,
        centerX,
        centerY + (if (isCenter) 26f else 22f),
        labelPaint
    )
}
