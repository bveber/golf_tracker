package com.golftracker.ui.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import java.util.Date
import java.util.Locale
import java.text.SimpleDateFormat
import androidx.hilt.navigation.compose.hiltViewModel
import com.golftracker.data.entity.Club
import com.golftracker.data.model.ApproachLie
import com.golftracker.data.repository.ApproachStats
import com.golftracker.data.repository.ChippingStats
import com.golftracker.data.repository.DrivingStats
import com.golftracker.data.repository.OnTargetBreakdown
import com.golftracker.data.repository.PuttingStats
import com.golftracker.data.repository.ScoringStats
import com.golftracker.data.repository.StatsData
import com.golftracker.ui.components.DistributionBar
import com.golftracker.ui.components.DistributionSegment
import com.golftracker.ui.components.ShotDispersionVisual
import com.golftracker.ui.components.StatCard
import com.golftracker.ui.components.StatCardWithDistribution

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsDashboardScreen(
    onNavigateBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel()
) {
    val uiState by viewModel.statsUiState.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val courses by viewModel.courses.collectAsState()
    val clubs by viewModel.clubs.collectAsState()
    val availableYears by viewModel.availableYears.collectAsState()

    var showDateRangePicker by remember { mutableStateOf(false) }
    var showManageRounds by remember { mutableStateOf(false) }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Scoring", "Driving", "Approach", "Chipping", "Putting", "Strokes Gained")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stats Dashboard") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // ── Filter Bar ──────────────────────────────────────────
            FilterBar(
                courses = courses,
                availableYears = availableYears,
                selectedCourseId = filter.courseId,
                selectedYear = filter.year,
                lastNRounds = filter.lastNRounds,
                startDate = filter.startDate,
                endDate = filter.endDate,
                excludedCount = filter.excludedRoundIds.size,
                onCourseSelected = { viewModel.updateCourseFilter(it) },
                onYearSelected = { viewModel.updateYearFilter(it) },
                onLastNChanged = { viewModel.updateLastNRounds(it) },
                onShowDateRange = { showDateRangePicker = true },
                onShowManageRounds = { showManageRounds = true },
                onClearFilters = { viewModel.clearFilters() }
            )

            // ── Tabs ────────────────────────────────────────────────
            ScrollableTabRow(selectedTabIndex = selectedTabIndex, edgePadding = 0.dp) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }

            // ── Content ─────────────────────────────────────────────
            when (val state = uiState) {
                is StatsUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is StatsUiState.Success -> {
                    val data = state.data
                    if (data.rounds.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No finalized rounds found.")
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            when (selectedTabIndex) {
                                0 -> ScoringTab(data.scoring, data.sg)
                                1 -> DrivingTab(
                                    d = data.driving,
                                    clubs = clubs,
                                    selectedClubId = filter.drivingClubId,
                                    onClubSelected = { viewModel.updateDrivingClubFilter(it) },
                                    sg = data.sg
                                )
                                2 -> ApproachTab(
                                    a = data.approach,
                                    clubs = clubs,
                                    selectedClubId = filter.approachClubId,
                                    onClubSelected = { viewModel.updateApproachClubFilter(it) },
                                    sg = data.sg
                                )
                                3 -> ChippingTab(data.chipping, data.sg)
                                4 -> PuttingTab(data.putting, data.sg)
                                5 -> SgTab(data.sg)
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
                is StatsUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Error: ${state.message}")
                    }
                }
            }
        }

        if (showDateRangePicker) {
            DateRangePickerDialog(
                initialStartDate = filter.startDate,
                initialEndDate = filter.endDate,
                onDismiss = { showDateRangePicker = false },
                onDatesSelected = { start, end ->
                    viewModel.updateStartDate(start)
                    viewModel.updateEndDate(end)
                    showDateRangePicker = false
                }
            )
        }

        if (showManageRounds && uiState is StatsUiState.Success) {
            ManageRoundsDialog(
                rounds = (uiState as StatsUiState.Success).data.rounds,
                excludedRoundIds = filter.excludedRoundIds,
                onDismiss = { showManageRounds = false },
                onToggleExclusion = { viewModel.toggleRoundExclusion(it) }
            )
        }
    }
}

// ── Filter Bar ──────────────────────────────────────────────────────────

