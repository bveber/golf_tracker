# Course Scoring Analysis

## Overview

The Course Scoring Analysis feature adds a dedicated screen for per-course performance breakdowns, accessible from two entry points:

1. A new **"Courses" tab** (7th tab) in the Stats Dashboard — shows a list of all courses the user has played (with round counts), tapping any course opens the full analysis.
2. A **stats icon button** on each row in the Course List screen for courses that have at least one finalized round.

Both entry points navigate to `courseAnalysis/{courseId}`, a full-screen destination with its own filter state (independent of the main stats filter bar).

---

## Navigation

```
StatsDashboard → "Courses" tab → course summary list → courseAnalysis/{courseId}
CourseList     → [stats icon on rows with ≥1 round]  → courseAnalysis/{courseId}
```

The `courseId` is a mandatory Int route argument. The analysis screen always shows data for exactly one course at a time, selected before navigation.

---

## Files

### New Files

| File | Purpose |
|---|---|
| `ui/courseanalysis/CourseAnalysisScreen.kt` | Full-screen composable — header, filter row, 5-tab layout |
| `ui/courseanalysis/CourseAnalysisViewModel.kt` | Owns `CourseAnalysisFilter` state, exposes `CourseAnalysisUiState` |
| `ui/courseanalysis/CourseAnalysisData.kt` | All data model classes for the feature |

### Modified Files

| File | Change |
|---|---|
| `navigation/NavGraph.kt` | Add `courseAnalysis/{courseId}` route with Int arg; thread `onNavigateToCourseAnalysis` callback into both Stats and CourseList composables |
| `ui/stats/StatsDashboardScreen.kt` | Add 7th "Courses" tab; expose `onNavigateToCourseAnalysis` callback; add `CoursesTabContent` composable |
| `ui/stats/StatsViewModel.kt` | Expose `coursesWithRoundCounts: StateFlow<List<CourseWithRoundCount>>` for the new tab |
| `ui/courselist/CourseListScreen.kt` | Add `onNavigateToCourseAnalysis` callback; render analysis icon button on rows with ≥1 finalized round |
| `data/repository/StatsRepository.kt` | Add `getCourseAnalysisData(courseId, filter): Flow<CourseAnalysisData>` |

---

## Data Models

Defined in `CourseAnalysisData.kt`:

```kotlin
data class CourseAnalysisFilter(
    val teeSetId: Int? = null,
    val year: Int? = null,
    val startDate: Date? = null,
    val endDate: Date? = null
)

// Overall scoring across all filtered rounds at the course
data class OverallScoringStats(
    val roundsPlayed: Int,
    val avgScore: Double,
    val avgToPar: Double,
    val bestRoundToPar: Int,
    val worstRoundToPar: Int,
    val eagles: Int,
    val birdies: Int,
    val pars: Int,
    val bogeys: Int,
    val doubles: Int,
    val worseCount: Int,
    val trend: List<RoundScoreSummary>   // feeds directly into ScoringTrendChart
)

// Aggregated scoring stats for a single hole across all filtered rounds
data class HoleAnalysis(
    val holeNumber: Int,
    val par: Int,
    val roundsPlayed: Int,
    val avgScore: Double,
    val medianScore: Double,
    val eagles: Int,
    val birdies: Int,
    val pars: Int,
    val bogeys: Int,
    val doubles: Int,
    val worseCount: Int
)

// Aggregated SG per hole, broken down by shot type
// avgSgOffTee is null for Par 3s (no tee SG tracked)
data class HoleSgBreakdown(
    val holeNumber: Int,
    val par: Int,
    val roundsPlayed: Int,
    val avgSgTotal: Double,
    val avgSgOffTee: Double?,
    val avgSgApproach: Double,
    val avgSgAroundGreen: Double,
    val avgSgPutting: Double
)

// Directional miss breakdown + raw dispersion points for one shot type
data class MissBreakdown(
    val totalShots: Int,
    val leftPct: Float,
    val rightPct: Float,
    val shortPct: Float,
    val longPct: Float,
    val dispersionPoints: List<DispersionPoint>  // feeds into existing DispersionCard
)

data class CourseMissStats(
    val tee: MissBreakdown,
    val approach: MissBreakdown,
    val chip: MissBreakdown
)

data class CoursePuttingStats(
    val avgPuttsPerRound: Double,
    val onePuttPct: Float,
    val twoPuttPct: Float,
    val threePlusPct: Float,
    val makePctByDistance: List<DistanceBucket>,  // feeds into existing distance bar
    val avgFirstPuttDistance: Double,
    val missLeftPct: Float,
    val missRightPct: Float,
    val missLongPct: Float,
    val missShortPct: Float
)

// Root object returned by StatsRepository
data class CourseAnalysisData(
    val course: Course,
    val teeSets: List<TeeSet>,              // for tee set filter picker
    val availableYears: List<Int>,
    val overall: OverallScoringStats,
    val byHole: List<HoleAnalysis>,         // sorted by holeNumber
    val holeSgBreakdowns: List<HoleSgBreakdown>, // sorted by holeNumber
    val missStats: CourseMissStats,
    val putting: CoursePuttingStats
)

// Used by the 7th "Courses" tab in StatsDashboard
data class CourseWithRoundCount(
    val course: Course,
    val roundCount: Int,
    val lastPlayed: Date
)
```

