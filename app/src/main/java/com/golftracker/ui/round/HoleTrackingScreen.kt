package com.golftracker.ui.round

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.golftracker.data.entity.Club
import com.golftracker.data.model.ApproachLie
import com.golftracker.data.model.PenaltyType
import com.golftracker.data.model.ShotOutcome
import com.golftracker.ui.components.ChipSelector
import com.golftracker.ui.components.NumberStepper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HoleTrackingScreen(
    onNavigateBack: () -> Unit,
    onFinishRound: () -> Unit,
    viewModel: RoundViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val clubs by viewModel.clubs.collectAsState()
    val holeStat = uiState.currentHoleStat
    val hole = uiState.currentHole

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Hole ${hole?.holeNumber ?: "-"}", style = MaterialTheme.typography.titleMedium)
                            // Show hole yardage
                            uiState.currentHoleYardage?.let { yds ->
                                Text(
                                    "  •  $yds yds",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Par ${hole?.par ?: "-"} • HCP ${hole?.handicapIndex ?: "-"}", 
                                style = MaterialTheme.typography.bodySmall
                            )
                            // Show cumulative over-par
                            val cumulative = uiState.cumulativeOverPar
                            if (uiState.holeStats.any { it.score > 0 }) {
                                Text(
                                    "  •  Round: ${if (cumulative > 0) "+$cumulative" else if (cumulative < 0) "$cumulative" else "E"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        cumulative < 0 -> MaterialTheme.colorScheme.primary
                                        cumulative > 0 -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.prevHole() }, enabled = uiState.currentHoleIndex > 0) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Previous Hole")
                    }
                    Text("${uiState.currentHoleIndex + 1} / ${uiState.holes.size}", modifier = Modifier.padding(horizontal = 8.dp))
                    IconButton(onClick = { viewModel.nextHole() }, enabled = uiState.currentHoleIndex < uiState.holes.size - 1) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "Next Hole")
                    }
                }
            )
        },
        bottomBar = {
            // Always show Finish Round button
            Button(
                onClick = { 
                    viewModel.finalizeRound()
                    onFinishRound()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = if (uiState.currentHoleIndex == uiState.holes.size - 1) {
                    ButtonDefaults.buttonColors()
                } else {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(
                    if (uiState.currentHoleIndex == uiState.holes.size - 1) "Finish Round"
                    else "Finish Round (${uiState.currentHoleIndex + 1}/${uiState.holes.size})"
                )
            }
        }
    ) { padding ->
        if (holeStat == null || hole == null) {
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hole Summary Header
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Hole ${hole.holeNumber}",
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.weight(1f)
                            )
                            val totalSg = holeStat.strokesGained ?: 0.0
                            val sgColor = if (totalSg > 0.1) MaterialTheme.colorScheme.primary else if (totalSg < -0.1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            val sign = if (totalSg > 0) "+" else ""
                            Text(
                                "Total SG: $sign${String.format(java.util.Locale.US, "%.2f", totalSg)}",
                                style = MaterialTheme.typography.titleLarge,
                                color = sgColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            SummarySgItem("Off Tee", holeStat.sgOffTee)
                            SummarySgItem("Appr", holeStat.sgApproach)
                            SummarySgItem("Around", holeStat.sgAroundGreen)
                            SummarySgItem("Putt", holeStat.sgPutting)
                        }
                    }
                }
            }

            // Tee Shot (hidden on Par 3)
            if (hole.par > 3) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Tee Shot", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))

                            // Smart default: Driver on par 4/5 when unset
                            val effectiveTeeClubId = holeStat.teeClubId ?: viewModel.defaultTeeClub()?.id
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Distance (yds)", modifier = Modifier.weight(1f))
                                IntegerInput(
                                    value = holeStat.teeShotDistance,
                                    onValueChange = { 
                                        viewModel.updateTeeShot(holeStat.teeOutcome, holeStat.teeInTrouble, effectiveTeeClubId, it)
                                    },
                                    label = "Distance",
                                    modifier = Modifier.width(100.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            // Club dropdown
                            val teeClubs = clubs.filter { it.type == "DRIVER" || it.type == "WOOD" || it.type == "HYBRID" || it.type == "IRON" }
                            if (teeClubs.isNotEmpty()) {
                                ClubDropdown(
                                    label = "Club",
                                    clubs = teeClubs,
                                    selectedClubId = effectiveTeeClubId,
                                    onClubSelected = { cid ->
                                        viewModel.updateTeeShot(holeStat.teeOutcome, holeStat.teeInTrouble, cid, holeStat.teeShotDistance)
                                    }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            Text("Outcome", style = MaterialTheme.typography.labelMedium)
                            ChipSelector(
                                options = ShotOutcome.values().toList(),
                                selectedOption = holeStat.teeOutcome,
                                onOptionSelected = { viewModel.updateTeeShot(it, holeStat.teeInTrouble, effectiveTeeClubId, holeStat.teeShotDistance) },
                                labelMapper = { it.name.replace("_", " ") }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("In Trouble", modifier = Modifier.weight(1f))
                                Switch(
                                    checked = holeStat.teeInTrouble,
                                    onCheckedChange = { viewModel.updateTeeShot(holeStat.teeOutcome, it, effectiveTeeClubId, holeStat.teeShotDistance) }
                                )
                            }
                            
                            holeStat.sgOffTee?.let { sg ->
                                Spacer(modifier = Modifier.height(4.dp))
                                val sgColor = if (sg > 0) MaterialTheme.colorScheme.primary else if (sg < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                val sign = if (sg > 0) "+" else ""
                                Text(
                                    "SG Off Tee: $sign${String.format(java.util.Locale.US, "%.2f", sg)}", 
                                    color = sgColor, 
                                    style = MaterialTheme.typography.bodyMedium, 
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Approach Shots
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Approach Shots", style = MaterialTheme.typography.titleMedium)
                            OutlinedButton(onClick = { viewModel.addApproachShot() }) {
                                Text("Add Shot")
                            }
                        }
                        
                        // Suggest Tee Distance logic
                        val firstApproach = uiState.shots.firstOrNull()
                        val currentTeeDist = holeStat.teeShotDistance
                        val holeYardage = uiState.currentHoleYardage
                        if (firstApproach != null && firstApproach.distanceToPin != null && holeYardage != null) {
                            val potentialTeeDist = holeYardage - firstApproach.distanceToPin!!
                            if (currentTeeDist == null || currentTeeDist != potentialTeeDist) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { viewModel.updateTeeShot(holeStat.teeOutcome, holeStat.teeInTrouble, holeStat.teeClubId, potentialTeeDist) },
                                    colors = ButtonDefaults.filledTonalButtonColors()
                                ) {
                                    Text("Set Tee Distance to $potentialTeeDist (Calculated)")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (uiState.shots.isEmpty()) {
                            Text(
                                "No approach shots recorded.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            uiState.shots.forEachIndexed { index, shot ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Shot ${index + 1}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                                            shot.strokesGained?.let { sg ->
                                                val sgColor = if (sg > 0) MaterialTheme.colorScheme.primary else if (sg < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                                val sign = if (sg > 0) "+" else ""
                                                Text("SG: $sign${String.format(java.util.Locale.US, "%.2f", sg)}", color = sgColor, style = MaterialTheme.typography.labelMedium)
                                            }
                                            IconButton(onClick = { viewModel.deleteApproachShot(shot) }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete Shot", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                        
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            IntegerInput(
                                                value = shot.distanceToPin,
                                                onValueChange = { dist: Int? ->
                                                    val newClubId = dist?.let { d -> viewModel.suggestApproachClub(d)?.id } ?: shot.clubId
                                                    viewModel.updateShotDetails(shot, shot.outcome, shot.lie, newClubId, dist, shot.isRecovery)
                                                },
                                                label = "Dist to Pin",
                                                modifier = Modifier.weight(1f)
                                            )
                                            
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text("Recovery", style = MaterialTheme.typography.labelSmall)
                                                Switch(
                                                    checked = shot.isRecovery,
                                                    onCheckedChange = { 
                                                        val currentClubId = shot.clubId ?: shot.distanceToPin?.let { dist -> viewModel.suggestApproachClub(dist)?.id }
                                                        viewModel.updateShotDetails(shot, shot.outcome, shot.lie, currentClubId, shot.distanceToPin, it)
                                                    }
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Club
                                        val approachClubs = clubs.filter { it.type != "DRIVER" && it.type != "PUTTER" }
                                        val suggestedClubId = shot.clubId
                                            ?: shot.distanceToPin?.let { dist ->
                                                viewModel.suggestApproachClub(dist)?.id
                                            }
                                        if (approachClubs.isNotEmpty()) {
                                            ClubDropdown(
                                                label = "Club",
                                                clubs = approachClubs,
                                                selectedClubId = suggestedClubId,
                                                onClubSelected = { cid ->
                                                    viewModel.updateShotDetails(shot, shot.outcome, shot.lie, cid, shot.distanceToPin, shot.isRecovery)
                                                }
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }

                                        // Lie
                                        Text("Lie", style = MaterialTheme.typography.labelSmall)
                                        ChipSelector(
                                            options = ApproachLie.values().toList(),
                                            selectedOption = shot.lie,
                                            onOptionSelected = { viewModel.updateShotDetails(shot, shot.outcome, it, suggestedClubId, shot.distanceToPin, shot.isRecovery) }
                                        )
                                        
                                        // Outcome
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Outcome", style = MaterialTheme.typography.labelSmall)
                                        ChipSelector(
                                            options = ShotOutcome.values().toList(),
                                            selectedOption = shot.outcome,
                                            onOptionSelected = { viewModel.updateShotDetails(shot, it, shot.lie, suggestedClubId, shot.distanceToPin, shot.isRecovery) },
                                            labelMapper = { it.name.replace("_", " ") }
                                        )
                                    }
                                }
                            }
                        }
                        
                        holeStat.sgApproach?.let { sg ->
                            Spacer(modifier = Modifier.height(16.dp))
                            val sgColor = if (sg > 0) MaterialTheme.colorScheme.primary else if (sg < -0.1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            val sign = if (sg > 0) "+" else ""
                            Text(
                                "SG Approach: $sign${String.format(java.util.Locale.US, "%.2f", sg)}", 
                                color = sgColor, 
                                style = MaterialTheme.typography.bodyMedium, 
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Green & Short Game
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Short Game", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("GIR", modifier = Modifier.weight(1f))
                            val isGir = (holeStat.score - holeStat.putts <= hole.par - 2) && holeStat.score > 0
                            Text(
                                if (isGir) "Yes" else "No",
                                color = if (isGir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Sand Shots", modifier = Modifier.weight(1f))
                            NumberStepper(
                                value = holeStat.sandShots,
                                onValueChange = { viewModel.updateGreen(holeStat.chips, it, holeStat.chipDistance, holeStat.chipLie) },
                                range = 0..5
                            )
                        }
                        
                         Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Up & Down", modifier = Modifier.weight(1f))
                            val isUpAndDown = (holeStat.score - holeStat.putts <= hole.par - 2).let { gir ->
                                !gir && holeStat.chips == 1 && holeStat.putts == 1
                            }
                            Text(
                                if (isUpAndDown) "Yes" else "No",
                                color = if (isUpAndDown) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Chips", modifier = Modifier.weight(1f))
                            NumberStepper(
                                value = holeStat.chips,
                                onValueChange = { viewModel.updateGreen(it, holeStat.sandShots, holeStat.chipDistance, holeStat.chipLie) },
                                range = 0..10
                            )
                        }
                        
                        if (holeStat.chips > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Chip Lie", modifier = Modifier.weight(1f))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val isFairway = holeStat.chipLie == com.golftracker.data.model.ApproachLie.FAIRWAY
                                    androidx.compose.material3.FilterChip(
                                        selected = isFairway,
                                        onClick = { viewModel.updateGreen(holeStat.chips, holeStat.sandShots, holeStat.chipDistance, com.golftracker.data.model.ApproachLie.FAIRWAY) },
                                        label = { Text("Fwy/Fringe") }
                                    )
                                    val isRough = holeStat.chipLie == com.golftracker.data.model.ApproachLie.ROUGH
                                    androidx.compose.material3.FilterChip(
                                        selected = isRough,
                                        onClick = { viewModel.updateGreen(holeStat.chips, holeStat.sandShots, holeStat.chipDistance, com.golftracker.data.model.ApproachLie.ROUGH) },
                                        label = { Text("Rough") }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Chip Distance (yds)", modifier = Modifier.weight(1f))
                                    IntegerInput(
                                        value = holeStat.chipDistance,
                                        onValueChange = { 
                                            viewModel.updateGreen(holeStat.chips, holeStat.sandShots, it, holeStat.chipLie)
                                        },
                                        label = "Distance",
                                        modifier = Modifier.width(100.dp)
                                    )
                            }
                        }
                        
                        holeStat.sgAroundGreen?.let { sg ->
                            Spacer(modifier = Modifier.height(8.dp))
                            val sgColor = if (sg > 0) MaterialTheme.colorScheme.primary else if (sg < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            val sign = if (sg > 0) "+" else ""
                            Text(
                                "SG Around Green: $sign${String.format(java.util.Locale.US, "%.2f", sg)}", 
                                color = sgColor, 
                                style = MaterialTheme.typography.bodyMedium, 
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Putting
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Putting", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Putts", modifier = Modifier.weight(1f))
                            NumberStepper(
                                value = holeStat.putts,
                                onValueChange = { viewModel.updatePutts(it) },
                                range = 0..5 
                            )
                        }
                        
                        if (holeStat.putts > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Distances (ft)", style = MaterialTheme.typography.labelSmall)
                            uiState.putts.forEachIndexed { index, putt ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                                    Text("Putt ${index + 1}", modifier = Modifier.width(52.dp), style = MaterialTheme.typography.bodyMedium)
                                    putt.strokesGained?.let { sg ->
                                        val sgColor = if (sg > 0) MaterialTheme.colorScheme.primary else if (sg < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                        val sign = if (sg > 0) "+" else ""
                                        Text("$sign${String.format(java.util.Locale.US, "%.2f", sg)}", color = sgColor, modifier = Modifier.width(40.dp), style = MaterialTheme.typography.labelSmall)
                                    } ?: Spacer(modifier = Modifier.width(40.dp))
                                    OutlinedButton(
                                        onClick = { viewModel.incrementPuttDistance(putt, -5f) },
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                        modifier = Modifier.height(40.dp).width(40.dp)
                                    ) {
                                        Text("-5", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    NumberStepper(
                                        value = putt.distance?.toInt() ?: 0,
                                        onValueChange = { viewModel.updatePuttDistance(putt, it.toFloat()) },
                                        range = 0..100,
                                        buttonSize = 32.dp,
                                        textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    OutlinedButton(
                                        onClick = { viewModel.incrementPuttDistance(putt, 5f) },
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                        modifier = Modifier.height(40.dp).width(40.dp)
                                    ) {
                                        Text("+5", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        
                        holeStat.sgPutting?.let { sg ->
                            Spacer(modifier = Modifier.height(16.dp))
                            val sgColor = if (sg > 0) MaterialTheme.colorScheme.primary else if (sg < -0.1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            val sign = if (sg > 0) "+" else ""
                            Text(
                                "SG Putting: $sign${String.format(java.util.Locale.US, "%.2f", sg)}", 
                                color = sgColor, 
                                style = MaterialTheme.typography.bodyMedium, 
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Penalties
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Penalties", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { viewModel.addPenalty(PenaltyType.WATER, 1) }) {
                                Text("+ Water")
                            }
                            OutlinedButton(onClick = { viewModel.addPenalty(PenaltyType.OB, 1) }) {
                                Text("+ OB")
                            }
                             OutlinedButton(onClick = { viewModel.addPenalty(PenaltyType.OTHER, 1) }) {
                                Text("+ Other")
                            }
                        }
                        
                        if (uiState.penalties.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            uiState.penalties.forEach { penalty ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${penalty.type} (${penalty.strokes})", modifier = Modifier.weight(1f))
                                    IconButton(onClick = { viewModel.removePenalty(penalty) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove Penalty")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // Score
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Score", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        NumberStepper(
                            value = holeStat.score,
                            onValueChange = { viewModel.updateScore(it) },
                            range = 1..20
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val toPar = holeStat.score - hole.par
                        Text(
                            text = if (toPar > 0) "+$toPar" else if (toPar < 0) "$toPar" else "E",
                            style = MaterialTheme.typography.titleLarge,
                            color = when {
                                toPar < 0 -> MaterialTheme.colorScheme.primary
                                toPar > 0 -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                        
                        holeStat.strokesGained?.let { sg ->
                            Spacer(modifier = Modifier.height(4.dp))
                            val sgColor = if (sg > 0) MaterialTheme.colorScheme.primary else if (sg < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            val sign = if (sg > 0) "+" else ""
                            Text(
                                "SG: $sign${String.format(java.util.Locale.US, "%.2f", sg)}", 
                                color = sgColor, 
                                style = MaterialTheme.typography.bodyMedium, 
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            
            // Bottom Spacer
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClubDropdown(
    label: String,
    clubs: List<Club>,
    selectedClubId: Int?,
    onClubSelected: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedClub = clubs.firstOrNull { it.id == selectedClubId }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedClub?.let {
                "${it.name}${it.stockDistance?.let { d -> " ($d yds)" } ?: ""}"
            } ?: "Select club",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("None") },
                onClick = {
                    onClubSelected(null)
                    expanded = false
                }
            )
            clubs.forEach { club ->
                DropdownMenuItem(
                    text = {
                        Text("${club.name}${club.stockDistance?.let { " ($it yds)" } ?: ""}")
                    },
                    onClick = {
                        onClubSelected(club.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun IntegerInput(
    value: Int?,
    onValueChange: (Int?) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    var textValue by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(value?.toString() ?: "")) }

    // Sync external value changes to local state, only if they differ significantly
    androidx.compose.runtime.LaunchedEffect(value) {
        val currentParsed = textValue.text.toIntOrNull()
        if (currentParsed != value) {
            val newText = value?.toString() ?: ""
            // Only update if the text representation is actually different (avoids cursor reset if just reformatting)
            if (textValue.text != newText) {
                textValue = textValue.copy(text = newText, selection = androidx.compose.ui.text.TextRange(newText.length))
            }
        }
    }

    OutlinedTextField(
        value = textValue,
        onValueChange = { newValue ->
            textValue = newValue
            val parsed = newValue.text.filter { it.isDigit() }.toIntOrNull()
            if (parsed != value) {
                onValueChange(parsed)
            }
        },
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

@Composable
private fun SummarySgItem(label: String, sg: Double?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val value = sg ?: 0.0
        val color = if (value > 0.1) MaterialTheme.colorScheme.primary else if (value < -0.1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        val sign = if (value > 0) "+" else ""
        Text(
            "$sign${String.format(java.util.Locale.US, "%.1f", value)}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