@Composable
fun FilterBar(
    courses: List<com.golftracker.data.entity.Course>,
    availableYears: List<Int>,
    selectedCourseId: Int?,
    selectedYear: Int?,
    lastNRounds: Int,
    startDate: Date?,
    endDate: Date?,
    excludedCount: Int,
    onCourseSelected: (Int?) -> Unit,
    onYearSelected: (Int?) -> Unit,
    onLastNChanged: (Int) -> Unit,
    onShowDateRange: () -> Unit,
    onShowManageRounds: () -> Unit,
    onClearFilters: () -> Unit
) {
    var courseExpanded by remember { mutableStateOf(false) }
    var yearExpanded by remember { mutableStateOf(false) }
    var lastNExpanded by remember { mutableStateOf(false) }
    val lastNOptions = listOf(5, 10, 20, 50, 0) // 0 = all
    val dateFormat = remember { SimpleDateFormat("MM/dd", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Manage Rounds
        FilterChip(
            selected = excludedCount > 0,
            onClick = onShowManageRounds,
            label = { 
                Text(
                    if (excludedCount > 0) "Rounds (-$excludedCount)" else "Rounds",
                    style = MaterialTheme.typography.labelSmall 
                ) 
            },
            leadingIcon = {
                if (excludedCount > 0) {
                    Icon(
                        androidx.compose.material.icons.Icons.Default.FilterList,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        )

        // Course filter
        Box {
            FilterChip(
                selected = selectedCourseId != null,
                onClick = { courseExpanded = true },
                label = {
                    Text(
                        courses.find { it.id == selectedCourseId }?.name ?: "Course",
                        maxLines = 1,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            )
            DropdownMenu(expanded = courseExpanded, onDismissRequest = { courseExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("All Courses") },
                    onClick = { onCourseSelected(null); courseExpanded = false }
                )
                courses.forEach { course ->
                    DropdownMenuItem(
                        text = { Text(course.name) },
                        onClick = { onCourseSelected(course.id); courseExpanded = false }
                    )
                }
            }
        }

        // Date Range
        FilterChip(
            selected = startDate != null || endDate != null,
            onClick = onShowDateRange,
            label = {
                val text = when {
                    startDate != null && endDate != null -> "${dateFormat.format(startDate)} - ${dateFormat.format(endDate)}"
                    startDate != null -> "From ${dateFormat.format(startDate)}"
                    endDate != null -> "To ${dateFormat.format(endDate)}"
                    else -> "Dates"
                }
                Text(text, style = MaterialTheme.typography.labelSmall)
            }
        )

        // Year filter (secondary to Date Range)
        if (startDate == null && endDate == null) {
            Box {
                FilterChip(
                    selected = selectedYear != null,
                    onClick = { yearExpanded = true },
                    label = { Text(selectedYear?.toString() ?: "Year", style = MaterialTheme.typography.labelSmall) }
                )
                DropdownMenu(expanded = yearExpanded, onDismissRequest = { yearExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("All Years") },
                        onClick = { onYearSelected(null); yearExpanded = false }
                    )
                    availableYears.forEach { year ->
                        DropdownMenuItem(
                            text = { Text(year.toString()) },
                            onClick = { onYearSelected(year); yearExpanded = false }
                        )
                    }
                }
            }
        }

        // Last N filter
        Box {
            FilterChip(
                selected = lastNRounds != 20,
                onClick = { lastNExpanded = true },
                label = { Text(if (lastNRounds == 0) "All" else "Last $lastNRounds", style = MaterialTheme.typography.labelSmall) }
            )
            DropdownMenu(expanded = lastNExpanded, onDismissRequest = { lastNExpanded = false }) {
                lastNOptions.forEach { n ->
                    DropdownMenuItem(
                        text = { Text(if (n == 0) "All Rounds" else "Last $n") },
                        onClick = { onLastNChanged(n); lastNExpanded = false }
                    )
                }
            }
        }

        // Clear
        if (selectedCourseId != null || selectedYear != null || lastNRounds != 20 || startDate != null || endDate != null || excludedCount > 0) {
            TextButton(onClick = onClearFilters) {
                Text("Clear", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerDialog(
    initialStartDate: Date?,
    initialEndDate: Date?,
    onDismiss: () -> Unit,
    onDatesSelected: (Date?, Date?) -> Unit
) {
    val dateRangePickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStartDate?.time,
        initialSelectedEndDateMillis = initialEndDate?.time
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val start = dateRangePickerState.selectedStartDateMillis?.let { Date(it) }
                val end = dateRangePickerState.selectedEndDateMillis?.let { Date(it) }
                onDatesSelected(start, end)
            }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DateRangePicker(
            state = dateRangePickerState,
            modifier = Modifier.height(400.dp),
            title = { Text("Select Date Range", modifier = Modifier.padding(16.dp)) },
            headline = {
                val start = dateRangePickerState.selectedStartDateMillis
                val end = dateRangePickerState.selectedEndDateMillis
                val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                Text(
                    text = if (start != null && end != null) "${sdf.format(Date(start))} - ${sdf.format(Date(end))}" else "Select dates",
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            },
            showModeToggle = false
        )
    }
}

@Composable
fun ManageRoundsDialog(
    rounds: List<com.golftracker.data.model.RoundWithDetails>,
    excludedRoundIds: Set<Int>,
    onDismiss: () -> Unit,
    onToggleExclusion: (Int) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Include/Exclude Rounds") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Deselect rounds to exclude them from calculations. All active filters still apply.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                rounds.forEach { roundWithDetails ->
                    val round = roundWithDetails.round
                    val isIncluded = round.id !in excludedRoundIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleExclusion(round.id) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isIncluded,
                            onCheckedChange = { onToggleExclusion(round.id) }
                        )
                        Column {
                            Text(
                                "Round ${round.id} at ${roundWithDetails.course?.name ?: "Unknown Course"}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                dateFormat.format(round.date),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

// ── Scoring Tab ─────────────────────────────────────────────────────────

@Composable
fun ScoringTab(s: com.golftracker.data.repository.ScoringStats, sg: com.golftracker.data.repository.SgStats) {
    if (s.trend.isNotEmpty()) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = 16.dp)) {
                com.golftracker.ui.components.ScoringTrendChart(trendData = s.trend)
            }
        }
    }

    // Total Strokes Gained
    StatCard(
        title = "Total SG / Round", 
        value = String.format(java.util.Locale.US, "%+.2f", sg.totalSgPerRound)
    )
    
    // Handicap
    s.handicapIndex?.let { hcp ->
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Handicap Index", style = MaterialTheme.typography.labelMedium)
                Text(
                    String.format("%.1f", hcp),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }

    // Avg Score with distribution
    StatCardWithDistribution(
        title = "Average Score",
        value = String.format("%.1f", s.avgScore),
        moe = if (s.scoreMoE > 0) String.format("±%.1f", s.scoreMoE) else null,
        segments = if (s.scoreDistribution.isNotEmpty()) {
            // ... (rest of segments logic)
            val min = s.scoreDistribution.min().toInt()
            val max = s.scoreDistribution.max().toInt()
            val range = max - min
            if (range > 0) {
                val low = s.scoreDistribution.count { it <= min + range / 3.0 }
                val mid = s.scoreDistribution.count { it > min + range / 3.0 && it <= min + 2 * range / 3.0 }
                val high = s.scoreDistribution.count { it > min + 2 * range / 3.0 }
                val total = s.scoreDistribution.size.toDouble()
                listOf(
                    DistributionSegment("≤${min + range / 3}", (low / total) * 100, Color(0xFF4CAF50)),
                    DistributionSegment("Mid", (mid / total) * 100, Color(0xFFFFC107)),
                    DistributionSegment("≥${min + 2 * range / 3}", (high / total) * 100, Color(0xFFFF5722))
                )
            } else null
        } else null
    )

    StatCard(
        title = "Average To Par", 
        value = String.format("%+.1f", s.avgToPar),
        moe = if (s.toParMoE > 0) String.format("±%.1f", s.toParMoE) else null
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCard(title = "Best Round", value = if (s.bestRoundToPar > 0) "+${s.bestRoundToPar}" else "${s.bestRoundToPar}", modifier = Modifier.weight(1f))
        StatCard(title = "Worst Round", value = if (s.worstRoundToPar > 0) "+${s.worstRoundToPar}" else "${s.worstRoundToPar}", modifier = Modifier.weight(1f))
    }

    StatCard(title = "Rounds Played", value = s.roundsPlayed.toString())

    // Score breakdown distribution
    val totalHoles = s.eagles + s.birdies + s.pars + s.bogeyCount + s.doubles + s.worse
    if (totalHoles > 0) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Score Breakdown", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                DistributionBar(
                    segments = listOfNotNull(
                        if (s.eagles > 0) DistributionSegment("Eagle-", (s.eagles.toDouble() / totalHoles) * 100, Color(0xFF1B5E20)) else null,
                        if (s.birdies > 0) DistributionSegment("Birdie", (s.birdies.toDouble() / totalHoles) * 100, Color(0xFF4CAF50)) else null,
                        if (s.pars > 0) DistributionSegment("Par", (s.pars.toDouble() / totalHoles) * 100, Color(0xFF2196F3)) else null,
                        if (s.bogeyCount > 0) DistributionSegment("Bogey", (s.bogeyCount.toDouble() / totalHoles) * 100, Color(0xFFFFC107)) else null,
                        if (s.doubles > 0) DistributionSegment("Dbl", (s.doubles.toDouble() / totalHoles) * 100, Color(0xFFFF9800)) else null,
                        if (s.worse > 0) DistributionSegment("3+", (s.worse.toDouble() / totalHoles) * 100, Color(0xFFFF5722)) else null
                    )
                )
            }
        }
    }

    // By Par Breakdown
    if (s.byPar.isNotEmpty()) {
        ByParBreakdownCard(
            byPar = s.byPar,
            metrics = listOf(
                "Avg Score" to { it.avgScore },
                "To Par" to { it.avgToPar ?: 0.0 }
            ),
            formatters = listOf(
                { String.format("%.1f", it) },
                { String.format("%+.1f", it) }
            )
        )
    }
}

// ── Driving Tab ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrivingTab(
    d: DrivingStats,
    clubs: List<Club>,
    selectedClubId: Int?,
    onClubSelected: (Int?) -> Unit,
    sg: com.golftracker.data.repository.SgStats
) {
    // Club filter dropdown
    if (clubs.isNotEmpty()) {
        ClubFilterDropdown(
            clubs = clubs.filter { it.type == "DRIVER" || it.type == "WOOD" || it.type == "HYBRID" || it.type == "IRON" },
            selectedClubId = selectedClubId,
            onClubSelected = onClubSelected,
            label = "Filter by Club"
        )
        Spacer(modifier = Modifier.height(8.dp))
    }

    StatCard(
        title = "SG: Off the Tee", 
        value = String.format(java.util.Locale.US, "%+.2f", sg.sgOffTeePerRound)
    )

    StatCard(
        title = "Fairways Hit", 
        value = String.format("%.1f%%", d.fairwaysHitPct),
        moe = if (d.fairwaysHitMoE > 0) String.format("±%.1f%%", d.fairwaysHitMoE) else null
    )
    StatCard(
        title = "Average Distance", 
        value = String.format("%.0f yds", d.avgDistance),
        moe = if (d.distanceMoE > 0) String.format("±%.0f yds", d.distanceMoE) else null
    )
    
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCard(
            title = "Avg Dist (Clean)", 
            value = String.format("%.0f yds", d.avgDistanceExMishits),
            moe = if (d.distanceExMishitsMoE > 0) String.format("±%.0f yds", d.distanceExMishitsMoE) else null,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "Mishit Rate", 
            value = String.format("%.1f%%", d.mishitPct),
            moe = if (d.mishitMoE > 0) String.format("±%.1f%%", d.mishitMoE) else null,
            modifier = Modifier.weight(1f)
        )
    }

    StatCard(
        title = "Trouble-Free", 
        value = String.format("%.1f%%", d.troubleFreePct),
        moe = if (d.troubleFreeMoE > 0) String.format("±%.1f%%", d.troubleFreeMoE) else null
    )

    // 2D shot dispersion visual
    if (d.totalDrivingHoles > 0) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                ShotDispersionVisual(
                    onTargetPct = d.fairwaysHitPct,
                    missLeftPct = d.missLeftPct,
                    missRightPct = d.missRightPct,
                    missShortPct = d.missShortPct,
                    missLongPct = d.missLongPct
                )
            }
        }
    }
    
    // Raw Miss Dispersion Scatter Plot
    if (d.rawDispersion.points.size >= 5) {
        Spacer(modifier = Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                com.golftracker.ui.components.RawDispersionVisual(data = d.rawDispersion)
            }
        }
    }

    StatCard(title = "Driving Holes Tracked", value = d.totalDrivingHoles.toString())

    // By Par Breakdown
    if (d.byPar.isNotEmpty()) {
        ByParBreakdownCard(
            byPar = d.byPar.filterKeys { it > 3 }, // Only Par 4/5 for driving
            metrics = listOf(
                "Fairways" to { it.fairwaysHitPct ?: 0.0 },
                "Avg Dist" to { it.avgScore } // avgScore was used for distance in DrivingByPar
            ),
            formatters = listOf(
                { String.format("%.1f%%", it) },
                { String.format("%.0fy", it) }
            )
        )
    }
}

