package com.golftracker.ui.round

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.golftracker.data.entity.Hole
import com.golftracker.data.entity.HoleStat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoundSummaryScreen(
    onNavigateBack: () -> Unit,
    onNavigateToHole: (Int) -> Unit, // Navigate to specific hole index
    onFinishRound: () -> Unit,
    viewModel: RoundViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val holes = uiState.holes
    val stats = uiState.holeStats

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scorecard") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            if (!uiState.isRoundFinalized) {
                Button(
                    onClick = {
                        viewModel.finalizeRound()
                        onFinishRound()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text("Finalize Round")
                }
            } else {
                 Button(
                    onClick = {
                        // Edit round starting from 1st hole (index 0)
                        onNavigateToHole(0)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text("Edit Round (Go to Hole 1)")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            if (holes.isNotEmpty() && stats.isNotEmpty()) {
                ScorecardTable(
                    holes = holes,
                    stats = stats,
                    onHoleClick = onNavigateToHole
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading scorecard...")
                }
            }
        }
    }
}

@Composable
fun ScorecardTable(
    holes: List<Hole>,
    stats: List<HoleStat>,
    onHoleClick: (Int) -> Unit
) {
    // Basic Table Implementation using Row/Column
    // Header
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp)
    ) {
        Text("Hole", modifier = Modifier.width(40.dp), fontWeight = FontWeight.Bold)
        Text("Par", modifier = Modifier.width(40.dp), fontWeight = FontWeight.Bold)
        Text("Score", modifier = Modifier.width(50.dp), fontWeight = FontWeight.Bold)
        Text("Putts", modifier = Modifier.width(50.dp), fontWeight = FontWeight.Bold)
        Text("SG", modifier = Modifier.width(50.dp), fontWeight = FontWeight.Bold)
        Text("GIR", modifier = Modifier.width(40.dp), fontWeight = FontWeight.Bold)
    }

    // Rows
    var totalPar = 0
    var totalScore = 0
    var totalPutts = 0

    holes.forEachIndexed { index, hole ->
        val stat = stats.find { it.holeId == hole.id }
        val score = stat?.score ?: 0
        val putts = stat?.putts ?: 0
        val isGir = stat?.let { it.girOverride ?: com.golftracker.util.GirCalculator.isGir(score, hole.par, putts) } ?: false
        
        if (score > 0) {
            totalPar += hole.par
            totalScore += score
            totalPutts += putts
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onHoleClick(index) }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(hole.holeNumber.toString(), modifier = Modifier.width(40.dp))
            Text(hole.par.toString(), modifier = Modifier.width(40.dp))
            
            // Score with color logic
            val scoreColor = when {
                score == 0 -> Color.Gray // Not played
                score < hole.par -> Color(0xFF4CAF50) // Birdie or better (Green)
                score > hole.par -> Color(0xFFE57373) // Bogey or worse (Red)
                else -> Color.Unspecified // Par
            }
            Text(
                text = if (score > 0) score.toString() else "-",
                modifier = Modifier.width(50.dp),
                fontWeight = FontWeight.Bold,
                color = scoreColor
            )

            Text(if (putts > 0) putts.toString() else "-", modifier = Modifier.width(50.dp))
            
            val sg = stat?.strokesGained ?: 0.0
            val sgColor = when {
                sg > 0.1 -> MaterialTheme.colorScheme.primary
                sg < -0.1 -> MaterialTheme.colorScheme.error
                else -> Color.Unspecified
            }
            Text(
                text = String.format(java.util.Locale.US, "%.1f", sg),
                modifier = Modifier.width(50.dp),
                color = sgColor,
                fontWeight = FontWeight.Medium
            )
            
            if (isGir) {
                Icon(Icons.Filled.Check, contentDescription = "GIR", tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
            } else {
                Spacer(modifier = Modifier.width(20.dp))
            }
        }
        HorizontalDivider()
    }

    // Total Row
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(8.dp)
    ) {
        Text("Total", modifier = Modifier.width(40.dp), fontWeight = FontWeight.Bold)
        Text(if (totalScore > 0) totalPar.toString() else "-", modifier = Modifier.width(40.dp), fontWeight = FontWeight.Bold)
        Text(if (totalScore > 0) totalScore.toString() else "-", modifier = Modifier.width(50.dp), fontWeight = FontWeight.Bold)
        Text(totalPutts.toString(), modifier = Modifier.width(50.dp), fontWeight = FontWeight.Bold)
        
        val totalSg = stats.sumOf { it.strokesGained ?: 0.0 }
        val sgColor = if (totalSg > 0) MaterialTheme.colorScheme.primary else if (totalSg < 0) MaterialTheme.colorScheme.error else Color.Unspecified
        Text(
            text = String.format(java.util.Locale.US, "%.1f", totalSg),
            modifier = Modifier.width(50.dp),
            fontWeight = FontWeight.Bold,
            color = sgColor
        )
    }
}
