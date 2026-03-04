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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.golftracker.data.repository.RoundScoreSummary

@Composable
fun ScoringTrendChart(
    trendData: List<RoundScoreSummary>,
    modifier: Modifier = Modifier
) {
    if (trendData.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Scoring Trend",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(horizontal = 24.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Determine min/max values for scaling
                val toPars = trendData.map { it.toPar.toDouble() }
                val differentials = trendData.mapNotNull { it.differential }
                
                val allValues = toPars + differentials
                val maxVal = (allValues.maxOrNull() ?: 10.0).coerceAtLeast(1.0)
                val minVal = (allValues.minOrNull() ?: -5.0).coerceAtMost(0.0)
                
                // Add some padding to top and bottom
                val range = maxVal - minVal
                val paddedMax = maxVal + range * 0.1
                val paddedMin = minVal - range * 0.1
                val totalRange = paddedMax - paddedMin
                
                // Helper to map a value to Y coordinate
                fun mapY(value: Double): Float {
                    if (totalRange == 0.0) return h / 2f
                    return h - ((value - paddedMin) / totalRange * h).toFloat()
                }

                // Draw zero line
                val zeroY = mapY(0.0)
                drawLine(
                    color = Color.Gray.copy(alpha = 0.5f),
                    start = Offset(0f, zeroY),
                    end = Offset(w, zeroY),
                    strokeWidth = 2f
                )

                if (trendData.size == 1) {
                    val summary = trendData.first()
                    val cx = w / 2f
                    // Draw single toPar point
                    drawCircle(
                        color = Color(0xFF2196F3),
                        radius = 10f,
                        center = Offset(cx, mapY(summary.toPar.toDouble()))
                    )
                    // Draw single differential point
                    summary.differential?.let { diff ->
                        drawCircle(
                            color = Color(0xFFFF9800),
                            radius = 8f,
                            center = Offset(cx, mapY(diff))
                        )
                    }
                    return@Canvas
                }

                // Calculate X positions
                val xStep = w / (trendData.size - 1)
                val toParPath = Path()
                
                trendData.forEachIndexed { index, summary ->
                    val cx = index * xStep
                    val cyToPar = mapY(summary.toPar.toDouble())
                    
                    if (index == 0) {
                        toParPath.moveTo(cx, cyToPar)
                    } else {
                        toParPath.lineTo(cx, cyToPar)
                    }
                }

                // Draw toPar line
                drawPath(
                    path = toParPath,
                    color = Color(0xFF2196F3).copy(alpha = 0.6f),
                    style = Stroke(width = 6f, join = androidx.compose.ui.graphics.StrokeJoin.Round)
                )

                // Draw points and differentials
                val textPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.GRAY
                    textSize = 28f
                    textAlign = android.graphics.Paint.Align.RIGHT
                    isAntiAlias = true
                }

                trendData.forEachIndexed { index, summary ->
                    val cx = index * xStep
                    val cyToPar = mapY(summary.toPar.toDouble())
                    
                    // To Par Dot
                    drawCircle(
                        color = Color(0xFF2196F3),
                        radius = 8f,
                        center = Offset(cx, cyToPar)
                    )
                    
                    // Differential Dot
                    summary.differential?.let { diff ->
                        val cyDiff = mapY(diff)
                        
                        drawLine(
                            color = Color.Gray.copy(alpha = 0.4f),
                            start = Offset(cx, cyToPar),
                            end = Offset(cx, cyDiff),
                            strokeWidth = 3f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        )

                        drawCircle(
                            color = Color(0xFFFF9800),
                            radius = 8f,
                            center = Offset(cx, cyDiff)
                        )
                    }
                }
                
                // Draw Min/Max Labels
                drawContext.canvas.nativeCanvas.drawText(
                    String.format("%.1f", paddedMax),
                    -10f,
                    30f,
                    textPaint
                )
                drawContext.canvas.nativeCanvas.drawText(
                    String.format("%.1f", paddedMin),
                    -10f,
                    h,
                    textPaint
                )
            }
        }
        
        // Legend
        Row(
            modifier = Modifier.padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LegendItem("To Par", Color(0xFF2196F3))
            LegendItem("Differential", Color(0xFFFF9800))
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(12.dp)) {
            drawCircle(color = color)
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}