---

## Screen Layout

### CourseAnalysisScreen

```
┌─────────────────────────────────────────────┐
│ ← Course Analysis                            │  TopAppBar
├─────────────────────────────────────────────┤
│  Pebble Beach — Pebble Beach, CA            │  Course header card
│  Blue Tees · Rating 75.5 · Slope 145       │
│  12 rounds played                           │
├─────────────────────────────────────────────┤
│  [Tee Set ▼]  [Year ▼]          12 rounds  │  Filter row
├─────────────────────────────────────────────┤
│  Overview │ By Hole │ SG by Hole │ Misses │ Putting │  TabRow
├─────────────────────────────────────────────┤
│                                             │
│  (tab content — see below)                  │
│                                             │
└─────────────────────────────────────────────┘
```

### Overview Tab

```
StatCard row:  Rounds Played  |  Avg Score  |  Avg To Par
StatCard row:  Best Round     |  Worst Round

Score Distribution bar:
  Eagles  Birdies  Pars  Bogeys  Doubles+
  count + % of total holes played

Scoring Trend chart  ← reuses ScoringTrendChart directly
  (x = round date, y = score to par, tappable points)
```

### By Hole Tab

```
Sort chips:  [Hole #]  [Avg Score]  [Birdie Count]

LazyColumn — one row per hole:
┌──────────────────────────────────────────┐
│ Hole 7  Par 5  (12 rounds)               │
│ Avg: 5.33   Median: 5                    │  green if ≤ par, red if > par
│  E:0  B:2  P:6  Bo:3  D+:1              │  birdie cell highlighted if > 0
└──────────────────────────────────────────┘
```

### SG by Hole Tab

Each hole is displayed as a row in a `LazyColumn`. Positive SG values are green, negative are red. Par 3 rows omit the Off-the-Tee column.

Sort chips allow ordering by hole number, total SG, or any individual component.

```
Sort chips:  [Hole #]  [Total SG]  [Off Tee]  [Approach]  [Around Green]  [Putting]

LazyColumn — one row per hole:
┌───────────────────────────────────────────────────────┐
│ Hole 7  Par 5  (12 rounds)                            │
│                                                       │
│ Total SG:  +0.42                                      │  green / red
│                                                       │
│  Off Tee   Approach   Around Green   Putting          │  column headers
│  +0.21     +0.18      -0.05          +0.08            │  color-coded per value
│                                                       │
│  [horizontal bar showing relative weight of each      │
│   component — proportional fill, signed]              │
└───────────────────────────────────────────────────────┘
```

The horizontal component bar shows the SG contribution of each shot type as a signed proportional fill:
- Positive contributions fill right from the center in green
- Negative contributions fill left from the center in red
- Width is scaled relative to the largest absolute SG component across all holes, so bars are comparable across the column

