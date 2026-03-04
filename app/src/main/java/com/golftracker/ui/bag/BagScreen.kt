package com.golftracker.ui.bag

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.draw.shadow
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.golftracker.data.entity.Club

val CLUB_TYPES = listOf("DRIVER", "WOOD", "HYBRID", "IRON", "WEDGE", "PUTTER")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BagScreen(
    onNavigateBack: () -> Unit,
    viewModel: BagViewModel = hiltViewModel()
) {
    val clubs by viewModel.clubs.collectAsState()
    var localClubs by remember { mutableStateOf(clubs) }
    androidx.compose.runtime.LaunchedEffect(clubs) {
        localClubs = clubs
    }

    val state = rememberReorderableLazyListState(
        onMove = { from, to ->
            localClubs = localClubs.toMutableList().apply {
                add(to.index, removeAt(from.index))
            }
        },
        onDragEnd = { _, _ ->
            viewModel.updateClubOrder(localClubs)
        }
    )

    var showAddDialog by remember { mutableStateOf(false) }
    var editingClub by remember { mutableStateOf<Club?>(null) }

    // ─── Add / Edit Dialog ───────────────────────────────────────
    if (showAddDialog || editingClub != null) {
        val isEdit = editingClub != null
        val initial = editingClub
        ClubDialog(
            title = if (isEdit) "Edit Club" else "Add Club",
            initialName = initial?.name ?: "",
            initialType = initial?.type ?: CLUB_TYPES.first(),
            initialDistance = initial?.stockDistance,
            onConfirm = { name, type, distance ->
                if (isEdit && initial != null) {
                    viewModel.updateClub(initial.copy(name = name, type = type, stockDistance = distance))
                } else {
                    viewModel.addClub(name, type, distance)
                }
                showAddDialog = false
                editingClub = null
            },
            onDismiss = {
                showAddDialog = false
                editingClub = null
            }
        )
    }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("My Bag") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Club")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (clubs.isEmpty()) {
                Text(
                    "No clubs in your bag yet. Tap + to add one.",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                LazyColumn(
                    state = state.listState,
                    modifier = Modifier.reorderable(state)
                ) {
                    itemsIndexed(items = localClubs, key = { _, club -> club.id }) { index, club ->
                        ReorderableItem(state, key = club.id) { isDragging ->
                            val elevation = if (isDragging) 8.dp else 0.dp
                            ClubItem(
                                club = club,
                                modifier = Modifier.shadow(elevation),
                                dragModifier = Modifier.detectReorderAfterLongPress(state),
                                onEdit = { editingClub = club },
                                onDelete = { viewModel.deleteClub(club) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Club List Item ──────────────────────────────────────────────────

@Composable
fun ClubItem(
    club: Club,
    modifier: Modifier = Modifier,
    dragModifier: Modifier = Modifier,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        ListItem(
            headlineContent = { Text(club.name) },
            supportingContent = {
                val distText = club.stockDistance?.let { " · ${it} yds" } ?: ""
                Text("${club.type}$distText")
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Drag handle
                    Icon(
                        Icons.Default.DragHandle,
                        contentDescription = "Reorder",
                        modifier = dragModifier.padding(end = 8.dp)
                    )
                    // Ellipsis menu
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit") },
                                onClick = {
                                    showMenu = false
                                    onEdit()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }
        )
    }
}

// ─── Add / Edit Club Dialog ──────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClubDialog(
    title: String,
    initialName: String,
    initialType: String,
    initialDistance: Int?,
    onConfirm: (name: String, type: String, distance: Int?) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var type by remember { mutableStateOf(initialType) }
    var distanceText by remember { mutableStateOf(initialDistance?.toString() ?: "") }
    var typeExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Club name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Club Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Club type dropdown
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = type,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        CLUB_TYPES.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    type = option
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }

                // Stock distance
                OutlinedTextField(
                    value = distanceText,
                    onValueChange = { distanceText = it.filter { c -> c.isDigit() } },
                    label = { Text("Stock Distance (yds)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name, type, distanceText.toIntOrNull())
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
