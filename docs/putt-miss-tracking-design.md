# Putt Miss Tracking — Design Plan

## Background

This document covers extending the `Putt` entity with advanced tracking data. It applies **only
to putts** — `Shot` (covering tee shots, approaches, chips, and sand shots) already has its own
dispersion tracking via `dispersionLeft`, `dispersionRight`, `dispersionShort`, `dispersionLong`
fields added in migration 13→14.

The current `Putt` entity records `distance`, `made` (inferred), and `strokesGained`. The
proposed additions are:

| Field | Scale | Applies to |
|---|---|---|
| `breakDirection` | 5-point left–right | All putts |
| `slopeDirection` | 5-point uphill–downhill | All putts |
| `paceMiss` | 5-point short–long | Missed putts only |
| `directionMiss` | 5-point left–right | Missed putts only |

**High/low side is not stored — it is inferred from `breakDirection` + `directionMiss`.**

All new fields are optional extras accessed via a per-putt "Advanced" button. The existing
distance entry workflow is unchanged.

---

## 1. Field Rationale

### Break Direction — 5-Point Scale

```
BIG_LEFT | SMALL_LEFT | STRAIGHT | SMALL_RIGHT | BIG_RIGHT
```

Recorded as **how the ball broke**, not as a read or intention. On putts with changing slope,
record the net overall break.

**Tells you:**
- Whether the golfer performs differently on gently vs. severely breaking putts
- Input for the high/low side inference
- Whether big-breaking putts are more likely to end up low-side (common pattern)

---

### Slope Direction — 5-Point Scale

```
STEEP_UPHILL | UPHILL | FLAT | DOWNHILL | STEEP_DOWNHILL
```

Recorded relative to the direction the ball is rolling. On multi-slope putts, record the
dominant overall slope.

Relevant for **all putts**, including the final made putt, since slope affects both make rate
and pace calibration regardless of outcome.

**Tells you:**
- Make % by slope grade — steep downhill putts are the hardest to judge
- Whether pace misses are concentrated on downhill (fear of rolling past) vs. uphill (deceleration)
- Combined with pace miss: `STEEP_DOWNHILL + BIG_SHORT` is a distinct fear pattern

---

### Pace Miss — 5-Point Scale

```
BIG_SHORT | SHORT | GOOD | LONG | BIG_LONG
```

Recorded relative to the hole. `GOOD` covers lip-outs and on-pace putts that didn't drop.
Applies to **missed putts only**.

**Tells you:**
- Severity of pace misses — `BIG_SHORT` on lag putts is a fundamentally different problem from
  a `SHORT` miss from 6 feet
- Whether the golfer leaves putts short by a small margin (fixable with confidence) or a large
  margin (pace calibration issue)
- Combined with `STEEP_DOWNHILL`: are big short misses concentrated on steep downhillers?

---

### Direction Miss — 5-Point Scale

```
BIG_LEFT | LEFT | STRAIGHT | RIGHT | BIG_RIGHT
```

Recorded relative to the **starting line** (target line), independent of break.
Applies to **missed putts only**. `STRAIGHT` means the ball started on the intended line (covers
lip-outs and putts that broke more or less than expected but started correctly).

**Tells you:**
- Stroke severity — `BIG_LEFT` is a pulled stroke; `LEFT` might be a slight alignment issue
- Whether mechanical misses are minor corrections or recurring big misses
- Input for the high/low side inference

---

### Miss Magnitude Guidelines

The distinction between "big" and "small" is intentionally feel-based — consistency matters more
than exact measurements. The anchors below give a starting point; adjust to personal tendency.

**Direction (left/right of target line):**
- **Small** — lip-out, grazed the edge, or slid < 1 cup-width (~4 inches) past the hole.
  The ball was on a line to go in; a minor mechanical twitch or bounce explains it.
- **Big** — clearly offline, > 2 cup-widths (~8 inches) from the edge. "Never had a chance."
  Points to a stroke flaw (pull, push) worth investigating.

**Pace — Short:**
- **Small** — ball stops within ~12–18 inches of the hole. Close enough that it was makeable
  with a slightly firmer stroke; often a mental hesitation rather than a pace calibration issue.
