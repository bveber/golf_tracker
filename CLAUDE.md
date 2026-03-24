# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build
./gradlew assembleDebug
./gradlew assembleRelease

# Run unit tests
./gradlew testDebugUnitTest

# Run a single test class
./gradlew testDebugUnitTest --tests "com.golftracker.util.HandicapCalculatorTest"

# Run instrumentation tests (requires device/emulator)
./gradlew connectedDebugAndroidTest

# Lint
./gradlew lint
./gradlew lintFix
```

Test reports: `app/build/reports/tests/testDebugUnitTest/index.html`

## Architecture

Native Android app (Kotlin, min SDK 26) using **MVVM + Repository pattern** with Jetpack Compose and Hilt DI. Single-activity architecture with Compose Navigation.

### Layer Overview

```
Compose UI Screens
    ↓
ViewModels (StateFlow/Flow + viewModelScope)
    ↓
Repositories (abstract Room DAOs + Retrofit API)
    ↓
Room Database (SQLite, 10 entities, schema v25)
```

### Key Modules

**Navigation** — `navigation/NavGraph.kt` is the single `NavHost` with routes: `home`, `courseList`, `courseEdit`, `courseImport`, `bag`, `roundSetup`, `holeTracking`, `roundSummary`, `roundHistory`, `stats`, `handicap`, `settings`.

**Data Layer** — `data/db/GolfDatabase.kt` hosts all Room entities and migrations (v6→v25). Key entities: `Round`, `HoleStat`, `Shot`, `Putt`, `Penalty`, `Course`, `TeeSet`, `Hole`. `RoundWithDetails` is the composite object graph used throughout the stats pipeline.

**Domain Logic** — `domain/SgRecalculationUseCase.kt` runs on app startup to recalculate Strokes Gained for all finalized rounds. Key calculators in `util/`:
- `StrokesGainedCalculator` — loads PGA baseline from `assets/sg_baseline.csv`, interpolates expected strokes by distance/lie, applies course difficulty adjustments
- `HandicapCalculator` — WHS-compliant index from best differentials; handles 9- and 18-hole rounds
- `GirCalculator` — GIR via `score - putts <= par - 2`

**Entry Points**:
- `GolfTrackerApp.kt` — `@HiltAndroidApp`, triggers `SgRecalculationUseCase` on startup
- `MainActivity.kt` — single activity, renders theme + nav graph

### Data Flow: Round Tracking

1. `RoundSetupViewModel.createRound()` → inserts `Round` entity
2. `RoundViewModel` manages `HoleTrackingScreen`; writes `HoleStat`, `Shot`, `Putt`, `Penalty` per hole
3. On finalization: `isFinalized=true` + `SgRecalculationUseCase.recalculateRound()` computes per-hole SG
4. `StatsViewModel` / `HandicapViewModel` query `finalizedRoundsWithDetails` (Room `Flow`) for all aggregations

### Dependency Injection (Hilt)

- `DatabaseModule` — provides `GolfDatabase` singleton with all migrations + `SeedDataCallback` (seeds Pebble Beach on first run)
- `RepositoryModule` — binds `AuthRepository` implementation
- `NetworkModule` — provides Retrofit + `CourseApiService`
- `DataStoreModule` — provides `DataStore<Preferences>` for user settings

### API Keys

Stored in `local.properties` (not committed): `GOLF_COURSE_API_KEY`, `GOOGLE_SERVICES_API_KEY`. Accessed via `BuildConfig` fields defined in `app/build.gradle.kts`.

### Testing

Unit tests in `app/src/test/` use JUnit 4 + MockK + Kotlin Coroutines Test. `TestDataFactory` creates test entities. Core coverage: `HandicapCalculatorTest`, `StrokesGainedCalculatorTest`, `GirCalculatorTest`, `StatsCalculationTest`, `JsonExporterTest`.

Instrumentation tests in `app/src/androidTest/` test Room DAOs with in-memory databases and Compose screens with Espresso.
