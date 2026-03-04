package com.golftracker.ui.course

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.golftracker.data.api.model.NetworkCourseSummary
import com.golftracker.data.api.model.NetworkScorecard
import com.golftracker.data.api.model.NetworkCourseDetails

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseImportScreen(
    onNavigateBack: () -> Unit,
    viewModel: CourseImportViewModel = hiltViewModel()
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val importStatus by viewModel.importStatus.collectAsState()

    LaunchedEffect(importStatus) {
        if (importStatus is ImportStatus.Success) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import Course") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                label = { Text("Course Name or Location") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { viewModel.searchCourses() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && searchQuery.isNotBlank()
            ) {
                Text(if (isLoading) "Searching..." else "Search")
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(searchResults) { course ->
                        CourseResultItem(
                            course = course,
                            isImporting = importStatus is ImportStatus.Importing,
                            onImportClick = { viewModel.fetchCourseTees(course.id) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (importStatus is ImportStatus.Error) {
        AlertDialog(
            onDismissRequest = { viewModel.resetImportStatus() },
            title = { Text("Import Error") },
            text = { Text((importStatus as ImportStatus.Error).message) },
            confirmButton = {
                TextButton(onClick = { viewModel.resetImportStatus() }) {
                    Text("OK")
                }
            }
        )
    }

    if (importStatus is ImportStatus.TeeSelection) {
        val selectionState = importStatus as ImportStatus.TeeSelection
        TeeSelectionDialog(
            courseDetails = selectionState.courseDetails,
            availableTees = selectionState.availableTees,
            onConfirm = { selected -> viewModel.confirmImport(selectionState.courseDetails, selected) },
            onDismiss = { viewModel.resetImportStatus() }
        )
    }
}

@Composable
fun CourseResultItem(
    course: NetworkCourseSummary,
    isImporting: Boolean,
    onImportClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = course.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = listOfNotNull(course.city, course.state).joinToString(", ") + 
                       if (course.holes != null) " • ${course.holes} holes" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Button(
            onClick = onImportClick,
            enabled = !isImporting
        ) {
            Text("Import")
        }
    }
}

@Composable
fun TeeSelectionDialog(
    courseDetails: NetworkCourseDetails,
    availableTees: List<NetworkScorecard>,
    onConfirm: (List<NetworkScorecard>) -> Unit,
    onDismiss: () -> Unit
) {
    // Keep track of which tees are selected
    val selectedTees = remember { mutableStateListOf<NetworkScorecard>().apply { addAll(availableTees) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Tees to Import") },
        text = {
            Column {
                Text("Select the tee boxes you want to import for ${courseDetails.name}:")
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = {
                        selectedTees.clear()
                        selectedTees.addAll(availableTees)
                    }) {
                        Text("Select All")
                    }
                    TextButton(onClick = {
                        selectedTees.clear()
                    }) {
                        Text("Deselect All")
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn {
                    items(availableTees) { tee ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = selectedTees.contains(tee),
                                onCheckedChange = { isChecked ->
                                    if (isChecked) {
                                        selectedTees.add(tee)
                                    } else {
                                        selectedTees.remove(tee)
                                    }
                                }
                            )
                            Text(text = tee.teeName, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedTees) },
                enabled = selectedTees.isNotEmpty()
            ) {
                Text("Import Selected")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
