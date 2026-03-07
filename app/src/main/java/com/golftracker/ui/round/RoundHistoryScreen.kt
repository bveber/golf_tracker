package com.golftracker.ui.round

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.golftracker.data.entity.Round
import java.text.SimpleDateFormat
import java.util.Locale

import android.content.Intent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoundHistoryScreen(
    onNavigateBack: () -> Unit,
    onRoundClick: (Int) -> Unit,
    viewModel: RoundHistoryViewModel = hiltViewModel()
) {
    val roundsWithDetails by viewModel.roundsWithDetails.collectAsState()
    val courseNames by viewModel.courseNames.collectAsState()
    val dateFormat = SimpleDateFormat("MMM dd, yyyy - HH:mm", Locale.getDefault())
    val context = LocalContext.current
    var roundToDelete by remember { mutableStateOf<Round?>(null) }

    LaunchedEffect(Unit) {
        viewModel.exportFileEvent.collect { file ->
            if (file != null) {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share Round JSON"))
            }
        }
    }

    // Delete confirmation dialog
    roundToDelete?.let { round ->
        AlertDialog(
            onDismissRequest = { roundToDelete = null },
            title = { Text("Delete Round?") },
            text = {
                Text(
                    "Delete the round at ${courseNames[round.courseId] ?: "Unknown Course"} " +
                    "on ${dateFormat.format(round.date)}? This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteRound(round.id)
                        roundToDelete = null
                    }
                ) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { roundToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Round History") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (roundsWithDetails.isEmpty()) {
                Text(
                    text = "No rounds played yet.",
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn {
                    items(items = roundsWithDetails, key = { it.roundWithDetails.round.id }) { item ->
                        val roundDetail = item.roundWithDetails
                        val roundScore = item.scoreData
                        val scoreString = if (roundScore.score > 0) {
                            val sign = if (roundScore.toPar > 0) "+" else ""
                            "Score: ${roundScore.score} ($sign${roundScore.toPar})"
                        } else {
                            "Score: -"
                        }
                        val sgString = if (roundScore.score > 0) {
                            val sgSign = if (roundScore.totalSg > 0) "+" else ""
                            "SG: $sgSign${String.format(java.util.Locale.US, "%.2f", roundScore.totalSg)}"
                        } else {
                            ""
                        }

                        RoundItem(
                            round = roundDetail.round,
                            courseName = roundDetail.course.name,
                            dateFormat = dateFormat,
                            scoreDisplay = scoreString,
                            sgDisplay = sgString,
                            teeName = roundScore.teeName,
                            distance = roundScore.totalDistance,
                            rating = roundScore.rating,
                            slope = roundScore.slope,
                            onClick = { onRoundClick(roundDetail.round.id) },
                            onExportClick = { viewModel.exportRound(roundDetail.round.id) },
                            onDeleteClick = { roundToDelete = roundDetail.round }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RoundItem(
    round: Round,
    courseName: String,
    dateFormat: SimpleDateFormat,
    scoreDisplay: String,
    sgDisplay: String,
    teeName: String,
    distance: Int,
    rating: Double,
    slope: Int,
    onClick: () -> Unit,
    onExportClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(modifier = Modifier.padding(8.dp).fillMaxWidth().clickable(onClick = onClick)) {
        ListItem(
            headlineContent = { Text(courseName, fontWeight = FontWeight.Bold) },
            supportingContent = { 
                Column {
                    Text(dateFormat.format(round.date))
                    Text(
                        text = "$teeName • $distance yds • $rating/$slope",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row {
                        Text(scoreDisplay, fontWeight = FontWeight.Bold)
                        if (sgDisplay.isNotEmpty()) {
                            Text(" • ", fontWeight = FontWeight.Bold)
                            val sgColor = if (sgDisplay.contains("+")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            Text(sgDisplay, fontWeight = FontWeight.Bold, color = sgColor)
                        }
                    }
                    val status = if (round.isFinalized) "Finalized" else "In Progress"
                    Text(status, color = if (round.isFinalized) Color.Green else Color.Gray, style = MaterialTheme.typography.labelSmall)
                }
            },
            trailingContent = {
                Row {
                    IconButton(onClick = onExportClick) {
                        Icon(Icons.Default.Share, contentDescription = "Export JSON")
                    }
                    IconButton(onClick = onDeleteClick) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Round", tint = Color.Red.copy(alpha = 0.7f))
                    }
                }
            }
        )
    }
}

