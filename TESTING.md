# Golf Tracker Testing Strategy

## Overview

This document defines the testing strategy, important edge cases, and guidelines for continuing to extend test coverage as the Golf Tracker app evolves.

## Test Pyramid

| Layer | What | Framework | Location |
|-------|-------|-----------|----------|
| **Unit** | Pure logic: calculators, stats, data transforms | JUnit 4 + MockK | `app/src/test/` |
| **Integration** | Room DAOs, repository flows, database migrations | JUnit 4 + Room in-memory DB | `app/src/androidTest/` |
| **UI** | Compose screens, navigation flows | Compose Testing + Espresso | `app/src/androidTest/` |

> [!IMPORTANT]
> **MVP scope focuses on Unit tests.** Integration and UI tests are documented here as the recommended next phase.

## Running Tests

```bash
# All unit tests
./gradlew testDebugUnitTest

# Specific test class
./gradlew testDebugUnitTest --tests "com.golftracker.util.GirCalculatorTest"

# With verbose output
./gradlew testDebugUnitTest --info
```

Test reports are generated at:  
`app/build/reports/tests/testDebugUnitTest/index.html`

## Current Test Coverage

### GirCalculator (`GirCalculatorTest.kt`)
| Scenario | Why it matters |
|----------|---------------|
| Standard GIR on par 3/4/5 | Core happy path |
| Boundary: exactly on/off regulation | Off-by-one in `<=` check |
| Unplayed hole (score=0) | Prevent false positives |
| Hole-in-one on par 3 and par 4 | Edge where putts=0 |
| High putts but still GIR | Validates formula: approach strokes vs. regulation, not total score |

### HandicapCalculator (`HandicapCalculatorTest.kt`)
| Scenario | Why it matters |
|----------|---------------|
| Differential formula correctness | Core WHS math |
| Different slope values | Non-trivial division |
| 9-hole rounds skipped | MVP only supports 18-hole |
| Invalid tee sets (0 slope/rating) | Prevents divide-by-zero |
| Zero gross score skipped | Prevents meaningless differentials |
| < 3 rounds returns null | Minimum threshold per WHS |
| 3/4 rounds with adjustments | WHS adjustment values |
| Best differentials selected | Must sort and pick lowest |
| Truncation not rounding | WHS specifies truncation |
| 20+ round cap | Limits to last 20 differentials |

### StatsCalculation (`StatsCalculationTest.kt`)
| Scenario | Why it matters |
|----------|---------------|
| Average score math | Division accuracy |
| Average to par | Sign handling (over/under) |
| Empty rounds | No divide-by-zero |
| GIR percentage via GirCalculator | Consistency across codebase |
| girOverride precedence | User override always wins |
| Putts per GIR hole | Conditional aggregation |

## Critical Edge Cases Catalog

### Scoring & Data Entry
- **Score of 0**: Unplayed holes must not inflate averages or count as GIR
- **Partial rounds**: 9-hole rounds in an 18-hole context
- **Max score holes**: Very high scores (e.g., 15) shouldn't break calculations

### Handicap
- **Negative differentials**: Possible when playing below course rating — must be handled
- **All same scores**: Ensures sorting/selection produces correct average
- **Exactly 3/6/20 rounds**: Boundary counts in WHS table
- **Mixed valid/invalid rounds**: Some rounds with bad tee set data interspersed

### Statistics
- **No finalized rounds**: Stats should show zeros/empty, not crash
- **Single round**: Averages should equal that round's values
- **All par 3 course**: Driving stats should have 0 eligible holes

### CSV Export
- **Course name with special characters**: Spaces, apostrophes in filenames
- **Round with 0 penalties**: Penalties list empty, sum = 0
- **Round with no hole stats**: Graceful empty CSV

## Writing Tests: Guidelines for Future Development

### Naming Convention
```
fun `[context] - [scenario] - [expected result]`()
```
Examples:
- `fun `par 4 - reach green in 2 - GIR`()`
- `fun `3 rounds - uses 1 best differential with minus 2 adjustment`()`

### Test Structure (AAA Pattern)
```kotlin
@Test
fun `descriptive name`() {
    // Arrange — set up test data using TestDataFactory
    val round = TestDataFactory.roundWithDetails(scorePerHole = 5)

    // Act — call the function under test
    val result = HandicapCalculator.calculateDifferentials(listOf(round))

    // Assert — verify the result
    assertEquals(1, result.size)
    assertEquals(18.0, result[0].value, 0.01)
}
```

### When to Add Tests

Add tests **before** implementing when:
- Adding a new calculation or formula
- Fixing a bug (write the failing test first)
- Changing existing business logic

Add tests **after** implementing when:
- Refactoring internal structure (tests verify behavior is preserved)
- Adding a new screen (integration/UI tests)

### TestDataFactory Usage
Always use `TestDataFactory` rather than constructing entities directly. Override only the parameters relevant to your test:

```kotlin
// Good: clear what matters for this test
val round = TestDataFactory.roundWithDetails(scorePerHole = 10, parPerHole = 4)

// Bad: constructing full entity graph manually
val course = Course(id = 1, name = "Test", city = "C", state = "S", holeCount = 18)
val teeSet = TeeSet(id = 1, courseId = 1, name = "W", slope = 113.0, rating = 72.0)
// ... 30 more lines of setup ...
```

## Future Test Priorities

1. **Room DAO tests** — Verify queries with in-memory database
2. **ViewModel tests** — Test state management with `Turbine` library
3. **Navigation tests** — Ensure correct routes and argument passing
4. **CSV export integration test** — Verify file content matches expected format
5. **Database migration tests** — Critical once schema evolves