- **Big** — ball stops > ~3 feet short. A clear calibration miss, frequently seen on steep
  downhillers or when the golfer decelerates through impact.

**Pace — Long:**
- **Small** — ball rolls ~12–24 inches past. Leaves a manageable comebacker.
- **Big** — ball rolls > ~3 feet past. Creates a genuine 3-putt risk and suggests the golfer
  overcommitted, typically on uphill putts or after a previous short miss.

**Rule of thumb:** Small = "borderline, could be unlucky"; Big = "mechanical or calibration
issue, a recurring pattern here is worth fixing."

---

### Combined Miss: 2D Selection

`paceMiss` and `directionMiss` are stored as separate fields but **selected together** in the UI
via a 5×5 grid (see Section 3.3). This lets the golfer record the full character of a miss in a
single tap: e.g., "short and right", "big long and slightly left", "on pace but left".

---

### High / Low Side — Inferred, Not Stored

High/low side is computed from `breakDirection` + `directionMiss` and never written to the
database. The magnitude of the slide miss is further qualified from the magnitudes of both inputs.

**Inference logic:**

| Break | Direction Miss | Inferred Slide |
|---|---|---|
| RIGHT (either size) | LEFT (either size) | **HIGH** — stayed above the break (pro side) |
| RIGHT (either size) | RIGHT (either size) | **LOW** — went with the break (amateur side) |
| LEFT (either size) | RIGHT (either size) | **HIGH** |
| LEFT (either size) | LEFT (either size) | **LOW** |
| STRAIGHT | any | **not applicable** |
| any | STRAIGHT | **not applicable** (started on line — lip-out, bad bounce, or misread break) |
| null (either) | — | **null** |

**Magnitude qualification:** when both `breakDirection` and `directionMiss` have the same
magnitude class (both BIG or both SMALL), the inferred slide is a stronger signal. Stats can
weight or stratify by this when sufficient data exists. Storing the raw fields preserves all
this information without adding a derived magnitude field.

---

## 2. Schema Changes

### 2.1 New Enum Types

```kotlin
// app/src/main/java/com/golftracker/data/entity/Putt.kt

enum class PuttBreak { BIG_LEFT, SMALL_LEFT, STRAIGHT, SMALL_RIGHT, BIG_RIGHT }
enum class PuttSlopeDirection { STEEP_UPHILL, UPHILL, FLAT, DOWNHILL, STEEP_DOWNHILL }
enum class PaceMiss { BIG_SHORT, SHORT, GOOD, LONG, BIG_LONG }
enum class DirectionMiss { BIG_LEFT, LEFT, STRAIGHT, RIGHT, BIG_RIGHT }
```

`SlideMiss` is a derived type used only in the stats layer — **not** persisted, no TypeConverter
needed:

```kotlin
// Stats layer only — not stored
enum class SlideMiss { HIGH, LOW }
```

Room stores the four persisted enums as `TEXT` via `@TypeConverters`.

### 2.2 Updated Putt Entity

```kotlin
@Entity(
    tableName = "putts",
    foreignKeys = [ForeignKey(
        entity = HoleStat::class,
        parentColumns = ["id"],
        childColumns = ["holeStatId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("holeStatId")]
)
data class Putt(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val holeStatId: Int,
    val puttNumber: Int,
    val distance: Float? = null,
    val made: Boolean = false,
    val strokesGained: Double? = null,

    // Advanced fields — null means not recorded.
    // breakDirection and slopeDirection apply to all putts (including made).
    // paceMiss and directionMiss apply to missed putts only.
    val breakDirection: PuttBreak? = null,
    val slopeDirection: PuttSlopeDirection? = null,
    val paceMiss: PaceMiss? = null,
    val directionMiss: DirectionMiss? = null,
)
```

### 2.3 TypeConverters

Add to the existing `Converters.kt`:

