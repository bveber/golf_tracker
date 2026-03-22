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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.golftracker.data.repository.RawDispersionData
import kotlin.math.max

/**
 * 2D scatter plot showing raw dispersion points relative to a center target.
 */
@Composable
fun RawDispersionVisual(
    data: RawDispersionData,
    modifier: Modifier = Modifier,
    title: String = "Raw Dispersion (yds)",
    pointColor: Color = MaterialTheme.colorScheme.primary,
    ringStepOverride: Float? = null
) {
    if (data.points.isEmpty()) return

    // 1. Calculate the scale parameters outside the Canvas so we can use them in the legend
    var maxDistVal = 10f
    data.points.forEach { p ->
        val xDist = max(p.left?.toFloat() ?: 0f, p.right?.toFloat() ?: 0f)
        val yDist = max(p.short?.toFloat() ?: 0f, p.long?.toFloat() ?: 0f)
        maxDistVal = max(maxDistVal, max(xDist, yDist))
    }
    
    val ringStep = ringStepOverride ?: when {
        maxDistVal <= 15f -> 5f
        maxDistVal <= 40f -> 10f
        maxDistVal <= 100f -> 25f
        else -> 50f
    }

    // Add 20% padding to the max distance for the grid boundary
    // Ensure maxDist is at least one ringStep so at least one ring is always visible
    val maxDist = max(maxDistVal * 1.2f, ringStep)
    
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
                .aspectRatio(1f) // Square aspect ratio is best for X/Y scatter plots
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                
                // Scale factor: pixels per yard
                val scale = (size.width / 2f) / maxDist

                val gridColor = Color.Gray.copy(alpha = 0.4f)
                val axisColor = Color.Gray.copy(alpha = 0.6f)
                
                // Draw axes
                drawLine(
                    color = axisColor,
                    start = Offset(0f, cy),
                    end = Offset(size.width, cy),
                    strokeWidth = 2f
                )
                drawLine(
                    color = axisColor,
                    start = Offset(cx, 0f),
                    end = Offset(cx, size.height),
                    strokeWidth = 2f
                )

                // Draw distance rings
                var currentRing = ringStep
                while (currentRing <= maxDist) {
                    val radiusPx = currentRing * scale
                    drawCircle(
                        color = gridColor,
                        radius = radiusPx,
                        center = Offset(cx, cy),
                        style = Stroke(
                            width = 2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        )
                    )
                    currentRing += ringStep
                }
                
                // Draw Target (Center)
                drawCircle(
                    color = Color.Red.copy(alpha = 0.8f),
                    radius = 8f,
                    center = Offset(cx, cy)
                )

                // Draw Dispersion Points
                data.points.forEach { p ->
                    // Calculate relative distances: Left is -X, Right is +X. Short is +Y (down), Long is -Y (up)
                    val xYds = (p.right?.toFloat() ?: 0f) - (p.left?.toFloat() ?: 0f)
                    val yYds = (p.short?.toFloat() ?: 0f) - (p.long?.toFloat() ?: 0f)
                    
                    val px = cx + (xYds * scale)
                    val py = cy + (yYds * scale)
                    
                    drawCircle(
                        color = pointColor.copy(alpha = 0.7f),
                        radius = 12f,
                        center = Offset(px, py)
                    )
                }
            }
        }
        
        Text(
            text = "Grid Rings: every ${ringStep.toInt()} yards",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
