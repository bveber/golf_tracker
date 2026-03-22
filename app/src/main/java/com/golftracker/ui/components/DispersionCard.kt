package com.golftracker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.abs

@Composable
fun DispersionCard(
    title: String,
    avgLateral: Double,
    avgDistance: Double,
    unit: String = "yds",
    modifier: Modifier = Modifier
) {
    val lateralText = when {
        avgLateral < -0.05 -> String.format("%.1f %s L", abs(avgLateral), unit)
        avgLateral > 0.05 -> String.format("%.1f %s R", avgLateral, unit)
        else -> String.format("0.0 %s", unit)
    }
    
    val distanceText = when {
        avgDistance < -0.05 -> String.format("%.1f %s S", abs(avgDistance), unit)
        avgDistance > 0.05 -> String.format("%.1f %s Long", avgDistance, unit)
        else -> String.format("0.0 %s", unit)
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Lateral Miss", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = lateralText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Distance Miss", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = distanceText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
