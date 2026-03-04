package com.golftracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

data class DistributionSegment(
    val label: String,
    val value: Double, // percentage 0-100
    val color: Color
)

/**
 * Horizontal stacked bar showing distribution segments.
 * Each segment is proportional to its value. Labels appear below.
 */
@Composable
fun DistributionBar(
    segments: List<DistributionSegment>,
    modifier: Modifier = Modifier
) {
    val total = segments.sumOf { it.value }
    if (total <= 0) return

    Column(modifier = modifier.fillMaxWidth()) {
        // Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .clip(RoundedCornerShape(6.dp))
        ) {
            segments.forEach { segment ->
                if (segment.value > 0) {
                    val fraction = (segment.value / total).toFloat()
                    Box(
                        modifier = Modifier
                            .weight(fraction)
                            .fillMaxHeight()
                            .background(segment.color),
                        contentAlignment = Alignment.Center
                    ) {
                        if (fraction > 0.08f) { // Only show label if segment is wide enough
                            Text(
                                text = "${segment.value.toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Legend
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        ) {
            segments.forEach { segment ->
                if (segment.value > 0) {
                    val fraction = (segment.value / total).toFloat()
                    Text(
                        text = segment.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(fraction)
                    )
                }
            }
        }
    }
}
