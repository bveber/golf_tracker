package com.golftracker.ui.course

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.golftracker.data.entity.Hole
import com.golftracker.data.entity.TeeSet
import com.golftracker.ui.components.NumberStepper
import com.golftracker.util.UsStates

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseEditScreen(
    onNavigateBack: () -> Unit,
    viewModel: CourseEditViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.name.isEmpty()) "New Course" else uiState.name) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.validateAndSave() }) {
                        Icon(Icons.Filled.Save, contentDescription = "Save")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(16.dp)
        ) {
            item {
                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = { viewModel.updateName(it) },
                    label = { Text("Course Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = uiState.city,
                        onValueChange = { viewModel.updateCity(it) },
                        label = { Text("City") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.padding(4.dp))
                    
                    // State dropdown
                    StateDropdown(
                        selectedState = uiState.state,
                        onStateSelected = { viewModel.updateState(it) },
                        modifier = Modifier.weight(0.6f)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                Text("Holes", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = uiState.holeCount == 9,
                        onClick = { if (uiState.holeCount != 9) viewModel.toggleHoleCount() },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text("9 Holes")
                    }
                    SegmentedButton(
                        selected = uiState.holeCount == 18,
                        onClick = { if (uiState.holeCount != 18) viewModel.toggleHoleCount() },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text("18 Holes")
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("Tee Sets", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.addTeeSet() }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Tee Set")
                    }
                }
            }

            itemsIndexed(uiState.teeSets) { index, teeSet ->
                TeeSetItem(
                    teeSet = teeSet,
                    totalDistance = uiState.getTotalDistanceForTeeSet(index),
                    onUpdate = { updatedSet -> viewModel.updateTeeSet(index, updatedSet) },
                    onDelete = { viewModel.removeTeeSet(index) }
                )
            }
            
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text("Hole Details", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            itemsIndexed(uiState.holes) { holeIndex, hole ->
                HoleEditor(
                    hole = hole,
                    holeIndex = holeIndex,
                    teeSets = uiState.teeSets,
                    yardages = uiState.yardages,
                    availableHandicaps = viewModel.getAvailableHandicaps(holeIndex),
                    onUpdateHole = { updatedHole -> viewModel.updateHole(holeIndex, updatedHole) },
                    onUpdateYardage = { teeSetIndex, yardage -> viewModel.updateYardage(holeIndex, teeSetIndex, yardage) }
                )
            }
        }
    }

    if (uiState.duplicateHandicaps.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissHandicapWarning() },
            title = { Text("Duplicate Handicaps") },
            text = { Text("The following handicap indices are assigned to multiple holes:\n${uiState.duplicateHandicaps.joinToString(", ")}\n\nAre you sure you want to save?") },
            confirmButton = {
                TextButton(onClick = { viewModel.proceedWithSave() }) {
                    Text("Save Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissHandicapWarning() }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ─── State Dropdown ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StateDropdown(
    selectedState: String,
    onStateSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf(selectedState) }

    // Keep search text in sync with external changes
    LaunchedEffect(selectedState) { searchText = selectedState }

    val filteredStates = remember(searchText) {
        if (searchText.isBlank()) UsStates.list
        else UsStates.list.filter { it.startsWith(searchText.uppercase()) }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = searchText,
            onValueChange = {
                searchText = it.uppercase().take(2)
                expanded = true
            },
            label = { Text("State") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth(),
            singleLine = true
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            filteredStates.forEach { state ->
                DropdownMenuItem(
                    text = { Text(state) },
                    onClick = {
                        onStateSelected(state)
                        searchText = state
                        expanded = false
                    }
                )
            }
        }
    }
}

// ─── Tee Set Item ────────────────────────────────────────────────────

@Composable
fun TeeSetItem(
    teeSet: TeeSet,
    totalDistance: Int,
    onUpdate: (TeeSet) -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = teeSet.name,
                    onValueChange = { onUpdate(teeSet.copy(name = it)) },
                    label = { Text("Name") },
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove")
                }
            }
            Row(modifier = Modifier.padding(top = 4.dp)) {
                OutlinedTextField(
                    value = if (totalDistance == 0) "0" else totalDistance.toString(),
                    onValueChange = {},
                    label = { Text("Total Yds") },
                    modifier = Modifier.weight(1f),
                    readOnly = true,
                    enabled = false
                )
                Spacer(modifier = Modifier.padding(2.dp))
                var slopeText by remember(teeSet.id) { mutableStateOf(if (teeSet.slope == 0) "" else teeSet.slope.toString()) }
                LaunchedEffect(teeSet.slope) {
                    if (teeSet.slope != (slopeText.toIntOrNull() ?: 0)) {
                        slopeText = if (teeSet.slope == 0) "" else teeSet.slope.toString()
                    }
                }
                OutlinedTextField(
                    value = slopeText,
                    onValueChange = { 
                        slopeText = it.filter { char -> char.isDigit() }
                        onUpdate(teeSet.copy(slope = slopeText.toIntOrNull() ?: 0))
                    },
                    label = { Text("Slope") },
                    modifier = Modifier.weight(0.8f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(modifier = Modifier.padding(2.dp))
                var ratingText by remember(teeSet.id) { mutableStateOf(if (teeSet.rating == 0.0) "" else teeSet.rating.toString()) }
                LaunchedEffect(teeSet.rating) {
                    if (teeSet.rating != (ratingText.toDoubleOrNull() ?: 0.0)) {
                        ratingText = if (teeSet.rating == 0.0) "" else ratingText
                    }
                }
                OutlinedTextField(
                    value = ratingText,
                    onValueChange = { 
                        ratingText = it.filter { char -> char.isDigit() || char == '.' }
                        onUpdate(teeSet.copy(rating = ratingText.toDoubleOrNull() ?: 0.0))
                    },
                    label = { Text("Rating") },
                    modifier = Modifier.weight(0.8f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
        }
    }
}

// ─── Hole Editor ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HoleEditor(
    hole: Hole,
    holeIndex: Int,
    teeSets: List<TeeSet>,
    yardages: Map<String, Int>,
    availableHandicaps: List<Int>,
    onUpdateHole: (Hole) -> Unit,
    onUpdateYardage: (teeSetIndex: Int, yardage: Int) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text("Hole ${hole.holeNumber}", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Par — NumberStepper (3-5, default 4)
                Column(modifier = Modifier.weight(1f)) {
                    Text("Par", style = MaterialTheme.typography.labelSmall)
                    NumberStepper(
                        value = hole.par,
                        onValueChange = { onUpdateHole(hole.copy(par = it)) },
                        range = 3..5
                    )
                }
                
                // Handicap — dropdown
                HandicapDropdown(
                    currentHandicap = hole.handicapIndex,
                    availableHandicaps = availableHandicaps,
                    onHandicapSelected = { onUpdateHole(hole.copy(handicapIndex = it)) },
                    modifier = Modifier.weight(0.7f)
                )
            }
            
            // Yardages per tee set
            teeSets.forEachIndexed { teeSetIndex, teeSet ->
                val yardageKey = "${holeIndex}_${teeSetIndex}"
                val currentYardage = yardages[yardageKey] ?: 0
                Row(modifier = Modifier.padding(top = 4.dp)) {
                    Text(
                        text = "${teeSet.name}:", 
                        modifier = Modifier.align(Alignment.CenterVertically).width(80.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                    var yardageText by remember(hole.id, teeSet.id) { mutableStateOf(if (currentYardage == 0) "" else currentYardage.toString()) }
                    LaunchedEffect(currentYardage) {
                        if (currentYardage != (yardageText.toIntOrNull() ?: 0)) {
                            yardageText = if (currentYardage == 0) "" else currentYardage.toString()
                        }
                    }
                    OutlinedTextField(
                        value = yardageText,
                        onValueChange = { 
                            yardageText = it.filter { char -> char.isDigit() }
                            onUpdateYardage(teeSetIndex, yardageText.toIntOrNull() ?: 0)
                        },
                        label = { Text("Yds") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }
        }
    }
}

// ─── Handicap Dropdown ───────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandicapDropdown(
    currentHandicap: Int?,
    availableHandicaps: List<Int>,
    onHandicapSelected: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    // Build the display list: current value (if set) + available unassigned values
    val displayOptions = buildList {
        if (currentHandicap != null && currentHandicap !in availableHandicaps) {
            add(currentHandicap) // Always show current assignment
        }
        addAll(availableHandicaps)
    }.sorted()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = currentHandicap?.toString() ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("HCP") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            singleLine = true
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            // Option to clear
            DropdownMenuItem(
                text = { Text("—") },
                onClick = {
                    onHandicapSelected(null)
                    expanded = false
                }
            )
            displayOptions.forEach { hcp ->
                DropdownMenuItem(
                    text = { Text("$hcp") },
                    onClick = {
                        onHandicapSelected(hcp)
                        expanded = false
                    }
                )
            }
        }
    }
}