```kotlin
@TypeConverter
fun toPuttBreak(value: String?): PuttBreak? = value?.let { enumValueOf<PuttBreak>(it) }
@TypeConverter
fun fromPuttBreak(value: PuttBreak?): String? = value?.name

@TypeConverter
fun toPuttSlopeDirection(value: String?): PuttSlopeDirection? =
    value?.let { enumValueOf<PuttSlopeDirection>(it) }
@TypeConverter
fun fromPuttSlopeDirection(value: PuttSlopeDirection?): String? = value?.name

@TypeConverter
fun toPaceMiss(value: String?): PaceMiss? = value?.let { enumValueOf<PaceMiss>(it) }
@TypeConverter
fun fromPaceMiss(value: PaceMiss?): String? = value?.name

@TypeConverter
fun toDirectionMiss(value: String?): DirectionMiss? =
    value?.let { enumValueOf<DirectionMiss>(it) }
@TypeConverter
fun fromDirectionMiss(value: DirectionMiss?): String? = value?.name
```

### 2.4 Slide Inference Utility

Add to `PuttAnalysisUtils.kt` (new file) or alongside `StrokesGainedCalculator`:

```kotlin
fun inferSlideMiss(break_: PuttBreak?, direction: DirectionMiss?): SlideMiss? {
    if (break_ == null || direction == null) return null
    if (break_ == PuttBreak.STRAIGHT) return null
    if (direction == DirectionMiss.STRAIGHT) return null

    val breakIsRight = break_ == PuttBreak.SMALL_RIGHT || break_ == PuttBreak.BIG_RIGHT
    val missIsRight  = direction == DirectionMiss.RIGHT || direction == DirectionMiss.BIG_RIGHT
    return when {
        breakIsRight && !missIsRight -> SlideMiss.HIGH   // stayed above right-breaking putt
        breakIsRight &&  missIsRight -> SlideMiss.LOW    // went with the right break
        !breakIsRight &&  missIsRight -> SlideMiss.HIGH  // stayed above left-breaking putt
        !breakIsRight && !missIsRight -> SlideMiss.LOW   // went with the left break
        else -> null
    }
}
```

### 2.5 Database Migration (v25 → v26)

```kotlin
val MIGRATION_25_26 = object : Migration(25, 26) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE putts ADD COLUMN breakDirection TEXT")
        database.execSQL("ALTER TABLE putts ADD COLUMN slopeDirection TEXT")
        database.execSQL("ALTER TABLE putts ADD COLUMN paceMiss TEXT")
        database.execSQL("ALTER TABLE putts ADD COLUMN directionMiss TEXT")
    }
}
```

Register in `GolfDatabase.kt` `addMigrations(...)` and bump `version = 26`. All existing putt
rows will have `null` for all four columns — the correct default.

---

## 3. UI Changes

### 3.1 Scope

Advanced putt controls appear **only in the Putting card** of `HoleTrackingScreen`. Chip, sand,
approach, and tee shot cards are unaffected.

### 3.2 Entry Point: Single "Advanced" Button for the Putting Card

A single `OutlinedButton` labeled **"Advanced Putt Details"** sits below the distance rows and
above the SG Putting line — the same visual position as "Advanced Chip Lie Options" and "Advanced
Sand Lie Options" elsewhere in the screen.

```
Distances (ft)
Putt 1  [SG]  [-5]  [ 20 ]  [+5]
Putt 2  [SG]  [-5]  [  4 ]  [+5]

[ Advanced Putt Details ]

SG Putting: -0.72
```

**Visual cue:** tint the button with `MaterialTheme.colorScheme.primary` when any advanced field
on any putt for this hole is non-null; render it muted when all fields are null across all putts.

### 3.3 Arrow Label Notation

Single and double arrows denote small and large values throughout all pickers. The same symbols
appear in both the dialog chips and the miss grid axis labels.

| Concept | ←← | ← | — / · | → | →→ |
|---|---|---|---|---|---|
| Break | Big left | Left | Straight | Right | Big right |
| Slope | Steep up (↑↑) | Uphill (↑) | Flat (—) | Downhill (↓) | Steep down (↓↓) |
| Direction miss | ←← | ← | · (straight) | → | →→ |
| Pace miss (rows) | ↑↑ short | ↑ short | Good / lip | ↓ long | ↓↓ long |

For slope, use vertical arrows (`↑↑ ↑ — ↓ ↓↓`). For break and direction, use horizontal arrows
(`←← ← — → →→`). Pace uses vertical arrows since short/long maps naturally to up/down.

