package com.golftracker.ui.courseanalysis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.golftracker.data.entity.PuttBreak
import com.golftracker.data.entity.PuttSlopeDirection
import com.golftracker.data.repository.DistanceBucket
import com.golftracker.ui.components.DistributionBar
import com.golftracker.ui.components.DistributionSegment
import com.golftracker.ui.components.ScoringTrendChart
import com.golftracker.ui.components.ShotDispersionVisual
import com.golftracker.ui.components.StatCard
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs

// Shared score palette
private val ColorEagle = Color(0xFF9C27B0)
private val ColorBirdie = Color(0xFF4CAF50)
private val ColorPar = Color(0xFF2196F3)
private val ColorBogey = Color(0xFFFF9800)
private val ColorDouble = Color(0xFFF44336)
private val ColorGreen = Color(0xFF4CAF50)
private val ColorRed = Color(0xFFF44336)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseAnalysisScreen(
    onNavigateBack: () -> Unit,
    viewModel: CourseAnalysisViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val filter by viewModel.filter.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Overview", "By Hole", "SG by Hole", "Misses", "Putting", "Tee Clubs")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Course Analysis") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val state = uiState) {
                is CourseAnalysisUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is CourseAnalysisUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Error: ${state.message}")
                    }
                }
                is CourseAnalysisUiState.Success -> {
                    val data = state.data
                    val roundCount = data.overall.roundsPlayed

                    // Course header
                    CourseHeaderCard(data, filter)

                    // Filter row
                    CourseFilterRow(
                        data = data,
                        filter = filter,
                        onTeeSetSelected = { viewModel.updateTeeSetFilter(it) },
                        onYearSelected = { viewModel.updateYearFilter(it) },
                        onClearFilters = { viewModel.clearFilters() },
                        roundCount = roundCount
                    )

                    // Tabs
                    ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
                        tabs.forEachIndexed { i, title ->
                            Tab(selected = selectedTab == i, onClick = { selectedTab = i }, text = { Text(title) })
                        }
                    }

                    // Tab content
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (roundCount == 0) {
                            Box(modifier = Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                                Text("No rounds recorded for this course with the current filters.")
                            }
                        } else {
                            when (selectedTab) {
                                0 -> OverviewTab(data.overall)
                                1 -> ByHoleTab(data.byHole)
                                2 -> SgByHoleTab(data.holeSgBreakdowns)
                                3 -> MissesTab(data.missStats)
                                4 -> PuttingTab(data.putting)
                                5 -> TeeClubsTab(data.clubTeeStats, data.holeClubBreakdowns)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

// ── Course Header ────────────────────────────────────────────────────────

@Composable
private fun CourseHeaderCard(data: CourseAnalysisData, filter: CourseAnalysisFilter) {
    val course = data.course
    val selectedTeeSet = data.teeSets.firstOrNull { it.id == filter.teeSetId }
        ?: data.teeSets.firstOrNull()
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(course.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "${course.city}, ${course.state}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (selectedTeeSet != null && selectedTeeSet.rating > 0.0) {
                Text(
                    "${selectedTeeSet.name} · Rating ${selectedTeeSet.rating} · Slope ${selectedTeeSet.slope}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Filter Row ───────────────────────────────────────────────────────────

@Composable
private fun CourseFilterRow(
    data: CourseAnalysisData,
    filter: CourseAnalysisFilter,
    onTeeSetSelected: (Int?) -> Unit,
    onYearSelected: (Int?) -> Unit,
    onClearFilters: () -> Unit,
    roundCount: Int
) {
    var teeSetExpanded by remember { mutableStateOf(false) }
    var yearExpanded by remember { mutableStateOf(false) }
    val hasFilter = filter.teeSetId != null || filter.year != null || filter.startDate != null || filter.endDate != null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Tee Set picker
        if (data.teeSets.isNotEmpty()) {
            Box {
                FilterChip(
                    selected = filter.teeSetId != null,
                    onClick = { teeSetExpanded = true },
                    label = {
                        Text(
                            data.teeSets.firstOrNull { it.id == filter.teeSetId }?.name ?: "All Tees",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )
                DropdownMenu(expanded = teeSetExpanded, onDismissRequest = { teeSetExpanded = false }) {
                    DropdownMenuItem(text = { Text("All Tees") }, onClick = { onTeeSetSelected(null); teeSetExpanded = false })
                    data.teeSets.forEach { ts ->
                        DropdownMenuItem(text = { Text(ts.name) }, onClick = { onTeeSetSelected(ts.id); teeSetExpanded = false })
                    }
                }
            }
        }

        // Year picker
        if (data.availableYears.isNotEmpty()) {
            Box {
                FilterChip(
                    selected = filter.year != null,
                    onClick = { yearExpanded = true },
                    label = {
                        Text(
                            filter.year?.toString() ?: "All Years",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )
                DropdownMenu(expanded = yearExpanded, onDismissRequest = { yearExpanded = false }) {
                    DropdownMenuItem(text = { Text("All Years") }, onClick = { onYearSelected(null); yearExpanded = false })
                    data.availableYears.forEach { year ->
                        DropdownMenuItem(text = { Text(year.toString()) }, onClick = { onYearSelected(year); yearExpanded = false })
                    }
                }
            }
        }

        // Clear button
        if (hasFilter) {
            TextButton(onClick = onClearFilters) {
                Text("Clear", style = MaterialTheme.typography.labelSmall)
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        Text(
            "$roundCount round${if (roundCount != 1) "s" else ""}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Overview Tab ─────────────────────────────────────────────────────────

@Composable
private fun OverviewTab(overall: OverallScoringStats) {
    val toParStr = { v: Double -> if (v >= 0) "+${String.format("%.1f", v)}" else String.format("%.1f", v) }
    val intToParStr = { v: Int -> if (v >= 0) "+$v" else "$v" }

    // Headline stats
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCard(title = "Rounds", value = "${overall.roundsPlayed}", modifier = Modifier.weight(1f))
        StatCard(title = "Avg Score", value = String.format("%.1f", overall.avgScore), modifier = Modifier.weight(1f))
        StatCard(title = "Avg To Par", value = toParStr(overall.avgToPar), modifier = Modifier.weight(1f))
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCard(title = "Best Round", value = intToParStr(overall.bestRoundToPar), modifier = Modifier.weight(1f))
        StatCard(title = "Worst Round", value = intToParStr(overall.worstRoundToPar), modifier = Modifier.weight(1f))
    }

    // Score distribution
    val totalHoles = overall.eagles + overall.birdies + overall.pars + overall.bogeys + overall.doubles + overall.worseCount
    if (totalHoles > 0) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Score Distribution", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                DistributionBar(
                    segments = listOf(
                        DistributionSegment("Eagle", (overall.eagles.toDouble() / totalHoles) * 100, ColorEagle),
                        DistributionSegment("Birdie", (overall.birdies.toDouble() / totalHoles) * 100, ColorBirdie),
                        DistributionSegment("Par", (overall.pars.toDouble() / totalHoles) * 100, ColorPar),
                        DistributionSegment("Bogey", (overall.bogeys.toDouble() / totalHoles) * 100, ColorBogey),
                        DistributionSegment("Double+", ((overall.doubles + overall.worseCount).toDouble() / totalHoles) * 100, ColorDouble)
                    ).filter { it.value > 0 }
                )
                // Count row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ScoreCountLabel("Eagles", overall.eagles, ColorEagle)
                    ScoreCountLabel("Birdies", overall.birdies, ColorBirdie)
                    ScoreCountLabel("Pars", overall.pars, ColorPar)
                    ScoreCountLabel("Bogeys", overall.bogeys, ColorBogey)
                    ScoreCountLabel("D+", overall.doubles + overall.worseCount, ColorDouble)
                }
            }
        }
    }

    // Trend chart
    if (overall.trend.size >= 2) {
        ScoringTrendChart(trendData = overall.trend)
    }
}

@Composable
private fun ScoreCountLabel(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "$count", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── By Hole Tab ──────────────────────────────────────────────────────────

private enum class ByHoleSort { HOLE_NUMBER, AVG_SCORE, BIRDIE_COUNT }

@Composable
private fun ByHoleTab(holes: List<HoleAnalysis>) {
    if (holes.isEmpty()) {
        Text("No hole data available.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    var sort by remember { mutableStateOf(ByHoleSort.HOLE_NUMBER) }
    val sorted = when (sort) {
        ByHoleSort.HOLE_NUMBER -> holes.sortedBy { it.holeNumber }
        ByHoleSort.AVG_SCORE -> holes.sortedBy { it.avgScore - it.par }
        ByHoleSort.BIRDIE_COUNT -> holes.sortedByDescending { it.birdies }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SortChip("Hole #", sort == ByHoleSort.HOLE_NUMBER) { sort = ByHoleSort.HOLE_NUMBER }
        SortChip("Avg Score", sort == ByHoleSort.AVG_SCORE) { sort = ByHoleSort.AVG_SCORE }
        SortChip("Birdies", sort == ByHoleSort.BIRDIE_COUNT) { sort = ByHoleSort.BIRDIE_COUNT }
    }

    sorted.forEach { hole -> HoleAnalysisRow(hole) }
}

@Composable
private fun HoleAnalysisRow(hole: HoleAnalysis) {
    val avgToPar = hole.avgScore - hole.par
    val avgColor = when {
        avgToPar < -0.1 -> ColorGreen
        avgToPar > 0.1 -> ColorRed
        else -> MaterialTheme.colorScheme.onSurface
    }
    val totalRounds = hole.roundsPlayed.coerceAtLeast(1)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Header
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Hole ${hole.holeNumber}  Par ${hole.par}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "(${hole.roundsPlayed} rounds)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Avg / Median
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Avg: ${String.format("%.2f", hole.avgScore)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = avgColor,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Median: ${String.format("%.1f", hole.medianScore)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Score distribution mini bar
            val total = (hole.eagles + hole.birdies + hole.pars + hole.bogeys + hole.doubles + hole.worseCount).toDouble()
            if (total > 0) {
                DistributionBar(
                    segments = listOf(
                        DistributionSegment("E", (hole.eagles / total) * 100, ColorEagle),
                        DistributionSegment("B", (hole.birdies / total) * 100, ColorBirdie),
                        DistributionSegment("P", (hole.pars / total) * 100, ColorPar),
                        DistributionSegment("Bo", (hole.bogeys / total) * 100, ColorBogey),
                        DistributionSegment("D+", ((hole.doubles + hole.worseCount) / total) * 100, ColorDouble)
                    ).filter { it.value > 0 }
                )
            }
            // Count row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                HoleCountCell("E", hole.eagles, ColorEagle, highlight = hole.eagles > 0)
                HoleCountCell("B", hole.birdies, ColorBirdie, highlight = hole.birdies > 0)
                HoleCountCell("P", hole.pars, ColorPar)
                HoleCountCell("Bo", hole.bogeys, ColorBogey)
                HoleCountCell("D+", hole.doubles + hole.worseCount, ColorDouble)
            }
        }
    }
}

@Composable
private fun HoleCountCell(label: String, count: Int, color: Color, highlight: Boolean = false) {
    val bg = if (highlight) color.copy(alpha = 0.15f) else Color.Transparent
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "$count", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = color)
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── SG by Hole Tab ───────────────────────────────────────────────────────

private enum class SgSort { HOLE_NUMBER, TOTAL_SG, OFF_TEE, APPROACH, AROUND_GREEN, PUTTING }

@Composable
private fun SgByHoleTab(breakdowns: List<HoleSgBreakdown>) {
    if (breakdowns.isEmpty()) {
        Text(
            "No strokes gained data yet. SG is calculated after rounds are finalized.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    var sort by remember { mutableStateOf(SgSort.HOLE_NUMBER) }
    val sorted = when (sort) {
        SgSort.HOLE_NUMBER -> breakdowns.sortedBy { it.holeNumber }
        SgSort.TOTAL_SG -> breakdowns.sortedByDescending { it.avgSgTotal }
        SgSort.OFF_TEE -> breakdowns.filter { it.avgSgOffTee != null }.sortedByDescending { it.avgSgOffTee!! } +
                breakdowns.filter { it.avgSgOffTee == null }
        SgSort.APPROACH -> breakdowns.sortedByDescending { it.avgSgApproach }
        SgSort.AROUND_GREEN -> breakdowns.sortedByDescending { it.avgSgAroundGreen }
        SgSort.PUTTING -> breakdowns.sortedByDescending { it.avgSgPutting }
    }

    // Sort chips row 1
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SortChip("Hole #", sort == SgSort.HOLE_NUMBER) { sort = SgSort.HOLE_NUMBER }
        SortChip("Total SG", sort == SgSort.TOTAL_SG) { sort = SgSort.TOTAL_SG }
        SortChip("Off Tee", sort == SgSort.OFF_TEE) { sort = SgSort.OFF_TEE }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SortChip("Approach", sort == SgSort.APPROACH) { sort = SgSort.APPROACH }
        SortChip("Around Green", sort == SgSort.AROUND_GREEN) { sort = SgSort.AROUND_GREEN }
        SortChip("Putting", sort == SgSort.PUTTING) { sort = SgSort.PUTTING }
    }

    // Scale for the component bar — find max absolute value across all components
    val maxAbs = breakdowns.flatMap {
        listOfNotNull(
            abs(it.avgSgTotal),
            it.avgSgOffTee?.let { v -> abs(v) },
            abs(it.avgSgApproach),
            abs(it.avgSgAroundGreen),
            abs(it.avgSgPutting)
        )
    }.maxOrNull()?.coerceAtLeast(0.01) ?: 1.0

    sorted.forEach { hole -> SgHoleRow(hole, maxAbs) }
}

@Composable
private fun SgHoleRow(hole: HoleSgBreakdown, maxAbs: Double) {
    val totalColor = if (hole.avgSgTotal >= 0) ColorGreen else ColorRed

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Header
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Hole ${hole.holeNumber}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "  Par ${hole.par}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "  Scratch: ${String.format("%.2f", hole.avgExpectedStrokes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "(${hole.roundsPlayed} rounds)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Total SG
            Text(
                "Total SG: ${sgFormatted(hole.avgSgTotal)}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = totalColor
            )
            // Component columns
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hole.avgSgOffTee != null) {
                    SgComponentCell("Off Tee", hole.avgSgOffTee, modifier = Modifier.weight(1f))
                } else {
                    Box(modifier = Modifier.weight(1f))
                }
                SgComponentCell("Approach", hole.avgSgApproach, modifier = Modifier.weight(1f))
                SgComponentCell("Around\nGreen", hole.avgSgAroundGreen, modifier = Modifier.weight(1f))
                SgComponentCell("Putting", hole.avgSgPutting, modifier = Modifier.weight(1f))
            }
            // Component bar
            SgComponentBar(hole, maxAbs)
        }
    }
}

@Composable
private fun SgComponentCell(label: String, value: Double, modifier: Modifier = Modifier) {
    val color = if (value >= 0) ColorGreen else ColorRed
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = sgFormatted(value),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = color,
            textAlign = TextAlign.Center
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SgComponentBar(hole: HoleSgBreakdown, maxAbs: Double) {
    val components = listOfNotNull(
        hole.avgSgOffTee?.let { "Off Tee" to it },
        "Approach" to hole.avgSgApproach,
        "Around" to hole.avgSgAroundGreen,
        "Putting" to hole.avgSgPutting
    )
    val totalWidth = components.sumOf { abs(it.second) }.coerceAtLeast(0.01)

    Canvas(modifier = Modifier.fillMaxWidth().height(10.dp)) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val scale = (w / 2f) / maxAbs.toFloat()
        var xPos = cx

        components.forEach { (_, value) ->
            val barW = (abs(value) * scale).toFloat()
            val color = if (value >= 0) ColorGreen.copy(alpha = 0.8f) else ColorRed.copy(alpha = 0.8f)
            if (value >= 0) {
                drawRect(color = color, topLeft = Offset(xPos, 0f), size = androidx.compose.ui.geometry.Size(barW, h))
                xPos += barW
            } else {
                drawRect(color = color, topLeft = Offset(xPos - barW, 0f), size = androidx.compose.ui.geometry.Size(barW, h))
                xPos -= barW
            }
        }
        // Center line
        drawLine(color = Color.Gray.copy(alpha = 0.5f), start = Offset(cx, 0f), end = Offset(cx, h), strokeWidth = 1.5f)
    }
}

private fun sgFormatted(v: Double) = if (v >= 0) "+${String.format("%.2f", v)}" else String.format("%.2f", v)

// ── Misses Tab ───────────────────────────────────────────────────────────

@Composable
private fun MissesTab(missStats: CourseMissStats) {
    MissSectionHeader("Tee Shots")
    MissSection(missStats.tee, showScatter = true)

    MissSectionHeader("Approach Shots")
    MissSection(missStats.approach, showScatter = true)

    MissSectionHeader("Chips")
    MissSection(missStats.chip, showScatter = false)
}

@Composable
private fun MissSectionHeader(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun MissSection(miss: MissBreakdown, showScatter: Boolean) {
    if (miss.totalShots == 0) {
        Text("No data", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }

    Text(
        "${miss.totalShots} shots tracked",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    // Direction visual
    ShotDispersionVisual(
        onTargetPct = miss.onTargetPct.toDouble(),
        missLeftPct = miss.leftPct.toDouble(),
        missRightPct = miss.rightPct.toDouble(),
        missShortPct = miss.shortPct.toDouble(),
        missLongPct = miss.longPct.toDouble()
    )

    // Direction bar
    val missTotal = (miss.leftPct + miss.rightPct + miss.shortPct + miss.longPct).coerceAtLeast(0.01f)
    if (missTotal > 0 && (miss.leftPct + miss.rightPct + miss.shortPct + miss.longPct) > 0) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Miss Direction", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                DistributionBar(
                    segments = listOf(
                        DistributionSegment("Left ${String.format("%.0f", miss.leftPct)}%", miss.leftPct.toDouble(), Color(0xFF9C27B0)),
                        DistributionSegment("Right ${String.format("%.0f", miss.rightPct)}%", miss.rightPct.toDouble(), Color(0xFF2196F3)),
                        DistributionSegment("Short ${String.format("%.0f", miss.shortPct)}%", miss.shortPct.toDouble(), Color(0xFFFF9800)),
                        DistributionSegment("Long ${String.format("%.0f", miss.longPct)}%", miss.longPct.toDouble(), Color(0xFF4CAF50))
                    ).filter { it.value > 0 }
                )
            }
        }
    }

    // Avg dispersion card (when we have scatter data)
    if (showScatter && miss.dispersionPoints.isNotEmpty()) {
        com.golftracker.ui.components.DispersionCard(
            title = "Avg Miss",
            avgLateral = miss.avgLateral,
            avgDistance = miss.avgDistance
        )
    }
}

// ── Putting Tab ──────────────────────────────────────────────────────────

@Composable
private fun PuttingTab(putting: CoursePuttingStats) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCard(
            title = "Avg Putts/Rnd",
            value = String.format("%.1f", putting.avgPuttsPerRound),
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "1-Putt%",
            value = String.format("%.0f%%", putting.onePuttPct),
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "3-Putt%",
            value = String.format("%.0f%%", putting.threePlusPct),
            modifier = Modifier.weight(1f)
        )
    }

    if (putting.avgFirstPuttDistance > 0) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Avg 1st Putt Distance", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(
                    String.format("%.1f ft", putting.avgFirstPuttDistance),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // Make% by distance
    if (putting.makePctByDistance.isNotEmpty()) {
        MakePctByDistanceCard(putting.makePctByDistance)
    }

    // Putt distribution
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Putt Distribution", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            DistributionBar(
                segments = listOf(
                    DistributionSegment("1-Putt", putting.onePuttPct.toDouble(), ColorGreen),
                    DistributionSegment("2-Putt", putting.twoPuttPct.toDouble(), ColorPar),
                    DistributionSegment("3-Putt+", putting.threePlusPct.toDouble(), ColorRed)
                ).filter { it.value > 0 }
            )
        }
    }

    // Miss direction
    if (putting.totalMissedPutts > 0) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Miss Patterns (${putting.totalMissedPutts} missed putts)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text("Pace", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                DistributionBar(
                    segments = listOf(
                        DistributionSegment("Short ${String.format("%.0f", putting.missShortPct)}%", putting.missShortPct.toDouble(), Color(0xFFFF9800)),
                        DistributionSegment("Long ${String.format("%.0f", putting.missLongPct)}%", putting.missLongPct.toDouble(), Color(0xFF9C27B0))
                    ).filter { it.value > 0 }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("Direction", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                DistributionBar(
                    segments = listOf(
                        DistributionSegment("Left ${String.format("%.0f", putting.missLeftPct)}%", putting.missLeftPct.toDouble(), Color(0xFF9C27B0)),
                        DistributionSegment("Right ${String.format("%.0f", putting.missRightPct)}%", putting.missRightPct.toDouble(), Color(0xFF2196F3))
                    ).filter { it.value > 0 }
                )
            }
        }
    }

    // Break and slope analysis
    if (putting.breakStats.isNotEmpty()) {
        PuttBreakCard(putting.breakStats)
    }
    if (putting.slopeStats.isNotEmpty()) {
        PuttSlopeCard(putting.slopeStats)
    }
}

@Composable
private fun MakePctByDistanceCard(buckets: List<DistanceBucket>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Make % by Distance", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            buckets.forEach { bucket ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(bucket.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(72.dp))
                    LinearProgressIndicator(
                        progress = { (bucket.makePct / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = when {
                            bucket.makePct >= 80 -> ColorGreen
                            bucket.makePct >= 50 -> ColorPar
                            else -> ColorBogey
                        },
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Text(
                        String.format("  %.0f%%", bucket.makePct),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(44.dp),
                        textAlign = TextAlign.End
                    )
                    Text(
                        " (${bucket.attempts})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ── Putt Break / Slope ───────────────────────────────────────────────────

@Composable
private fun PuttBreakCard(breakStats: List<PuttBreakStats>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Break Direction", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                breakStats.forEach { stat ->
                    PuttConditionCell(
                        label = breakLabel(stat.breakDir),
                        count = stat.count,
                        makePct = stat.makePct
                    )
                }
            }
        }
    }
}

@Composable
private fun PuttSlopeCard(slopeStats: List<PuttSlopeStats>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Green Slope", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                slopeStats.forEach { stat ->
                    PuttConditionCell(
                        label = slopeLabel(stat.slope),
                        count = stat.count,
                        makePct = stat.makePct
                    )
                }
            }
        }
    }
}