This makes it visually obvious which holes the user gains or loses SG on, and which shot type drives that result.

### Misses Tab

Three sections (Tee Shots, Approach Shots, Chips), each with the same layout:

```
── Tee Shots ──────────────────────────────
Miss direction bar:
  Left X%  ·  Right X%  ·  Short X%  ·  Long X%

Dispersion scatter  ← reuses existing DispersionCard
  (dots plotted relative to target; color by outcome)
```

### Putting Tab

```
StatCard row:  Avg Putts/Rnd  |  1-Putt%  |  3-Putt%

Avg first putt distance: X ft

Make % by distance  ← reuses existing distance bucket bar
  0-5 ft · 5-10 ft · 10-15 ft · 15-20 ft · 20-25 ft · 25+ ft

Miss direction breakdown:
  Left X%  ·  Right X%  ·  Short X%  ·  Long X%
```

---

## Repository Implementation Notes

`StatsRepository.getCourseAnalysisData(courseId, filter)`:

1. Collect `getFinalizedRoundsWithDetails()`, filter to `round.courseId == courseId`.
2. Apply `CourseAnalysisFilter` (teeSetId, year, date range).
3. Build `OverallScoringStats` by iterating rounds — sum eagle/birdie/par/bogey/double across all `HoleStatWithHole` entries; build `trend` list using same `RoundScoreSummary` shape already used by `calculateScoringStats()`.
4. Build `byHole` by grouping `HoleStatWithHole` entries by `hole.holeNumber` across all rounds. Median = sort scores for that hole, take middle (average of two middles for even N).
5. Build `holeSgBreakdowns` from the same grouping. For each hole, average `holeStat.strokesGained`, `holeStat.sgOffTee`, `holeStat.sgApproach`, `holeStat.sgAroundGreen`, and `holeStat.sgPutting` across all scored rounds. `avgSgOffTee` is `null` for Par 3 holes (where `sgOffTee` is not recorded). Only include `HoleStatWithHole` entries where `holeStat.isScored == true` and `holeStat.strokesGained != 0.0` to avoid pulling in holes where SG was never computed.
6. Build `CourseMissStats` using the same lateral/distance deviation fields already aggregated in `calculateDrivingStats()` and `calculateApproachStats()`, scoped to this course's rounds. Chip miss sourced from `chipDistance` directional fields.
6. Build `CoursePuttingStats` from `HoleStatWithHole.putts` — `Putt` entities carry `paceMiss` and `directionMiss`, the same fields used by `calculatePuttAdvancedStats()`.

All aggregation runs in-memory in a `map {}` operator on the Flow; no new DAO queries required.

---

## Courses Tab in StatsViewModel

A new `coursesWithRoundCounts` StateFlow is derived from `getFinalizedRoundsWithDetails()`:

```kotlin
val coursesWithRoundCounts: StateFlow<List<CourseWithRoundCount>> =
    statsRepository.getFinalizedRoundsFlow()
        .map { rounds ->
            rounds
                .groupBy { it.round.courseId }
                .map { (_, courseRounds) ->
                    CourseWithRoundCount(
                        course = courseRounds.first().course,
                        roundCount = courseRounds.size,
                        lastPlayed = courseRounds.maxOf { it.round.date }
                    )
                }
                .sortedByDescending { it.lastPlayed }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
```

This flow runs independently of the main `statsUiState` so the Courses tab always reflects all-time data regardless of the active stats filter.

---

## Reused Components

| Component | Where reused |
|---|---|
| `ScoringTrendChart` | Overview tab — trend chart |
| `DispersionCard` | Misses tab — scatter plots for tee/approach/chip |
| `StatCard` | Overview and Putting tabs — headline numbers |
| `DistributionBar` | Overview tab — score distribution bar |
| `makePctByDistance` bar | Putting tab — make % by distance buckets |

No new charting library is required.

---

## Out of Scope

- Course-vs-course comparison view
- Course-vs-course SG comparison
- Exporting course analysis data