**Miss grid axis titles** use two-sided arrow labels spanning the full axis:
- Column header (above the grid): `Left ←→ Right`
- Row header (rotated label to the left of the grid, or above the row arrows): `Short ↕ Long`

The individual cell tick labels (`←←  ←  ·  →  →→` and `↑↑  ↑  ✓  ↓  ↓↓`) appear inside the
grid margin, with the two-sided title making the direction immediately obvious.

---

### 3.4 Dialog Layout

Tapping "Advanced Putt Details" opens a **single scrollable dialog** containing one section per
putt. Each section shows different fields depending on whether that putt is the final made putt.

All putt sections are visible at once (no paging), since a typical hole has 1–3 putts and the
content fits comfortably in a scrollable dialog. A `LazyColumn` inside a fixed-height dialog
(~80% screen height) handles longer holes without overflow.

#### Break and Slope Pickers (all putts)

Both use a 5-chip `ChipSelector` row with arrow labels.

```
Break    [←←]  [←]  [—]  [→]  [→→]
Slope    [↑↑]  [↑]  [—]  [↓]  [↓↓]
```

#### Miss Grid (missed putts only)

Pace and direction are selected **together** from a labeled 5×5 grid. Tapping a cell sets both
`paceMiss` and `directionMiss` simultaneously. Tapping the active cell again clears both fields
back to null. This is a new `MissGrid` composable.

- **Rows** = pace (↑↑ short → ↓↓ long, top to bottom). Row labels on the left edge.
- **Columns** = direction (←← → ··→ →→, left to right). Column labels across the top.
- Axis titles span the full axis: `Left ←→ Right` above the column labels; `Short ↕ Long` to the
  left of the row labels (rotated text or stacked).
- Selected cell highlighted with `primaryContainer` fill; all others neutral.
- Row and column labels rendered in `labelSmall`.

```
               Left ←→ Right
         ←←    ←    ·    →    →→
  ↑↑  [ ]  [ ]  [ ]  [ ]  [ ]
  ↑   [ ]  [ ]  [ ]  [ ]  [ ]
  ✓   [ ]  [ ]  [ ]  [ ]  [ ]   Short ↕ Long
  ↓   [ ]  [ ]  [ ]  [ ]  [ ]
  ↓↓  [ ]  [ ]  [✓]  [ ]  [ ]
```

Minimum cell size ~44×36dp → full grid ~220×180dp, fits within a standard dialog width.

#### Full Dialog — 2-Putt Hole Example

```
┌────────────────────────────────────────────────┐
│  Advanced Putt Details                         │
│                                                │
│  ── Putt 1  (20 ft) ────────────────────────  │
│  Break   [←←]  [←]  [—]  [→]  [→→]           │
│  Slope   [↑↑]  [↑]  [—]  [↓]  [↓↓]           │
│  Miss                                          │
│       ←←   ←   ·   →   →→                     │
│  ↑↑  [ ]  [ ] [ ] [ ]  [ ]                    │
│  ↑   [ ]  [ ] [ ] [ ]  [ ]                    │
│  ✓   [ ]  [ ] [ ] [ ]  [ ]                    │
│  ↓   [ ]  [ ] [✓] [ ]  [ ]                    │
│  ↓↓  [ ]  [ ] [ ] [ ]  [ ]                    │
│                                                │
│  ── Putt 2  (4 ft — Made) ─────────────────── │
│  Break   [←←]  [←]  [—]  [→]  [→→]           │
│  Slope   [↑↑]  [↑]  [—]  [↓]  [↓↓]           │
│                                                │
│  [  Cancel  ]              [  Save  ]          │
└────────────────────────────────────────────────┘
```

The made putt section shows break and slope only — the miss grid is omitted. The section header
includes "(Made)" so it is unambiguous. Pace and direction fields remain null for the made putt
regardless of what is entered for other putts.

### 3.5 One-Putt Holes

When `holeStat.putts == 1`, the single putt is the made putt. "Advanced Putt Details" opens the
dialog with one section showing break and slope only.

### 3.6 Retroactive Editing

