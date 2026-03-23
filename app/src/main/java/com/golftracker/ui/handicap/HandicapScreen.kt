package com.golftracker.ui.handicap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.golftracker.ui.components.HandicapTrendChart
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
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Current Index bubble ─────────────────────────────────
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Current Index", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))

                        val displayIndex = uiState.handicapIndex ?: uiState.estimatedHandicap
                        val indexText = displayIndex?.let { String.format("%.1f", it) } ?: "N/A"
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
                            Text(
                                "Estimated Handicap",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (uiState.handicapIndex == null) {
                            Text(
                                "Need at least 3 rounds for official index",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        // Year context
                        if (uiState.availableYears.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Based on ${uiState.selectedYear} rounds",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ── Estimated handicap input (shown before official index) ─
            if (uiState.handicapIndex == null) {
                item {
                    EstimatedHandicapInput(
                        currentValue = uiState.estimatedHandicap,
                        onSave = { viewModel.saveEstimatedHandicap(it) }
                    )
                }
            }

            // ── Trend chart ──────────────────────────────────────────
            if (uiState.timeSeries.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(vertical = 16.dp)) {
                            HandicapTrendChart(
                                points = uiState.timeSeries,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            }

            // ── Differentials header ─────────────────────────────────
            item {
                Text(
                    "Recent Differentials",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ── Differential rows ────────────────────────────────────
            if (uiState.differentials.isEmpty()) {
                item {
                    Text("No finalized rounds yet.", modifier = Modifier.padding(16.dp))
                }
            } else {
                items(uiState.differentials) { diff ->
                    DifferentialItem(diff)
                    HorizontalDivider()
                }
            }

            // ── bottom spacer ────────────────────────────────────────
            item { Spacer(modifier = Modifier.height(16.dp)) }
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

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("e.g. 18.0") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { onSave(textValue.toDoubleOrNull()) }) {
                    Text("Save")
                }
            }
        }
    }
}

@Composable
fun DifferentialItem(diff: HandicapCalculator.Differential) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val holesLabel = if (diff.totalHoles == 9) " (9 holes)" else ""
            Text(
                text = "${dateFormat.format(diff.date)}$holesLabel",
                style = MaterialTheme.typography.bodyMedium
            )
            if (diff.totalHoles == 9 && diff.usedExpectedScore) {
                Text(
                    text = "Expected score used for back 9",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (diff.totalHoles == 9) {
                Text(
                    text = "Back 9 doubled (no prior handicap)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
        }
        Text(
            text = String.format("%.1f", diff.value),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}
