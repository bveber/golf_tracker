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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.golftracker.data.repository.DispersionPoint
import com.golftracker.data.repository.RawDispersionData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * 2D scatter plot showing raw dispersion points relative to a center target.
 * Scale is fully dynamic — all points in the filtered dataset are always visible.
 * Tap a dot to see which hole and round it came from.
 */
@Composable
fun RawDispersionVisual(
    data: RawDispersionData,
    modifier: Modifier = Modifier,
    title: String = "Raw Dispersion (yds)",
    pointColor: Color = MaterialTheme.colorScheme.primary,
) {
    if (data.points.isEmpty()) return

    // Compute max distance using the actual rendered offsets so no point is ever clipped
    var maxDistVal = 10f
    data.points.forEach { p ->
        val xYds = abs((p.right?.toFloat() ?: 0f) - (p.left?.toFloat() ?: 0f))
        val yYds = abs((p.short?.toFloat() ?: 0f) - (p.long?.toFloat() ?: 0f))
        maxDistVal = max(maxDistVal, max(xYds, yYds))
    }

    val ringStep = when {
        maxDistVal <= 15f -> 5f
        maxDistVal <= 40f -> 10f
        maxDistVal <= 100f -> 25f
        else -> 50f
    }
    // 20% padding so the outermost points aren't flush with the edge
    val maxDist = max(maxDistVal * 1.2f, ringStep)

    var selectedPoint by remember(data.points) { mutableStateOf<DispersionPoint?>(null) }
    var canvasWidthPx by remember { mutableFloatStateOf(0f) }

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
                .aspectRatio(1f)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { canvasWidthPx = it.width.toFloat() }
                    .pointerInput(data.points, maxDist) {
                        detectTapGestures { tapOffset ->
                            val cx = canvasWidthPx / 2f
                            if (cx == 0f) return@detectTapGestures
                            val scale = cx / maxDist

                            val nearest = data.points.minByOrNull { p ->
                                val xYds = (p.right?.toFloat() ?: 0f) - (p.left?.toFloat() ?: 0f)
                                val yYds = (p.short?.toFloat() ?: 0f) - (p.long?.toFloat() ?: 0f)
                                val px = cx + xYds * scale
                                val py = cx + yYds * scale
                                val dx = px - tapOffset.x
                                val dy = py - tapOffset.y
                                dx * dx + dy * dy
                            }

                            selectedPoint = if (nearest != null) {
                                val xYds = (nearest.right?.toFloat() ?: 0f) - (nearest.left?.toFloat() ?: 0f)
                                val yYds = (nearest.short?.toFloat() ?: 0f) - (nearest.long?.toFloat() ?: 0f)
                                val px = cx + xYds * scale
                                val py = cx + yYds * scale
                                val dx = px - tapOffset.x
                                val dy = py - tapOffset.y
                                // 40px tap radius
                                if (dx * dx + dy * dy <= 40f * 40f) nearest else null
                            } else null
                        }
                    }
            ) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val scale = cx / maxDist

                val gridColor = Color.Gray.copy(alpha = 0.4f)
                val axisColor = Color.Gray.copy(alpha = 0.6f)

                // Axes
                drawLine(color = axisColor, start = Offset(0f, cy), end = Offset(size.width, cy), strokeWidth = 2f)
                drawLine(color = axisColor, start = Offset(cx, 0f), end = Offset(cx, size.height), strokeWidth = 2f)

                // Distance rings
                var currentRing = ringStep
                while (currentRing <= maxDist) {
                    drawCircle(
                        color = gridColor,
                        radius = currentRing * scale,
                        center = Offset(cx, cy),
                        style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
                    )
                    currentRing += ringStep
                }

                // Center target
                drawCircle(color = Color.Red.copy(alpha = 0.8f), radius = 8f, center = Offset(cx, cy))

                // Dispersion points
                data.points.forEach { p ->
                    val xYds = (p.right?.toFloat() ?: 0f) - (p.left?.toFloat() ?: 0f)
                    val yYds = (p.short?.toFloat() ?: 0f) - (p.long?.toFloat() ?: 0f)
                    val px = cx + xYds * scale
                    val py = cy + yYds * scale
                    val isSelected = p == selectedPoint

                    drawCircle(
                        color = if (isSelected) Color(0xFFFFD700) else pointColor.copy(alpha = 0.7f),
                        radius = if (isSelected) 16f else 12f,
                        center = Offset(px, py)
                    )
                    if (isSelected) {
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.8f),
                            radius = 16f,
                            center = Offset(px, py),
                            style = Stroke(width = 2.5f)
                        )
                    }
                }
            }
        }

        Text(
            text = "Grid rings: every ${ringStep.toInt()} yds",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )

        // Tooltip
        AnimatedVisibility(visible = selectedPoint != null) {
            selectedPoint?.let { p ->
                val dateText = p.roundDate?.let {
                    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(it))
                } ?: "Unknown date"
                val holeText = "Hole ${p.holeNumber ?: "?"}"

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        if (p.courseName != null) {
                            Text(
                                text = p.courseName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "$holeText  ·  $dateText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
