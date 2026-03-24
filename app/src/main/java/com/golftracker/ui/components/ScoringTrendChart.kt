package com.golftracker.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.golftracker.data.repository.RoundScoreSummary
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun ScoringTrendChart(
    trendData: List<RoundScoreSummary>,
    modifier: Modifier = Modifier
) {
    if (trendData.isEmpty()) return

    var selectedSummary by remember(trendData) { mutableStateOf<RoundScoreSummary?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // Pre-compute scale values outside Canvas so the tap handler can reuse them
    val toPars = trendData.map { it.toPar.toDouble() }
    val differentials = trendData.mapNotNull { it.differential }
    val allValues = toPars + differentials
    val maxVal = (allValues.maxOrNull() ?: 10.0).coerceAtLeast(1.0)
    val minVal = (allValues.minOrNull() ?: -5.0).coerceAtMost(0.0)
    val range = maxVal - minVal
    val paddedMax = maxVal + range * 0.1
    val paddedMin = minVal - range * 0.1
    val totalRange = paddedMax - paddedMin

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
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(trendData) {
                        detectTapGestures { tapOffset ->
                            val w = canvasSize.width.toFloat()
                            val h = canvasSize.height.toFloat()
                            if (w == 0f || trendData.isEmpty()) return@detectTapGestures

                            fun mapY(value: Double): Float {
                                if (totalRange == 0.0) return h / 2f
                                return h - ((value - paddedMin) / totalRange * h).toFloat()
                            }

                            val nearest = if (trendData.size == 1) {
                                trendData.first()
                            } else {
                                val xStep = w / (trendData.size - 1)
                                trendData.minByOrNull { summary ->
                                    val cx = trendData.indexOf(summary) * xStep
                                    val cy = mapY(summary.toPar.toDouble())
                                    val dx = cx - tapOffset.x
                                    val dy = cy - tapOffset.y
                                    dx * dx + dy * dy
                                }
                            }

                            selectedSummary = if (nearest != null) {
                                val cx = if (trendData.size == 1) w / 2f
                                          else trendData.indexOf(nearest) * (w / (trendData.size - 1))
                                val cy = mapY(nearest.toPar.toDouble())
                                val dx = cx - tapOffset.x
                                val dy = cy - tapOffset.y
                                // 40px tap radius
                                if (dx * dx + dy * dy <= 40f * 40f) nearest else null
                            } else null
                        }
                    }
            ) {
                val w = size.width
                val h = size.height

                fun mapY(value: Double): Float {
                    if (totalRange == 0.0) return h / 2f
                    return h - ((value - paddedMin) / totalRange * h).toFloat()
                }

                // Zero line
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
                    val isSelected = summary == selectedSummary
                    drawCircle(
                        color = Color(0xFF2196F3),
                        radius = if (isSelected) 14f else 10f,
                        center = Offset(cx, mapY(summary.toPar.toDouble()))
                    )
                    summary.differential?.let { diff ->
                        drawCircle(
                            color = Color(0xFFFF9800),
                            radius = if (isSelected) 12f else 8f,
                            center = Offset(cx, mapY(diff))
                        )
                    }
                    return@Canvas
                }

                val xStep = w / (trendData.size - 1)
                val toParPath = Path()

                trendData.forEachIndexed { index, summary ->
                    val cx = index * xStep
                    val cyToPar = mapY(summary.toPar.toDouble())
                    if (index == 0) toParPath.moveTo(cx, cyToPar) else toParPath.lineTo(cx, cyToPar)
                }

                drawPath(
                    path = toParPath,
                    color = Color(0xFF2196F3).copy(alpha = 0.6f),
                    style = Stroke(width = 6f, join = androidx.compose.ui.graphics.StrokeJoin.Round)
                )

                val textPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.GRAY
                    textSize = 28f
                    textAlign = android.graphics.Paint.Align.RIGHT
                    isAntiAlias = true
                }

                trendData.forEachIndexed { index, summary ->
                    val cx = index * xStep
                    val cyToPar = mapY(summary.toPar.toDouble())
                    val isSelected = summary == selectedSummary

                    drawCircle(
                        color = if (isSelected) Color(0xFFFFD700) else Color(0xFF2196F3),
                        radius = if (isSelected) 14f else 8f,
                        center = Offset(cx, cyToPar)
                    )
                    if (isSelected) {
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.8f),
                            radius = 14f,
                            center = Offset(cx, cyToPar),
                            style = Stroke(width = 2.5f)
                        )
                    }

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
                            color = if (isSelected) Color(0xFFFFD700) else Color(0xFFFF9800),
                            radius = if (isSelected) 12f else 8f,
                            center = Offset(cx, cyDiff)
                        )
                    }
                }

                drawContext.canvas.nativeCanvas.drawText(
                    String.format("%.1f", paddedMax), -10f, 30f, textPaint
                )
                drawContext.canvas.nativeCanvas.drawText(
                    String.format("%.1f", paddedMin), -10f, h, textPaint
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

        // Tooltip
        AnimatedVisibility(visible = selectedSummary != null) {
            selectedSummary?.let { s ->
                val dateText = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(s.date)
                val holesText = "${s.totalHoles} holes"

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(
                            text = s.courseName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "$dateText  ·  $holesText  ·  ${if (s.toPar >= 0) "+${s.toPar}" else "${s.toPar}"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
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
