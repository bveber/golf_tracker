package com.golftracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.golftracker.util.HandicapCalculator
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun HandicapTrendChart(
    points: List<HandicapCalculator.HandicapPoint>,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return

    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Handicap Index Trend",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .padding(start = 36.dp, end = 16.dp, bottom = 24.dp)
        ) {
            val dateFormat = SimpleDateFormat("MM/dd", Locale.getDefault())

            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                val values = points.map { it.handicapIndex }
                val maxVal = (values.maxOrNull() ?: 36.0) + 1.0
                val minVal = ((values.minOrNull() ?: 0.0) - 1.0).coerceAtLeast(0.0)
                val range = (maxVal - minVal).coerceAtLeast(1.0)

                fun mapY(v: Double) = h - ((v - minVal) / range * h).toFloat()
                fun mapX(i: Int) = if (points.size == 1) w / 2f else i * (w / (points.size - 1).toFloat())

                // Grid lines (3 horizontal)
                val gridPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(40, 128, 128, 128)
                    strokeWidth = 2f
                    isAntiAlias = true
                }
                listOf(0.25, 0.5, 0.75).forEach { frac ->
                    val y = (h * frac).toFloat()
                    drawContext.canvas.nativeCanvas.drawLine(0f, y, w, y, gridPaint)
                }

                // Y-axis labels
                val labelPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.GRAY
                    textSize = 24f
                    textAlign = android.graphics.Paint.Align.RIGHT
                    isAntiAlias = true
                }
                listOf(0.0, 0.5, 1.0).forEach { frac ->
                    val v = minVal + range * frac
                    val y = mapY(v)
                    drawContext.canvas.nativeCanvas.drawText(
                        String.format("%.1f", v),
                        -6f,
                        y + 8f,
                        labelPaint
                    )
                }

                // Year boundary lines
                var lastYear = -1
                val yearPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(80, 128, 128, 128)
                    strokeWidth = 3f
                    pathEffect = android.graphics.DashPathEffect(floatArrayOf(12f, 8f), 0f)
                    isAntiAlias = true
                }
                val yearLabelPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.GRAY
                    textSize = 22f
                    textAlign = android.graphics.Paint.Align.LEFT
                    isAntiAlias = true
                }
                points.forEachIndexed { i, p ->
                    if (p.year != lastYear && i > 0) {
                        val x = mapX(i)
                        drawContext.canvas.nativeCanvas.drawLine(x, 0f, x, h, yearPaint)
                        drawContext.canvas.nativeCanvas.drawText(p.year.toString(), x + 4f, 22f, yearLabelPaint)
                    }
                    lastYear = p.year
                }

                // Trend line
                if (points.size > 1) {
                    val path = Path()
                    points.forEachIndexed { i, p ->
                        val x = mapX(i)
                        val y = mapY(p.handicapIndex)
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path = path,
                        color = primaryColor.copy(alpha = 0.7f),
                        style = Stroke(width = 6f, join = StrokeJoin.Round)
                    )

                    // Shaded area under curve
                    val areaPath = Path()
                    points.forEachIndexed { i, p ->
                        val x = mapX(i)
                        val y = mapY(p.handicapIndex)
                        if (i == 0) areaPath.moveTo(x, y) else areaPath.lineTo(x, y)
                    }
                    areaPath.lineTo(mapX(points.lastIndex), h)
                    areaPath.lineTo(mapX(0), h)
                    areaPath.close()
                    drawPath(areaPath, color = primaryColor.copy(alpha = 0.08f))
                }

                // Dots and date labels
                val dotLabelPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.DKGRAY
                    textSize = 22f
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }
                points.forEachIndexed { i, p ->
                    val x = mapX(i)
                    val y = mapY(p.handicapIndex)
                    drawCircle(
                        color = primaryColor,
                        radius = 10f,
                        center = Offset(x, y)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 5f,
                        center = Offset(x, y)
                    )
                    // Date label below x-axis (every other label if crowded)
                    if (points.size <= 10 || i % 2 == 0) {
                        drawContext.canvas.nativeCanvas.drawText(
                            dateFormat.format(p.date),
                            x,
                            h + 22f,
                            dotLabelPaint
                        )
                    }
                }
            }
        }
    }
}
