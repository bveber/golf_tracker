package com.golftracker.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun StatCard(
    title: String,
    value: String,
    moe: String? = null,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, style = MaterialTheme.typography.labelMedium)
            Text(text = value, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            if (!moe.isNullOrBlank()) {
                Text(
                    text = moe, 
                    style = MaterialTheme.typography.bodySmall, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