@Composable
private fun PuttConditionCell(label: String, count: Int, makePct: Float) {
    val makeColor = when {
        makePct >= 70 -> ColorGreen
        makePct >= 40 -> ColorPar
        else -> ColorRed
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Text(
            "${String.format("%.0f", makePct)}%",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = makeColor
        )
        Text(
            "$count putts",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun breakLabel(b: PuttBreak) = when (b) {
    PuttBreak.BIG_LEFT -> "Big\nLeft"
    PuttBreak.SMALL_LEFT -> "Left"
    PuttBreak.STRAIGHT -> "Straight"
    PuttBreak.SMALL_RIGHT -> "Right"
    PuttBreak.BIG_RIGHT -> "Big\nRight"
}

private fun slopeLabel(s: PuttSlopeDirection) = when (s) {
    PuttSlopeDirection.STEEP_UPHILL -> "Steep\nUphill"
    PuttSlopeDirection.UPHILL -> "Uphill"
    PuttSlopeDirection.FLAT -> "Flat"
    PuttSlopeDirection.DOWNHILL -> "Downhill"
    PuttSlopeDirection.STEEP_DOWNHILL -> "Steep\nDown"
}

// ── Tee Clubs Tab ────────────────────────────────────────────────────────

@Composable
private fun TeeClubsTab(
    clubTeeStats: List<ClubTeeStats>,
    holeClubBreakdowns: List<HoleClubBreakdown>
) {
    if (clubTeeStats.isEmpty()) {
        Text(
            "No tee club data yet. Record the club used on each par 4/5 during round tracking.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    // Overall summary per club
    Text("Overall by Club", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    clubTeeStats.forEach { club -> ClubSummaryCard(club) }

    // Per-hole breakdown (only show holes with >1 club used)
    val multiClubHoles = holeClubBreakdowns.filter { it.byClub.size > 1 }
    if (multiClubHoles.isNotEmpty()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text("Hole by Hole", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        multiClubHoles.forEach { hole -> HoleClubCard(hole) }
    }

    // Single-club holes as a compact table
    val singleClubHoles = holeClubBreakdowns.filter { it.byClub.size == 1 }
    if (singleClubHoles.isNotEmpty()) {
        Spacer(modifier = Modifier.height(4.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Single Club Holes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                singleClubHoles.forEach { hole ->
                    val club = hole.byClub.first()
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Hole ${hole.holeNumber} (Par ${hole.par})",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.width(110.dp)
                        )
                        Text(
                            club.clubName,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${club.holesPlayed}×  ${toParStr(club.avgToPar)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (club.avgToPar <= 0) ColorGreen else ColorRed
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClubSummaryCard(club: ClubTeeStats) {
    val toParColor = if (club.avgToPar <= 0) ColorGreen else ColorRed
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(club.clubName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    "${club.holesPlayed} holes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Key stats row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ClubStatCell("Avg To Par", toParStr(club.avgToPar), toParColor, modifier = Modifier.weight(1f))
                if (club.avgSgOffTee != null) {
                    ClubStatCell("SG Off Tee", sgFormatted(club.avgSgOffTee), if (club.avgSgOffTee >= 0) ColorGreen else ColorRed, modifier = Modifier.weight(1f))
                }
                ClubStatCell("SG Total", sgFormatted(club.avgSgTotal), if (club.avgSgTotal >= 0) ColorGreen else ColorRed, modifier = Modifier.weight(1f))
                ClubStatCell("FWY%", "${String.format("%.0f", club.onTargetPct)}%",
                    if (club.onTargetPct >= 60) ColorGreen else if (club.onTargetPct >= 40) ColorPar else ColorRed,
                    modifier = Modifier.weight(1f))
            }
            // Outcome distribution
            val outcomeDenom = club.onTargetPct + club.missLeftPct + club.missRightPct + club.missShortPct + club.missLongPct
            if (outcomeDenom > 0) {
                DistributionBar(
                    segments = listOf(
                        DistributionSegment("FWY ${String.format("%.0f", club.onTargetPct)}%", club.onTargetPct.toDouble(), ColorGreen),
                        DistributionSegment("Left ${String.format("%.0f", club.missLeftPct)}%", club.missLeftPct.toDouble(), Color(0xFF9C27B0)),
                        DistributionSegment("Right ${String.format("%.0f", club.missRightPct)}%", club.missRightPct.toDouble(), Color(0xFF2196F3)),
                        DistributionSegment("Short ${String.format("%.0f", club.missShortPct)}%", club.missShortPct.toDouble(), Color(0xFFFF9800)),
                        DistributionSegment("Long ${String.format("%.0f", club.missLongPct)}%", club.missLongPct.toDouble(), Color(0xFF795548))
                    ).filter { it.value > 0 }
                )
            }
            if (club.mishitPct > 0) {
                Text(
                    "Mishit: ${String.format("%.0f", club.mishitPct)}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = ColorBogey
                )
            }
        }
    }
}

@Composable
private fun HoleClubCard(hole: HoleClubBreakdown) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Hole ${hole.holeNumber}  Par ${hole.par}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            hole.byClub.forEach { club ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(club.clubName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(90.dp))
                    Text(
                        "${club.holesPlayed}×",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(26.dp)
                    )
                    Text(
                        toParStr(club.avgToPar),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (club.avgToPar <= 0) ColorGreen else ColorRed,
                        modifier = Modifier.width(40.dp)
                    )
                    if (club.avgSgOffTee != null) {
                        Text(
                            "SG OT: ${sgFormatted(club.avgSgOffTee)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (club.avgSgOffTee >= 0) ColorGreen else ColorRed
                        )
                    }
                    Text(
                        "FWY: ${String.format("%.0f", club.onTargetPct)}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (club.onTargetPct >= 60) ColorGreen else ColorRed,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}

@Composable
private fun ClubStatCell(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = color, textAlign = TextAlign.Center)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

private fun toParStr(v: Double) = if (v >= 0) "+${String.format("%.1f", v)}" else String.format("%.1f", v)

// ── Shared utils ─────────────────────────────────────────────────────────

@Composable
private fun SortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label, style = MaterialTheme.typography.labelSmall) })
}