All four fields default to `null` for existing rounds after migration. The "Advanced" button and
dialog are available wherever putt rows are rendered, including the round history editing flow,
so users can backfill past rounds at any time.

### 3.7 ViewModel Changes

```kotlin
fun updatePuttAdvancedDetails(
    putt: Putt,
    breakDirection: PuttBreak?,
    slopeDirection: PuttSlopeDirection?,
    paceMiss: PaceMiss?,
    directionMiss: DirectionMiss?
) {
    viewModelScope.launch {
        roundRepository.updatePutt(
            putt.copy(
                breakDirection = breakDirection,
                slopeDirection = slopeDirection,
                paceMiss = paceMiss,
                directionMiss = directionMiss
            )
        )
    }
}
```

All four fields save atomically on "Save". No SG recalculation needed.

---

## 4. Stats Changes

### 4.1 New PuttAdvancedStats Data Class

```kotlin
data class PuttAdvancedStats(
    // ── Denominators ─────────────────────────────────────────────────
    val totalPuttCount: Int,         // all putts (break/slope stats)
    val missedPuttCount: Int,        // missed putts (pace/direction/slide stats)

    // ── Break distribution (all putts with breakDirection) ────────────
    val breakDistribution: Map<PuttBreak, Float>,   // enum → % of putts

    // ── Slope distribution (all putts with slopeDirection) ────────────
    val slopeDistribution: Map<PuttSlopeDirection, Float>,

    // ── Make % by slope grade ─────────────────────────────────────────
    val makeRateBySlope: Map<PuttSlopeDirection, Float?>,   // null if < 5 putts in bucket

    // ── Pace miss distribution (missed putts with paceMiss) ────────────
    val paceMissDistribution: Map<PaceMiss, Float>,

    // ── Direction miss distribution (missed putts with directionMiss) ──
    val directionMissDistribution: Map<DirectionMiss, Float>,

    // ── Combined miss grid counts (for heatmap display) ───────────────
    // Key: Pair(PaceMiss, DirectionMiss) → count
    val missGridCounts: Map<Pair<PaceMiss, DirectionMiss>, Int>,

    // ── Inferred slide (missed putts with break + direction recorded) ─
    val highSidePct: Float?,
    val lowSidePct: Float?,

    // ── Cross-tabs ────────────────────────────────────────────────────
    val paceBySlope: Map<PuttSlopeDirection, Map<PaceMiss, Float>>,
    val slideByBreakMagnitude: Map<String, Float?>,  // "big" vs "small" break → low side %

    // ── Distance-stratified breakdown ─────────────────────────────────
    val shortRange: PuttRangeSplit?,    // < 6 ft
    val midRange: PuttRangeSplit?,      // 6–15 ft
    val longRange: PuttRangeSplit?,     // > 15 ft
)

data class PuttRangeSplit(
    val missedPutts: Int,
    val paceMissDistribution: Map<PaceMiss, Float>,
    val directionMissDistribution: Map<DirectionMiss, Float>,
    val highSidePct: Float?,   // inferred; null if insufficient data
    val lowSidePct: Float?,
)
```

### 4.2 Key Insights These Stats Enable

| Insight | Derived From |
|---|---|
| "You leave most putts short, especially lag putts" | `BIG_SHORT` + `SHORT` dominant in `longRange` |
| "You under-read breaking putts" | `lowSidePct` dominant (inferred) |
| "Big-breaking putts end up low-side more often" | `slideByBreakMagnitude["big"]` >> `["small"]` |
| "You fear steep downhill putts — big short misses" | `paceBySlope[STEEP_DOWNHILL]` shows `BIG_SHORT` dominant |
| "Your stroke pulls under pressure" | `BIG_LEFT` elevated in `shortRange.directionMissDistribution` |
| "Your misses are small — scoring opportunity" | `SHORT` + `LEFT`/`RIGHT` cluster near center of miss grid |
| "You make uphill putts but struggle downhill" | `makeRateBySlope[UPHILL]` >> `makeRateBySlope[DOWNHILL]` |

### 4.3 Stats Screen Display

