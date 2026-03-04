package com.golftracker.ui.round

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
            modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize()
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
                        value = uiState.selectedTeeSet?.let { "${it.name} (CR: ${it.rating} / Slope: ${it.slope})" } ?: "",
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
                                text = { Text("${teeSet.name} (${teeSet.rating}/${teeSet.slope})") },
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

            Spacer(modifier = Modifier.weight(1f))

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
