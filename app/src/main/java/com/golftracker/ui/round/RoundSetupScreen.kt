package com.golftracker.ui.round

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.golftracker.data.entity.Course
import com.golftracker.data.entity.TeeSet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoundSetupScreen(
    onNavigateBack: () -> Unit,
    onRoundCreated: (Int) -> Unit,
    viewModel: RoundSetupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.createdRoundId) {
        val id = uiState.createdRoundId
        if (id != null) {
            onRoundCreated(id)
            viewModel.resetCreatedRoundId() // Prevent re-navigation
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Round") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Course Selection
            var courseDropdownExpanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = uiState.selectedCourse?.name ?: "",
                    onValueChange = {},
                    label = { Text("Select Course") },
                    readOnly = true,
                    trailingIcon = {
                        // Icon(Icons.Default.ArrowDropDown, "Drop Down")
                    },
                    modifier = Modifier.fillMaxWidth().clickable { courseDropdownExpanded = true }
                )
                // Overlay for click capture on ReadOnly textfield
                Box(modifier = Modifier.matchParentSize().clickable { courseDropdownExpanded = true })
                
                DropdownMenu(
                    expanded = courseDropdownExpanded,
                    onDismissRequest = { courseDropdownExpanded = false }
                ) {
                    uiState.courses.forEach { course ->
                        DropdownMenuItem(
                            text = { Text(course.name) },
                            onClick = {
                                viewModel.selectCourse(course)
                                courseDropdownExpanded = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Tee Set Selection (only if course selected)
            if (uiState.selectedCourse != null) {
                var teeDropdownExpanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = uiState.selectedTeeSet?.let { set -> 
                            val yds = uiState.teeYardages[set.id] ?: 0
                            val ydsStr = if (yds > 0) "$yds yds / " else ""
                            "${set.name} (${ydsStr}CR: ${set.rating} / Slope: ${set.slope})" 
                        } ?: "",
                        onValueChange = {},
                        label = { Text("Select Tee Set") },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(modifier = Modifier.matchParentSize().clickable { teeDropdownExpanded = true })

                    DropdownMenu(
                        expanded = teeDropdownExpanded,
                        onDismissRequest = { teeDropdownExpanded = false }
                    ) {
                        uiState.teeSets.forEach { teeSet ->
                            DropdownMenuItem(
                                text = { 
                                    val yds = uiState.teeYardages[teeSet.id] ?: 0
                                    val ydsStr = if (yds > 0) "$yds yds / " else ""
                                    Text("${teeSet.name} ($ydsStr${teeSet.rating}/${teeSet.slope})") 
                                },
                                onClick = {
                                    viewModel.selectTeeSet(teeSet)
                                    teeDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Holes to Play Selection
            Text("Number of Holes", style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                com.golftracker.ui.components.ChipSelector(
                    options = listOf(18, 9),
                    selectedOption = uiState.holesToPlay,
                    onOptionSelected = { viewModel.updateHolesToPlay(it) },
                    labelMapper = { "$it Holes" }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("Starting Hole", style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                com.golftracker.ui.components.ChipSelector(
                    options = listOf(1, 10),
                    selectedOption = uiState.startingHole,
                    onOptionSelected = { viewModel.updateStartingHole(it) },
                    labelMapper = { "Hole $it" }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Date Picker (simplified as text display for now)
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            OutlinedTextField(
                value = dateFormat.format(uiState.date),
                onValueChange = {},
                label = { Text("Date") },
                readOnly = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            // Notes
            OutlinedTextField(
                value = uiState.notes,
                onValueChange = { viewModel.updateNotes(it) },
                label = { Text("Notes (Optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Practice Round", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Won't count toward handicap or stats",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = uiState.isPractice,
                    onCheckedChange = { viewModel.togglePracticeRound() }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // Weather Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Weather at Course", style = MaterialTheme.typography.labelMedium)
                if (uiState.weatherLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(
                        onClick = {
                            // Manual refresh — requires course with GPS data
                            uiState.selectedCourse?.let { viewModel.retryWeatherFetch() }
                        },
                        enabled = uiState.selectedCourse != null
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh weather")
                    }
                }
            }

            val weather = uiState.weather
            if (weather != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (weather.condition != null) {
                            Text(weather.condition, style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (weather.temperatureFahrenheit != null) {
                                Text("${weather.temperatureFahrenheit}°F", style = MaterialTheme.typography.bodySmall)
                            }
                            if (weather.humidityPercent != null) {
                                Text("Humidity: ${weather.humidityPercent}%", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (weather.windSpeedMph != null) {
                                val windStr = if (weather.windDirection != null)
                                    "Wind: ${weather.windSpeedMph} mph ${weather.windDirection}"
                                else
                                    "Wind: ${weather.windSpeedMph} mph"
                                Text(windStr, style = MaterialTheme.typography.bodySmall)
                            }
                            if (weather.pressureInHg != null) {
                                Text("Pressure: ${"%.2f".format(weather.pressureInHg)} inHg", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            } else if (uiState.weatherError != null) {
                Text(
                    uiState.weatherError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else if (uiState.selectedCourse != null && !uiState.weatherLoading) {
                Text(
                    "No GPS coordinates available for this course — weather unavailable",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Hole Strategy Notes
            if (uiState.holes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                var strategyExpanded by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { strategyExpanded = !strategyExpanded }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Hole Strategy Notes", style = MaterialTheme.typography.labelMedium)
                        Text(
                            "Optional notes visible during play",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = if (strategyExpanded) "Collapse" else "Expand",
                        modifier = Modifier.rotate(if (strategyExpanded) 180f else 0f)
                    )
                }

                if (strategyExpanded) {
                    Spacer(modifier = Modifier.height(8.dp))
                    uiState.holes.forEach { hole ->
                        OutlinedTextField(
                            value = hole.strategyNotes,
                            onValueChange = { viewModel.updateHoleStrategyNotes(hole, it) },
                            label = { Text("Hole ${hole.holeNumber} (Par ${hole.par})") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            minLines = 1,
                            maxLines = 4,
                            placeholder = { Text("Strategy, hazards, targets…", style = MaterialTheme.typography.bodySmall) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.startRound() },
                enabled = uiState.selectedCourse != null && uiState.selectedTeeSet != null && !uiState.isLoading,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text(if (uiState.isLoading) "Starting..." else "Start Round")
            }
        }
    }
}
