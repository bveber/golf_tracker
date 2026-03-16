package com.golftracker.ui.round

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.golftracker.ui.gps.GpsScreen

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
    var showGps by remember { mutableStateOf(false) }
    var showFinishConfirmation by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "Hole ${hole?.holeNumber ?: "-"}", 
                        style = MaterialTheme.typography.titleLarge, 
                        fontWeight = FontWeight.Bold
                    )
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
                    IconButton(onClick = { showGps = !showGps }) {
                        Icon(
                            if (showGps) Icons.Default.List else Icons.Default.Map,
                            contentDescription = if (showGps) "Back to Stats" else "Show GPS",
                            tint = if (showGps) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        },
        bottomBar = {
            if (!showGps) {
                // Always show Finish Round button
                Button(
                    onClick = { 
                        val incomplete = uiState.holeStats.any { it.score == 0 }
                        if (incomplete) {
                            showFinishConfirmation = true
                        } else {
                            viewModel.finalizeRound()
                            onFinishRound()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 32.dp, start = 16.dp, end = 16.dp),
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
        }
    ) { padding ->
        if (showFinishConfirmation) {
            AlertDialog(
                onDismissRequest = { showFinishConfirmation = false },
                title = { Text("Incomplete Round") },
                text = { Text("You haven't entered scores for all holes in this round. Do you want to finalize it anyway?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.finalizeRound()
                            onFinishRound()
                            showFinishConfirmation = false
                        }
                    ) {
                        Text("Finish Anyway")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showFinishConfirmation = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (holeStat == null || hole == null) {
            return@Scaffold
        }

        Column(modifier = Modifier.padding(padding)) {
            // Hole Info Header
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Par ${hole.par} • HCP ${hole.handicapIndex}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Dist (yds): ", style = MaterialTheme.typography.bodyMedium)
                        IntegerInput(
                            value = uiState.currentHoleYardage,
                            onValueChange = { viewModel.updateHoleYardage(it) },
                            label = "Yds",
                            modifier = Modifier.width(90.dp)
                        )
                    }
                    
                    val cumulative = uiState.cumulativeOverPar
                    if (uiState.holeStats.any { it.score > 0 }) {
                        val scoreStr = if (cumulative > 0) "+$cumulative" else if (cumulative < 0) "$cumulative" else "E"
                        Text(
                            text = "Current Score: $scoreStr",
                            style = MaterialTheme.typography.titleMedium,
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

            Box(modifier = Modifier.weight(1f)) {
                if (showGps) {
                    GpsScreen(
                        roundId = uiState.activeRound?.id,
                        holeStatId = holeStat.id,
                        holePar = hole.par,
                        onClose = { showGps = false }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {


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
                                        viewModel.updateTeeShot(holeStat.teeOutcome, holeStat.teeInTrouble, effectiveTeeClubId, it, holeStat.teeMishit)
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
                                        viewModel.updateTeeShot(holeStat.teeOutcome, holeStat.teeInTrouble, cid, holeStat.teeShotDistance, holeStat.teeMishit)
                                    }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            val teeDispersion = com.golftracker.ui.gps.GpsUtils.DispersionOffsets(holeStat.teeDispersionLeft, holeStat.teeDispersionRight, holeStat.teeDispersionShort, holeStat.teeDispersionLong)
                            val estimatedTeeOutcome = com.golftracker.ui.gps.GpsUtils.estimateOutcome(teeDispersion)
                            val teeMismatch = holeStat.teeOutcome != null && estimatedTeeOutcome != ShotOutcome.ON_TARGET && holeStat.teeOutcome != estimatedTeeOutcome

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Outcome", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                                if (teeMismatch) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = "Mismatch",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(end = 4.dp).height(16.dp)
                                    )
                                    Text(
                                        "GPS suggests ${estimatedTeeOutcome.name.replace("_", " ")}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            ChipSelector(
                                options = ShotOutcome.values().filter { it != ShotOutcome.HOLED_OUT },
                                selectedOption = holeStat.teeOutcome,
                                onOptionSelected = { viewModel.updateTeeShot(it, holeStat.teeInTrouble, effectiveTeeClubId, holeStat.teeShotDistance, holeStat.teeMishit) },
                                labelMapper = { it.name.replace("_", " ") }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("In Trouble", style = MaterialTheme.typography.bodyMedium)
                                    Switch(
                                        checked = holeStat.teeInTrouble,
                                        onCheckedChange = { viewModel.updateTeeShot(holeStat.teeOutcome, it, effectiveTeeClubId, holeStat.teeShotDistance, holeStat.teeMishit) },
                                        modifier = Modifier.padding(start = 4.dp)
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Mishit", style = MaterialTheme.typography.bodyMedium)
                                    Switch(
                                        checked = holeStat.teeMishit,
                                        onCheckedChange = { viewModel.updateTeeShot(holeStat.teeOutcome, holeStat.teeInTrouble, effectiveTeeClubId, holeStat.teeShotDistance, it) },
                                        modifier = Modifier.padding(start = 4.dp)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            var showTeeDispersion by remember(holeStat.id) { mutableStateOf(false) }
                            OutlinedButton(onClick = { showTeeDispersion = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("Dispersion Options")
                            }
                            if (showTeeDispersion) {
                                DispersionDialog(
                                    initialLeft = holeStat.teeDispersionLeft,
                                    initialRight = holeStat.teeDispersionRight,
                                    initialShort = holeStat.teeDispersionShort,
                                    initialLong = holeStat.teeDispersionLong,
                                    onDismissRequest = { showTeeDispersion = false },
                                    onSave = { l, r, s, ln ->
                                        viewModel.updateTeeShot(holeStat.teeOutcome, holeStat.teeInTrouble, effectiveTeeClubId, holeStat.teeShotDistance, holeStat.teeMishit, holeStat.teeSlope, holeStat.teeStance, l, r, s, ln)
                                        showTeeDispersion = false
                                    }
                                )
                            }
                            
                            holeStat.sgOffTee?.let { sg ->
                                Spacer(modifier = Modifier.height(4.dp))
                                val sgColor = if (sg > 0) MaterialTheme.colorScheme.primary else if (sg < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                val sign = if (sg > 0) "+" else ""
                                val adj = holeStat.difficultyAdjustment
                                val raw = sg - adj
                                val rawSign = if (raw > 0) "+" else ""
                                val adjSign = if (adj > 0) "+" else ""
                                
                                Column {
                                    Text(
                                        "SG Off Tee: $sign${String.format(java.util.Locale.US, "%.2f", sg)}", 
                                        color = sgColor, 
                                        style = MaterialTheme.typography.bodyMedium, 
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (adj != 0.0) {
                                        Text(
                                            "($rawSign${String.format(java.util.Locale.US, "%.2f", raw)} raw $adjSign${String.format(java.util.Locale.US, "%.2f", adj)} adj)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
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
                                    onClick = { viewModel.updateTeeShot(holeStat.teeOutcome, holeStat.teeInTrouble, holeStat.teeClubId, potentialTeeDist, holeStat.teeMishit, holeStat.teeSlope, holeStat.teeStance) },
                                    colors = ButtonDefaults.filledTonalButtonColors()
                                ) {
                                    Text("Set Tee Distance to $potentialTeeDist (Calculated)")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        var approachShotForAdvancedLie by remember { mutableStateOf<com.golftracker.data.entity.Shot?>(null) }
                        
                        if (approachShotForAdvancedLie != null) {
                            val activeShot = approachShotForAdvancedLie!!
                            val currentApproachClubId = activeShot.clubId ?: activeShot.distanceToPin?.let { dist -> viewModel.suggestApproachClub(dist)?.id }
                            AdvancedLieDialog(
                                initialSlope = activeShot.slope,
                                initialStance = activeShot.stance,
                                onDismissRequest = { approachShotForAdvancedLie = null },
                                onSave = { slope, stance ->
                                    viewModel.updateShotDetails(activeShot, activeShot.outcome, activeShot.lie, currentApproachClubId, activeShot.distanceToPin, activeShot.isRecovery, activeShot.distanceTraveled, slope, stance)
                                    approachShotForAdvancedLie = null
                                }
                            )
                        }

                        var approachShotForDispersion by remember { mutableStateOf<com.golftracker.data.entity.Shot?>(null) }
                        if (approachShotForDispersion != null) {
                            val activeShot = approachShotForDispersion!!
                            val currentApproachClubId = activeShot.clubId ?: activeShot.distanceToPin?.let { dist -> viewModel.suggestApproachClub(dist)?.id }
                            DispersionDialog(
                                initialLeft = activeShot.dispersionLeft,
                                initialRight = activeShot.dispersionRight,
                                initialShort = activeShot.dispersionShort,
                                initialLong = activeShot.dispersionLong,
                                onDismissRequest = { approachShotForDispersion = null },
                                onSave = { l, r, s, ln ->
                                    viewModel.updateShotDetails(activeShot, activeShot.outcome, activeShot.lie, currentApproachClubId, activeShot.distanceToPin, activeShot.isRecovery, activeShot.distanceTraveled, activeShot.slope, activeShot.stance, l, r, s, ln)
                                    approachShotForDispersion = null
                                }
                            )
                        }

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
                                                    viewModel.updateShotDetails(shot, shot.outcome, shot.lie, newClubId, dist, shot.isRecovery, shot.distanceTraveled)
                                                },
                                                label = "Dist to Pin",
                                                modifier = Modifier.weight(1f)
                                            )
                                            
                                            IntegerInput(
                                                value = shot.distanceTraveled,
                                                onValueChange = { dist: Int? ->
                                                    viewModel.updateShotDetails(shot, shot.outcome, shot.lie, shot.clubId, shot.distanceToPin, shot.isRecovery, dist)
                                                },
                                                label = "Shot Dist",
                                                modifier = Modifier.weight(1f)
                                            )
                                            
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text("Recovery", style = MaterialTheme.typography.labelSmall)
                                                Switch(
                                                    checked = shot.isRecovery,
                                                    onCheckedChange = { 
                                                        val currentClubId = shot.clubId ?: shot.distanceToPin?.let { dist -> viewModel.suggestApproachClub(dist)?.id }
                                                    viewModel.updateShotDetails(shot, shot.outcome, shot.lie, currentClubId, shot.distanceToPin, it, shot.distanceTraveled)
                                                }
                                            )
                                        }
                                    }
                                    if (shot.isRecovery) {
                                        val baseSg = (shot.strokesGained ?: 0.0) - shot.penaltyAttribution
                                        if (baseSg < 0) {
                                            val maxAttribution = kotlin.math.abs(baseSg).toFloat()
                                            val stepCount = (maxAttribution / 0.1f).toInt().coerceAtLeast(1)
                                            Column(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                                                Text(
                                                    text = "Attributed Stymied Penalty: ${String.format(java.util.Locale.US, "%.2f", shot.penaltyAttribution)}",
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                                Slider(
                                                    value = shot.penaltyAttribution.toFloat(),
                                                    onValueChange = { viewModel.updateShotPenaltyAttribution(shot, it.toDouble()) },
                                                    valueRange = 0f..maxAttribution,
                                                    steps = stepCount - 1,
                                                    modifier = Modifier.height(32.dp)
                                                )
                                            }
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
                                                    viewModel.updateShotDetails(shot, shot.outcome, shot.lie, cid, shot.distanceToPin, shot.isRecovery, shot.distanceTraveled)
                                                }
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }

                                        // Lie
                                        Text("Lie", style = MaterialTheme.typography.labelSmall)
                                        ChipSelector(
                                            options = ApproachLie.values().toList(),
                                            selectedOption = shot.lie,
                                            onOptionSelected = { viewModel.updateShotDetails(shot, shot.outcome, it, suggestedClubId, shot.distanceToPin, shot.isRecovery, shot.distanceTraveled) }
                                        )
                                        
                                        // Outcome
                                        val shotDispersion = com.golftracker.ui.gps.GpsUtils.DispersionOffsets(shot.dispersionLeft, shot.dispersionRight, shot.dispersionShort, shot.dispersionLong)
                                        val estimatedShotOutcome = com.golftracker.ui.gps.GpsUtils.estimateOutcome(shotDispersion)
                                        val shotMismatch = shot.outcome != null && estimatedShotOutcome != ShotOutcome.ON_TARGET && shot.outcome != estimatedShotOutcome

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Outcome", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                                            if (shotMismatch) {
                                                Icon(
                                                    Icons.Default.Warning,
                                                    contentDescription = "Mismatch",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.padding(end = 4.dp).height(14.dp)
                                                )
                                                Text(
                                                    "GPS suggests ${estimatedShotOutcome.name.replace("_", " ")}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                        ChipSelector(
                                            options = ShotOutcome.values().filter { it != ShotOutcome.HOLED_OUT },
                                            selectedOption = shot.outcome,
                                            onOptionSelected = { viewModel.updateShotDetails(shot, it, shot.lie, suggestedClubId, shot.distanceToPin, shot.isRecovery, shot.distanceTraveled, shot.slope, shot.stance) },
                                            modifier = Modifier.padding(top = 4.dp)
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                            OutlinedButton(onClick = { approachShotForAdvancedLie = shot }, modifier = Modifier.weight(1f)) {
                                                Text("Lie")
                                            }
                                            OutlinedButton(onClick = { approachShotForDispersion = shot }, modifier = Modifier.weight(1f)) {
                                                Text("Dispersion")
                                            }
                                        }
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
                            val isGir = holeStat.gir
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
                                onValueChange = { 
                                    viewModel.updateGreen(
                                        chips = holeStat.chips, 
                                        sandShots = it, 
                                        chipDistance = holeStat.chipDistance,
                                        sandShotDistance = holeStat.sandShotDistance,
                                        chipLie = holeStat.chipLie
                                    ) 
                                },
                                range = 0..5
                            )
                        }
                        
                        if (holeStat.sandShots > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Sand Dist (yds)", modifier = Modifier.weight(1f))
                                IntegerInput(
                                    value = holeStat.sandShotDistance,
                                    onValueChange = { 
                                        viewModel.updateGreen(
                                            chips = holeStat.chips,
                                            sandShots = holeStat.sandShots,
                                            chipDistance = holeStat.chipDistance,
                                            sandShotDistance = it,
                                            chipLie = holeStat.chipLie
                                        )
                                    },
                                    label = "Distance",
                                    modifier = Modifier.width(100.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            var showSandAdvancedLie by remember(holeStat.id) { mutableStateOf(false) }
                            OutlinedButton(onClick = { showSandAdvancedLie = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("Advanced Sand Lie Options")
                            }
                            if (showSandAdvancedLie) {
                                AdvancedLieDialog(
                                    initialSlope = holeStat.sandShotSlope,
                                    initialStance = holeStat.sandShotStance,
                                    onDismissRequest = { showSandAdvancedLie = false },
                                    onSave = { slope, stance ->
                                        viewModel.updateSandShotLieExtras(slope, stance)
                                        showSandAdvancedLie = false
                                    }
                                )
                            }
                        }
                        
                         Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Up & Down", modifier = Modifier.weight(1f))
                            val holedOutFromOffGreen = uiState.shots.any { it.outcome == ShotOutcome.HOLED_OUT } || holeStat.teeOutcome == ShotOutcome.HOLED_OUT
                            val isUpAndDown = !holeStat.gir && holeStat.chips == 1 && (holeStat.putts == 1 || holedOutFromOffGreen)
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
                                onValueChange = { 
                                    viewModel.updateGreen(
                                        chips = it, 
                                        sandShots = holeStat.sandShots, 
                                        chipDistance = holeStat.chipDistance,
                                        sandShotDistance = holeStat.sandShotDistance,
                                        chipLie = holeStat.chipLie
                                    ) 
                                },
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
                                        onClick = { 
                                            viewModel.updateGreen(
                                                chips = holeStat.chips, 
                                                sandShots = holeStat.sandShots, 
                                                chipDistance = holeStat.chipDistance,
                                                sandShotDistance = holeStat.sandShotDistance,
                                                chipLie = com.golftracker.data.model.ApproachLie.FAIRWAY
                                            ) 
                                        },
                                        label = { Text("Fwy/Fringe") }
                                    )
                                    val isRough = holeStat.chipLie == com.golftracker.data.model.ApproachLie.ROUGH
                                    androidx.compose.material3.FilterChip(
                                        selected = isRough,
                                        onClick = { 
                                            viewModel.updateGreen(
                                                chips = holeStat.chips, 
                                                sandShots = holeStat.sandShots, 
                                                chipDistance = holeStat.chipDistance,
                                                sandShotDistance = holeStat.sandShotDistance,
                                                chipLie = com.golftracker.data.model.ApproachLie.ROUGH
                                            ) 
                                        },
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
                                            viewModel.updateGreen(
                                                chips = holeStat.chips, 
                                                sandShots = holeStat.sandShots, 
                                                chipDistance = it,
                                                sandShotDistance = holeStat.sandShotDistance,
                                                chipLie = holeStat.chipLie
                                            )
                                        },
                                        label = "Distance",
                                        modifier = Modifier.width(100.dp)
                                    )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            var showChipAdvancedLie by remember(holeStat.id) { mutableStateOf(false) }
                            OutlinedButton(onClick = { showChipAdvancedLie = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("Advanced Chip Lie Options")
                            }
                            if (showChipAdvancedLie) {
                                AdvancedLieDialog(
                                    initialSlope = holeStat.chipSlope,
                                    initialStance = holeStat.chipStance,
                                    onDismissRequest = { showChipAdvancedLie = false },
                                    onSave = { slope, stance ->
                                        viewModel.updateChipLieExtras(slope, stance)
                                        showChipAdvancedLie = false
                                    }
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
                            val adj = holeStat.difficultyAdjustment
                            val raw = sg - adj
                            val rawSign = if (raw > 0) "+" else ""
                            val adjSign = if (adj > 0) "+" else ""

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "SG: $sign${String.format(java.util.Locale.US, "%.2f", sg)}", 
                                    color = sgColor, 
                                    style = MaterialTheme.typography.bodyMedium, 
                                    fontWeight = FontWeight.Bold
                                )
                                if (adj != 0.0) {
                                    Text(
                                        "($rawSign${String.format(java.util.Locale.US, "%.2f", raw)} raw $adjSign${String.format(java.util.Locale.US, "%.2f", adj)} adj)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // Hole Summary Header (Strokes Gained Overview)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Strokes Gained",
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.weight(1f)
                            )
                            val totalSg = holeStat.strokesGained ?: 0.0
                            val sgColor = if (totalSg > 0.1) MaterialTheme.colorScheme.primary else if (totalSg < -0.1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            val sign = if (totalSg > 0) "+" else ""
                            Text(
                                "Total: $sign${String.format(java.util.Locale.US, "%.2f", totalSg)}",
                                style = MaterialTheme.typography.titleLarge,
                                color = sgColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            SummarySgItem("Off Tee", holeStat.sgOffTee, holeStat.difficultyAdjustment)
                            SummarySgItem("Appr", holeStat.sgApproach)
                            SummarySgItem("Around", holeStat.sgAroundGreen)
                            SummarySgItem("Putt", holeStat.sgPutting)
                            val penaltyStrokes = uiState.penalties.sumOf { it.strokes }
                            if (penaltyStrokes > 0) {
                                SummarySgItem("Pen", -penaltyStrokes.toDouble())
                            }
                        }
                    }
                }
            }

            // Bottom Spacer
            item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
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
            val digitsOnly = newValue.text.filter { it.isDigit() }
            if (digitsOnly.length <= 3) {
                textValue = newValue.copy(text = digitsOnly)
                val parsed = digitsOnly.toIntOrNull()
                if (parsed != value) {
                    onValueChange(parsed)
                }
            }
        },
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

@Composable
private fun SummarySgItem(label: String, sg: Double?, adj: Double = 0.0) {
    val value = sg ?: 0.0
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val color = if (value > 0.1) MaterialTheme.colorScheme.primary else if (value < -0.1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        val sign = if (value > 0) "+" else ""
        Text(
            "$sign${String.format(java.util.Locale.US, "%.2f", value)}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        if (adj != 0.0) {
            val adjSign = if (adj > 0) "+" else ""
            Text(
                "${adjSign}${String.format(java.util.Locale.US, "%.2f", adj)} adj",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun AdvancedLieDialog(
    initialSlope: com.golftracker.data.model.LieSlope?,
    initialStance: com.golftracker.data.model.LieStance?,
    onDismissRequest: () -> Unit,
    onSave: (com.golftracker.data.model.LieSlope?, com.golftracker.data.model.LieStance?) -> Unit
) {
    var slope by remember { mutableStateOf(initialSlope) }
    var stance by remember { mutableStateOf(initialStance) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Advanced Lie Options") },
        text = {
            Column {
                Text("Slope", style = MaterialTheme.typography.labelMedium)
                ChipSelector(
                    options = com.golftracker.data.model.LieSlope.values().toList(),
                    selectedOption = slope,
                    onOptionSelected = { slope = if (slope == it) null else it },
                    labelMapper = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Stance", style = MaterialTheme.typography.labelMedium)
                ChipSelector(
                    options = com.golftracker.data.model.LieStance.values().toList(),
                    selectedOption = stance,
                    onOptionSelected = { stance = if (stance == it) null else it },
                    labelMapper = { it.name.replace("_", " ").lowercase().replaceFirstChar { c -> c.uppercase() } }
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onSave(slope, stance) }) { Text("Save") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismissRequest) { Text("Cancel") }
        }
    )
}

@Composable
fun DispersionDialog(
    initialLeft: Int?,
    initialRight: Int?,
    initialShort: Int?,
    initialLong: Int?,
    onDismissRequest: () -> Unit,
    onSave: (left: Int?, right: Int?, short: Int?, long: Int?) -> Unit
) {
    var left by remember { mutableStateOf(initialLeft) }
    var right by remember { mutableStateOf(initialRight) }
    var shortDist by remember { mutableStateOf(initialShort) }
    var longDist by remember { mutableStateOf(initialLong) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Shot Dispersion (yds)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter the distance missed for applicable directions. Leave blank if on target.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IntegerInput(
                        value = left, 
                        onValueChange = { left = it; if (it != null) right = null }, 
                        label = "Left", 
                        modifier = Modifier.weight(1f)
                    )
                    IntegerInput(
                        value = right, 
                        onValueChange = { right = it; if (it != null) left = null }, 
                        label = "Right", 
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IntegerInput(
                        value = shortDist, 
                        onValueChange = { shortDist = it; if (it != null) longDist = null }, 
                        label = "Short", 
                        modifier = Modifier.weight(1f)
                    )
                    IntegerInput(
                        value = longDist, 
                        onValueChange = { longDist = it; if (it != null) shortDist = null }, 
                        label = "Long", 
                        modifier = Modifier.weight(1f)
                    )
                }
                
                if (initialLeft != null || initialRight != null || initialShort != null || initialLong != null) {
                    Text(
                        "Values shown may have been estimated from GPS tracking.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onSave(left, right, shortDist, longDist) }) { Text("Save") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismissRequest) { Text("Cancel") }
        }
    )
}