// ── Approach Tab ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApproachTab(
    a: ApproachStats,
    clubs: List<Club>,
    selectedClubId: Int?,
    onClubSelected: (Int?) -> Unit,
    sg: com.golftracker.data.repository.SgStats
) {
    // Club filter dropdown
    if (clubs.isNotEmpty()) {
        ClubFilterDropdown(
            clubs = clubs.filter { it.type != "DRIVER" && it.type != "PUTTER" },
            selectedClubId = selectedClubId,
            onClubSelected = onClubSelected,
            label = "Filter by Club"
        )
        Spacer(modifier = Modifier.height(8.dp))
    }

    StatCard(
        title = "SG: Approach", 
        value = String.format(java.util.Locale.US, "%+.2f", sg.sgApproachPerRound)
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCard(
            title = "GIR %", 
            value = String.format("%.1f%%", a.girPct),
            moe = if (a.girMoE > 0) String.format("±%.1f%%", a.girMoE) else null,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "Near-GIR %", 
            value = String.format("%.1f%%", a.nearGirPct),
            moe = if (a.nearGirMoE > 0) String.format("±%.1f%%", a.nearGirMoE) else null,
            modifier = Modifier.weight(1f)
        )
    }

    StatCard(
        title = "On Target %",
        value = String.format("%.1f%%", a.onTargetPct),
        moe = if (a.onTargetMoE > 0) String.format("±%.1f%%", a.onTargetMoE) else null
    )
    
    StatCard(
        title = "Avg Approach Distance", 
        value = String.format("%.0f yds", a.avgDistance),
        moe = if (a.distanceMoE > 0) String.format("±%.0f yds", a.distanceMoE) else null
    )

    // GIR by Lie
    if (a.girByLie.isNotEmpty() && a.countByLie.values.any { it > 0 }) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("GIR by Lie", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                ApproachLie.values().forEach { lie ->
                    val count = a.countByLie[lie] ?: 0
                    if (count > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(lie.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                String.format("%.0f%% (%d holes)", a.girByLie[lie] ?: 0.0, count),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }

    // On-target by distance range
    if (a.onTargetByRange.isNotEmpty() && a.onTargetByRange.any { it.sampleCount > 0 }) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("On Target by Distance", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                a.onTargetByRange.filter { it.sampleCount > 0 }.forEach { breakdown ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${breakdown.label} yds", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            String.format("%.0f%% (%d shots)", breakdown.onTargetPct, breakdown.sampleCount),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

    // On-target by lie
    if (a.onTargetByLie.isNotEmpty() && a.onTargetByLie.any { it.sampleCount > 0 }) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("On Target by Lie", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                a.onTargetByLie.filter { it.sampleCount > 0 }.forEach { breakdown ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(breakdown.label, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            String.format("%.0f%% (%d shots)", breakdown.onTargetPct, breakdown.sampleCount),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

    // 2D shot dispersion visual
    if (a.totalHoles > 0) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                ShotDispersionVisual(
                    onTargetPct = a.onTargetPct,
                    missLeftPct = a.missLeftPct,
                    missRightPct = a.missRightPct,
                    missShortPct = a.missShortPct,
                    missLongPct = a.missLongPct
                )
            }
        }
    }

    // Raw Miss Dispersion Scatter Plot
    if (a.rawDispersion.points.size >= 5) {
        Spacer(modifier = Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                com.golftracker.ui.components.RawDispersionVisual(data = a.rawDispersion)
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
    StatCard(title = "Approach Shots Tracked", value = a.totalShots.toString())

    // By Par Breakdown
    if (a.byPar.isNotEmpty()) {
        ByParBreakdownCard(
            byPar = a.byPar,
            metrics = listOf(
                "GIR %" to { it.girPct ?: 0.0 }
            ),
            formatters = listOf(
                { String.format("%.1f%%", it) }
            )
        )
    }
}

// ── Chipping Tab ────────────────────────────────────────────────────────

@Composable
fun ChippingTab(c: ChippingStats, sg: com.golftracker.data.repository.SgStats) {
    StatCard(
        title = "SG: Around the Green", 
        value = String.format(java.util.Locale.US, "%+.2f", sg.sgAroundGreenPerRound)
    )

    StatCard(
        title = "Up & Down %", 
        value = String.format("%.1f%%", c.upAndDownPct),
        moe = if (c.upAndDownMoE > 0) String.format("±%.1f%%", c.upAndDownMoE) else null
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCard(title = "U&D / Round", value = String.format("%.1f", c.upAndDownsPerRound), modifier = Modifier.weight(1f))
        StatCard(title = "Dbl Chips / Round", value = String.format("%.1f", c.doubleChipsPerRound), modifier = Modifier.weight(1f))
    }

    StatCard(
        title = "Par Save %", 
        value = String.format("%.1f%%", c.parSavePct),
        moe = if (c.parSaveMoE > 0) String.format("±%.1f%%", c.parSaveMoE) else null
    )
    StatCard(title = "Par Saves / Round", value = String.format("%.1f", c.parSavesPerRound))

    StatCard(
        title = "Avg Chips / Hole", 
        value = String.format("%.2f", c.chipsPerHole),
        moe = if (c.chipsMoE > 0) String.format("±%.2f", c.chipsMoE) else null
    )
    StatCard(
        title = "Sand Save %", 
        value = String.format("%.1f%%", c.sandSavePct),
        moe = if (c.sandSaveMoE > 0) String.format("±%.1f%%", c.sandSaveMoE) else null
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCard(title = "Missed GIR Holes", value = c.totalMissedGir.toString(), modifier = Modifier.weight(1f))
        StatCard(title = "Sand Holes", value = c.totalSandHoles.toString(), modifier = Modifier.weight(1f))
    }

    // Proximity
    StatCard(
        title = "Avg Proximity", 
        value = if (c.avgProximity > 0) String.format("%.1f ft", c.avgProximity) else "-"
    )

    if (c.proximityByLie.isNotEmpty()) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Proximity by Lie", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                ApproachLie.values().forEach { lie ->
                    val prox = c.proximityByLie[lie]
                    if (prox != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(lie.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                String.format("%.1f ft", prox),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Putting Tab ─────────────────────────────────────────────────────────

@Composable
fun PuttingTab(p: PuttingStats, sg: com.golftracker.data.repository.SgStats) {
    StatCard(
        title = "SG: Putting", 
        value = String.format(java.util.Locale.US, "%+.2f", sg.sgPuttingPerRound)
    )

    // Avg putts with distribution
    StatCardWithDistribution(
        title = "Avg Putts / Round",
        value = String.format("%.1f", p.avgPuttsPerRound),
        moe = if (p.puttsMoE > 0) String.format("±%.1f", p.puttsMoE) else null,
        segments = if (p.puttsPerRoundDistribution.isNotEmpty() && p.puttsPerRoundDistribution.size > 1) {
            // ... (rest of segments logic)
            val min = p.puttsPerRoundDistribution.min().toInt()
            val max = p.puttsPerRoundDistribution.max().toInt()
            val range = max - min
            if (range > 0) {
                val low = p.puttsPerRoundDistribution.count { it <= min + range / 3.0 }
                val mid = p.puttsPerRoundDistribution.count { it > min + range / 3.0 && it <= min + 2 * range / 3.0 }
                val high = p.puttsPerRoundDistribution.count { it > min + 2 * range / 3.0 }
                val total = p.puttsPerRoundDistribution.size.toDouble()
                listOf(
                    DistributionSegment("≤${ min + range / 3}", (low / total) * 100, Color(0xFF4CAF50)),
                    DistributionSegment("Mid", (mid / total) * 100, Color(0xFFFFC107)),
                    DistributionSegment("≥${min + 2 * range / 3}", (high / total) * 100, Color(0xFFFF5722))
                )
            } else null
        } else null
    )

    StatCard(
        title = "Total Putts Tracked",
        value = p.totalPutts.toString()
    )

    StatCard(
        title = "Putts per GIR", 
        value = String.format("%.2f", p.puttsPerGir),
        moe = if (p.puttsPerGirMoE > 0) String.format("±%.2f", p.puttsPerGirMoE) else null
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCard(
            title = "Avg 1st Putt Dist", 
            value = String.format("%.0f ft", p.avgFirstPuttDistance), 
            moe = if (p.firstPuttDistMoE > 0) String.format("±%.1f ft", p.firstPuttDistMoE) else null,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "Avg Make Dist", 
            value = String.format("%.0f ft", p.avgMakePuttDistance), 
            moe = if (p.makePuttDistMoE > 0) String.format("±%.1f ft", p.makePuttDistMoE) else null,
            modifier = Modifier.weight(1f)
        )
    }

    // Putt count distribution
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Putt Count Distribution", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            DistributionBar(
                segments = listOf(
                    DistributionSegment("1-Putt", p.onePuttPct, Color(0xFF4CAF50)),
                    DistributionSegment("2-Putt", p.twoPuttPct, Color(0xFF2196F3)),
                    DistributionSegment("3+", p.threePlusPuttPct, Color(0xFFFF5722))
                )
            )
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCard(title = "1-Putts / Round", value = String.format("%.1f", p.onePuttsPerRound), modifier = Modifier.weight(1f))
        StatCard(title = "2-Putts / Round", value = String.format("%.1f", p.twoPuttsPerRound), modifier = Modifier.weight(1f))
        StatCard(title = "3+ Putts / Round", value = String.format("%.1f", p.threePlusPuttsPerRound), modifier = Modifier.weight(1f))
    }
}

// ── Strokes Gained Tab ──────────────────────────────────────────────────

@Composable
fun SgTab(sg: com.golftracker.data.repository.SgStats) {
    StatCard(
        title = "Total Strokes Gained / Round", 
        value = String.format(java.util.Locale.US, "%+.2f", sg.totalSgPerRound)
    )
    
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCard(title = "Off the Tee", value = String.format(java.util.Locale.US, "%+.2f", sg.sgOffTeePerRound), modifier = Modifier.weight(1f))
        StatCard(title = "Approach", value = String.format(java.util.Locale.US, "%+.2f", sg.sgApproachPerRound), modifier = Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCard(title = "Around Green", value = String.format(java.util.Locale.US, "%+.2f", sg.sgAroundGreenPerRound), modifier = Modifier.weight(1f))
        StatCard(title = "Putting", value = String.format(java.util.Locale.US, "%+.2f", sg.sgPuttingPerRound), modifier = Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCard(title = "Penalties", value = String.format(java.util.Locale.US, "%+.2f", sg.sgPenaltiesPerRound), modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.weight(1f))
    }
    
    // SG By Lie
    if (sg.sgByLie.isNotEmpty()) {
        Card(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("SG by Starting Lie", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                sg.sgByLie.forEach { (lie, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(lie.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            String.format(java.util.Locale.US, "%+.2f", value),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (value > 0) MaterialTheme.colorScheme.primary else if (value < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
    
    // SG By Distance
    if (sg.sgByDistance.isNotEmpty()) {
        Card(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("SG by Distance", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                val orderedDistances = listOf("<30y", "30-100y", "100-150y", "150-200y", "200y+")
                orderedDistances.forEach { dist ->
                    val value = sg.sgByDistance[dist]
                    if (value != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(dist, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                String.format(java.util.Locale.US, "%+.2f", value),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (value > 0) MaterialTheme.colorScheme.primary else if (value < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ByParBreakdownCard(
    byPar: Map<Int, com.golftracker.data.repository.ParBreakdown>,
    metrics: List<Pair<String, (com.golftracker.data.repository.ParBreakdown) -> Double>>,
    formatters: List<(Double) -> String>
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Breakdown by Par", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            
            // Header
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                Text("Hole", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                metrics.forEach { (name, _) ->
                    Text(
                        name, 
                        style = MaterialTheme.typography.labelSmall, 
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                    )
                }
            }
            
            Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
            
            byPar.keys.sorted().forEach { par ->
                val stats = byPar[par]!!
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Par $par", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    metrics.forEachIndexed { index, (_, selector) ->
                        val value = selector(stats)
                        Text(
                            formatters[index](value),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                        )
                    }
                }
            }
        }
    }
}

// ── Club Filter Dropdown ────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClubFilterDropdown(
    clubs: List<Club>,
    selectedClubId: Int?,
    onClubSelected: (Int?) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedClub = clubs.firstOrNull { it.id == selectedClubId }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedClub?.let {
                "${it.name}${it.stockDistance?.let { d -> " ($d yds)" } ?: ""}"
            } ?: "All Clubs",
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
                text = { Text("All Clubs") },
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