Add a "Putt Details" subsection to the existing Putting stats card. Use data sufficiency guards:
show a "Record advanced putt data to unlock" note for any section lacking ≥ 10 qualifying putts.

**Break distribution** (≥ 10 putts with `breakDirection`):
- 5-segment horizontal bar: Big Left | Left | Straight | Right | Big Right

**Slope make % table** (≥ 5 putts per slope bucket):
- 5-row table: make % by slope grade, optionally stratified by distance range

**Miss heatmap** (≥ 10 missed putts with both `paceMiss` + `directionMiss`):
- 5×5 grid matching the entry grid, cells shaded by frequency
- Immediately shows whether the miss cluster is in the short-left, long-right quadrant, etc.

**Pace miss bar** (≥ 10 missed putts with `paceMiss`):
- 5-segment: Big Short | Short | Good | Long | Big Long

**Direction miss bar** (≥ 10 missed putts with `directionMiss`):
- 5-segment: Big Left | Left | Center | Right | Big Right

**High/Low side bar** (≥ 10 missed putts on non-straight putts with both fields):
- 2-segment: High Side | Low Side
- Note: "Inferred from break + direction"

**Pace by slope cross-tab** (≥ 5 missed putts per slope bucket):
- Small table: rows = slope grade, columns = pace miss category

---

## 5. Implementation Phases

### Phase 1 — Schema & Migration (no UI impact)
1. Add `PuttBreak`, `PuttSlopeDirection`, `PaceMiss`, `DirectionMiss` enums to `Putt.kt`
2. Add `SlideMiss` enum (stats layer only — no TypeConverter)
3. Add TypeConverters for the four persisted enums to `Converters.kt`
4. Add `inferSlideMiss()` utility function
5. Add `MIGRATION_25_26`, bump DB version to 26
6. Update `Putt` entity with four nullable fields
7. Update `TestDataFactory` for unit tests
8. Run existing tests — confirm no regressions

### Phase 2 — Advanced Dialog UI
1. Build `MissGrid` composable — labeled 5×5 tap-to-select grid, single active cell, nullable;
   arrow symbols on row (pace) and column (direction) labels
2. Build `PuttAdvancedDialog` composable — scrollable dialog with one section per putt:
   - All putts: break chip row (`←← ← — → →→`) + slope chip row (`↑↑ ↑ — ↓ ↓↓`)
   - Missed putts additionally: `MissGrid`
   - Made putt section labeled "(Made)", no miss grid
3. Add single "Advanced Putt Details" `OutlinedButton` to the putting card in `HoleTrackingScreen`,
   below the distance rows and above the SG line
4. Add `updatePuttAdvancedDetails()` to `RoundViewModel`
5. Wire button tinting (primary when any field on any putt is non-null, muted otherwise)
6. Test all fields round-trip on both missed and made putts; test 1-putt holes

### Phase 3 — Stats & Display
1. Implement `calculatePuttAdvancedStats()` in `StatsRepository` including `inferSlideMiss()` calls
2. Add `PuttAdvancedStats` to stats pipeline, expose via `StatsViewModel`
3. Add "Putt Details" section to stats dashboard:
   - Break bar, slope make % table, miss heatmap, pace bar, direction bar, slide bar,
     pace-by-slope cross-tab
4. Add unit tests for `inferSlideMiss()` and `calculatePuttAdvancedStats()`

---

## 6. Open Questions

1. **Miss grid cell size on small screens** — minimum recommended cell size is ~44×36dp, putting
   the full grid at roughly 220×180dp. Verify on a small-screen device (e.g., Pixel 4a). If cells
   are too small, the fallback is to increase minimum touch target size at the cost of the dialog
   needing to scroll more on short screens.

---

## 7. Confirmed Decisions

- **High/low not tracked for straight putts** — `inferSlideMiss()` returns `null` when
  `breakDirection == STRAIGHT`. Stats sections that show high/low use only non-straight-break
  putts as the denominator and should note this in the UI.
- **Miss grid axis labels** — two-sided format: `Left ←→ Right` (columns), `Short ↕ Long` (rows).
- **Miss magnitude** — feel-based; see Section 1 guidelines. Small ≈ borderline/unlucky,
  Big ≈ clear mechanical or calibration issue.
