package com.golftracker.ui.round

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
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
                        .navigationBarsPadding()
                        .padding(bottom = 32.dp, start = 16.dp, end = 16.dp)
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
                        .navigationBarsPadding()
                        .padding(bottom = 32.dp, start = 16.dp, end = 16.dp)
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
                    yardages = uiState.yardages,
                    onHoleClick = onNavigateToHole
                )
                Spacer(modifier = Modifier.height(16.dp))
                SgBreakdownCard(stats = stats)
                Spacer(modifier = Modifier.height(16.dp))
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
    yardages: Map<Int, Int>,
    onHoleClick: (Int) -> Unit
) {
    // Header
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp)
    ) {
        Text("Hole", modifier = Modifier.width(36.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("Yds", modifier = Modifier.width(40.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("Par", modifier = Modifier.width(30.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("Score", modifier = Modifier.width(46.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("Putts", modifier = Modifier.width(40.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("SG", modifier = Modifier.width(40.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("GIR", modifier = Modifier.width(36.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center)
    }

    // Rows
    var totalPar = 0
    var totalScore = 0
    var totalPutts = 0
    var totalYardage = 0
    var totalGir = 0

    holes.forEachIndexed { index, hole ->
        val stat = stats.find { it.holeId == hole.id }
        val score = stat?.score ?: 0
        val putts = stat?.putts ?: 0
        val yardage = yardages[hole.id] ?: 0
        val isGir = com.golftracker.util.GirCalculator.isGir(score, hole.par, putts)
        
        if (score > 0) {
            totalPar += hole.par
            totalScore += score
            totalPutts += putts
            totalYardage += yardage
            if (isGir) totalGir++
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onHoleClick(index) }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(hole.holeNumber.toString(), modifier = Modifier.width(36.dp), fontSize = 14.sp)
            Text(if (yardage > 0) yardage.toString() else "-", modifier = Modifier.width(40.dp), fontSize = 14.sp)
            Text(hole.par.toString(), modifier = Modifier.width(30.dp), fontSize = 14.sp)
            
            // Score with color logic
            val scoreColor = when {
                score == 0 -> Color.Gray // Not played
                score < hole.par -> Color(0xFF4CAF50) // Birdie or better (Green)
                score > hole.par -> Color(0xFFE57373) // Bogey or worse (Red)
                else -> Color.Unspecified // Par
            }
            Text(
                text = if (score > 0) score.toString() else "-",
                modifier = Modifier.width(46.dp),
                fontWeight = FontWeight.Bold,
                color = scoreColor,
                fontSize = 14.sp
            )

            Text(if (putts > 0) putts.toString() else "-", modifier = Modifier.width(40.dp), fontSize = 14.sp)
            
            val sg = stat?.strokesGained ?: 0.0
            val sgColor = when {
                sg > 0.1 -> MaterialTheme.colorScheme.primary
                sg < -0.1 -> MaterialTheme.colorScheme.error
                else -> Color.Unspecified
            }
            Text(
                text = String.format(java.util.Locale.US, "%.1f", sg),
                modifier = Modifier.width(40.dp),
                color = sgColor,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp
            )
            
            Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                if (isGir) {
                    Icon(
                        imageVector = Icons.Default.Check, 
                        contentDescription = "GIR", 
                        tint = Color(0xFF4CAF50), 
                        modifier = Modifier.size(18.dp)
                    )
                } else if (score > 0) {
                    Text("-", color = Color.Gray, fontSize = 14.sp)
                }
            }
        }
        HorizontalDivider()
    }

    // Totals accumulation for SG (done in the forEach above)

    // Total Row
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Total", modifier = Modifier.width(36.dp), fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(if (totalYardage > 0) totalYardage.toString() else "-", modifier = Modifier.width(40.dp), fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(if (totalScore > 0) totalPar.toString() else "-", modifier = Modifier.width(30.dp), fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(if (totalScore > 0) totalScore.toString() else "-", modifier = Modifier.width(46.dp), fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(if (totalScore > 0) totalPutts.toString() else "-", modifier = Modifier.width(40.dp), fontWeight = FontWeight.Bold, fontSize = 14.sp)
        
        val totalSg = stats.sumOf { it.strokesGained ?: 0.0 }
        val sgColor = if (totalSg > 0.1) MaterialTheme.colorScheme.primary else if (totalSg < -0.1) MaterialTheme.colorScheme.error else Color.Unspecified
        Text(
            text = String.format(java.util.Locale.US, "%.1f", totalSg),
            modifier = Modifier.width(40.dp),
            fontWeight = FontWeight.Bold,
            color = sgColor,
            fontSize = 13.sp
        )
        Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.Center) {
            Text(if (totalScore > 0) totalGir.toString() else "-", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
fun SgBreakdownCard(stats: List<HoleStat>) {
    val holesWithSg = stats.filter { it.strokesGained != null }
    if (holesWithSg.isEmpty()) return

    val totalSg = holesWithSg.sumOf { it.strokesGained ?: 0.0 }
    val offTee = holesWithSg.sumOf { it.sgOffTee ?: 0.0 }
    val approach = holesWithSg.sumOf { it.sgApproach ?: 0.0 }
    val aroundGreen = holesWithSg.sumOf { it.sgAroundGreen ?: 0.0 }
    val putting = holesWithSg.sumOf { it.sgPutting ?: 0.0 }

    fun sgColor(value: Double): Color = when {
        value > 0.05 -> Color(0xFF4CAF50)
        value < -0.05 -> Color(0xFFE57373)
        else -> Color.Unspecified
    }

    fun fmt(value: Double): String {
        val sign = if (value > 0) "+" else ""
        return "$sign${String.format(java.util.Locale.US, "%.2f", value)}"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Strokes Gained", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                SgBreakdownItem("Off Tee", offTee, ::sgColor, ::fmt)
                SgBreakdownItem("Approach", approach, ::sgColor, ::fmt)
                SgBreakdownItem("Around", aroundGreen, ::sgColor, ::fmt)
                SgBreakdownItem("Putting", putting, ::sgColor, ::fmt)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text("Total  ", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                Text(fmt(totalSg), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = sgColor(totalSg))
            }
        }
    }
}

@Composable
private fun SgBreakdownItem(label: String, value: Double, colorFn: (Double) -> Color, fmtFn: (Double) -> String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Spacer(modifier = Modifier.height(4.dp))
        Text(fmtFn(value), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = colorFn(value))
    }
}
