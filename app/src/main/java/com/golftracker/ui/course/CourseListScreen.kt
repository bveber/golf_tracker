package com.golftracker.ui.course

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseListScreen(
    onAddCourse: () -> Unit,
    onImportCourse: () -> Unit,
    onCourseClick: (Int) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: CourseViewModel = hiltViewModel()
) {
    val courses by viewModel.allCourses.collectAsState()
    var courseToDelete by remember { mutableStateOf<Course?>(null) }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Courses") },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    androidx.compose.material3.IconButton(onClick = onImportCourse) {
                        Icon(Icons.Default.CloudDownload, contentDescription = "Import Course")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddCourse) {
                Icon(Icons.Default.Add, contentDescription = "Add Course")
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (courses.isEmpty()) {
                Text(
                    text = "No courses yet. Add one!",
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn {
                    items(items = courses, key = { it.id }) { course ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = {
                                if (it == SwipeToDismissBoxValue.EndToStart) {
                                    courseToDelete = course
                                }
                                false
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                Box(
                                    modifier = Modifier.fillMaxSize().padding(16.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Text("Delete")
                                }
                            },
                            content = {
                                CourseItem(course = course, onClick = { onCourseClick(course.id) })
                            }
                        )
                    }
                }
            }
        }

        if (courseToDelete != null) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { courseToDelete = null },
                title = { Text("Delete Course") },
                text = { Text("Are you sure you want to delete ${courseToDelete?.name}?") },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            courseToDelete?.let { viewModel.deleteCourse(it) }
                            courseToDelete = null
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { courseToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun CourseItem(course: Course, onClick: () -> Unit) {
    Card(modifier = Modifier.padding(8.dp).clickable(onClick = onClick)) {
        ListItem(
            headlineContent = { Text(course.name) },
            supportingContent = { Text("${course.city}, ${course.state} • ${course.holeCount} holes") }
        )
    }
}
