package com.golftracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun <T> ChipSelector(
    options: List<T>,
    selectedOption: T?,
    onOptionSelected: (T) -> Unit,
    labelMapper: (T) -> String = { it.toString() },
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp) // Added vertical spacing
    ) {
        options.forEach { option ->
            val isSelected = option == selectedOption
            FilterChip(
                selected = isSelected,
                onClick = { onOptionSelected(option) },
                label = { Text(labelMapper(option)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}
