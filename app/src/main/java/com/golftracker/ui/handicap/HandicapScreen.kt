package com.golftracker.ui.handicap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import com.golftracker.util.HandicapCalculator
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandicapScreen(
    onNavigateBack: () -> Unit,
    viewModel: HandicapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Handicap") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Handicap Index Display
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Current Index", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val displayIndex = uiState.handicapIndex ?: uiState.estimatedHandicap
                    val indexText = displayIndex?.toString() ?: "N/A"
                    val isEstimated = uiState.handicapIndex == null && uiState.estimatedHandicap != null

                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = indexText,
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (isEstimated) {
                        Text("Estimated Handicap", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    
                    if (uiState.handicapIndex == null) {
                        Text("Need at least 3 rounds for official index", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (uiState.handicapIndex == null) {
                Spacer(modifier = Modifier.height(16.dp))
                EstimatedHandicapInput(
                    currentValue = uiState.estimatedHandicap,
                    onSave = { viewModel.saveEstimatedHandicap(it) }
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                "Recent Differentials",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            // List of differentials
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(uiState.differentials) { diff ->
                    DifferentialItem(diff)
                    HorizontalDivider()
                }
                if (uiState.differentials.isEmpty()) {
                    item {
                        Text("No finalized rounds yet.", modifier = Modifier.padding(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun EstimatedHandicapInput(
    currentValue: Double?,
    onSave: (Double?) -> Unit
) {
    var textValue by remember(currentValue) { mutableStateOf(currentValue?.toString() ?: "") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Set Estimated Handicap", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("e.g. 18.0") },
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Button(
                    onClick = {
                        val parsed = textValue.toDoubleOrNull()
                        onSave(parsed)
                    }
                ) {
                    Text("Save")
                }
            }
        }
    }
}

@Composable
fun DifferentialItem(diff: HandicapCalculator.Differential) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val holesLabel = if (diff.totalHoles == 9) " (9 holes)" else ""
        Text(
            text = "${dateFormat.format(diff.date)}$holesLabel",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = diff.value.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}
