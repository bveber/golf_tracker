# GPS Testing Plan

Three layers: **unit tests** (no device needed), **emulator GPS simulation** (scripted, automated),
and a **manual checklist** for camera/UI feel that can't be expressed in code.

---

## Layer 1 — Unit Tests for GpsViewModel

### The Problem

`GpsViewModel` takes `@ApplicationContext Context` and internally creates a
`FusedLocationProviderClient`. This makes it impossible to unit-test without a real Android device
or Robolectric. The fix is to introduce a `LocationSource` interface and inject it.

### Step 1 — Extract LocationSource interface

```kotlin
// ui/gps/LocationSource.kt
interface LocationSource {
    fun getLastLocation(): Flow<LatLng?>
    fun startUpdates(): Flow<LatLng>
    fun stopUpdates()
}
```

`FusedLocationSource` wraps the real client. `GpsViewModel` receives `LocationSource` via Hilt.
This is a pure refactor — all behavior is identical; only the injection boundary moves.

```kotlin
// di/LocationModule.kt
@Module @InstallIn(SingletonComponent::class)
object LocationModule {
    @Provides fun provideLocationSource(@ApplicationContext ctx: Context): LocationSource =
        FusedLocationSource(ctx)
}
```

`GpsViewModel` constructor changes from `@ApplicationContext context: Context` to
`private val locationSource: LocationSource`. The Hilt annotation stays.

### Step 2 — ViewModel test structure

Use `kotlinx-coroutines-test` (already in the project) with `TestScope` / `runTest`.
No new library needed.

```kotlin
// test/.../ui/gps/GpsViewModelTest.kt
class GpsViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()  // swaps Main dispatcher

    private val locationFlow = MutableSharedFlow<LatLng>(replay = 1)
    private val fakeLocationSource = object : LocationSource {
        override fun startUpdates() = locationFlow.asSharedFlow()
        override fun stopUpdates() {}
    }

    // Fake repos — all backed by MutableStateFlow so we can push data
    private val clubRepo  = FakeClubRepository()
    private val roundRepo = FakeRoundRepository()
    private val courseRepo = FakeCourseRepository()
    // ... etc

    private lateinit var vm: GpsViewModel

    @Before fun setup() {
        vm = GpsViewModel(fakeLocationSource, clubRepo, roundRepo, courseRepo, ...)
    }
}
```

`MainDispatcherRule` is a one-time ~15-line test helper (standard Coroutines-test boilerplate —
see [kotlinx-coroutines-test docs](https://kotlinx.github.io/kotlinx.coroutines/kotlinx-coroutines-test/)).

### Test Cases

Each test pushes GPS coordinates via `locationFlow.emit(LatLng(...))` and asserts on
`vm.uiState.value`.

#### Hole marker reset tests

```kotlin
@Test
fun `opening mapped hole sets markers to stored tee and green`() = runTest {
    courseRepo.setHole(Hole(teeLat=33.0, teeLng=-112.0, greenLat=33.001, greenLng=-112.0))
    vm.setTrackingContext(roundId=1, holeStatId=10, holePar=4)
    advanceUntilIdle()

    assertEquals(LatLng(33.0, -112.0), vm.uiState.value.playerLocation)
    assertEquals(LatLng(33.001, -112.0), vm.uiState.value.greenAnchor)
    assertEquals(LatLng(33.001, -112.0), vm.uiState.value.flagLocation)
}

@Test
fun `advancing to unmapped hole clears stale flag from previous hole`() = runTest {
    // Set up hole 1 as mapped
    courseRepo.setHole(id=1, Hole(greenLat=33.001, greenLng=-112.0))
    vm.setTrackingContext(roundId=1, holeStatId=10, holePar=4)
    advanceUntilIdle()
    assertEquals(LatLng(33.001, -112.0), vm.uiState.value.flagLocation)

    // Advance to unmapped hole 2
    courseRepo.setHole(id=2, Hole(greenLat=null, greenLng=null))
    vm.setTrackingContext(roundId=1, holeStatId=11, holePar=4)
    advanceUntilIdle()

    // Flag must NOT be pointing at hole 1's green
    assertNotEquals(LatLng(33.001, -112.0), vm.uiState.value.flagLocation)
}
```

#### GPS auto-follow tests

```kotlin
@Test
fun `player marker follows GPS when playerFollowsGps is true`() = runTest {
    vm.setTrackingContext(1, 10, 4)
    locationFlow.emit(LatLng(33.0, -112.0))
    advanceUntilIdle()
    assertEquals(LatLng(33.0, -112.0), vm.uiState.value.playerLocation)

    locationFlow.emit(LatLng(33.0005, -112.0))  // walked 50y
    advanceUntilIdle()
    assertEquals(LatLng(33.0005, -112.0), vm.uiState.value.playerLocation)
}

@Test
fun `manual drag pauses GPS follow until next shot is tracked`() = runTest {
    vm.setTrackingContext(1, 10, 4)
    locationFlow.emit(LatLng(33.0, -112.0))
    advanceUntilIdle()

    vm.onPlayerDragged(LatLng(33.0002, -112.0))
    locationFlow.emit(LatLng(33.0005, -112.0))  // GPS update should NOT move marker
    advanceUntilIdle()

    assertEquals(LatLng(33.0002, -112.0), vm.uiState.value.playerLocation)

    vm.onTrackShot()  // should re-enable follow
    locationFlow.emit(LatLng(33.0007, -112.0))
    advanceUntilIdle()
    assertEquals(LatLng(33.0007, -112.0), vm.uiState.value.playerLocation)
}
```

#### Green anchor vs flag tests

```kotlin
@Test
fun `distanceToPin uses greenAnchor not flagLocation`() = runTest {
    val tee = LatLng(33.0, -112.0)
    val green = LatLng(33.004, -112.0)   // ~500 yards
    courseRepo.setHole(Hole(teeLat=tee.lat, teeLng=tee.lng, greenLat=green.lat, greenLng=green.lng))
    vm.setTrackingContext(1, 10, 4)
    advanceUntilIdle()

    // Select driver — flagLocation moves to ~280y, but greenAnchor stays at green
    vm.onClubSelected(driverId)
    val ballLand = LatLng(33.002, -112.0)  // simulated ball position after drive

    locationFlow.emit(ballLand)
    advanceUntilIdle()
    vm.onTrackShot()
    advanceUntilIdle()

    val shot = vm.uiState.value.trackedShots.first()
    // distanceToPin should be ~250y (distance from ball landing to green)
    // not ~0y (distance from ball landing to where flag was moved by club selection)
    assertTrue("Expected distanceToPin ~250y, got ${shot.distanceToPin}", 
        (shot.distanceToPin ?: 0) > 200)
}

@Test
fun `CHIP auto-suggestion uses greenAnchor distance`() = runTest {
    val tee = LatLng(33.0, -112.0)
    val green = LatLng(33.004, -112.0)   // ~500 yards
    courseRepo.setHole(Hole(teeLat=33.0, teeLng=-112.0, greenLat=33.004, greenLng=-112.0))
    vm.setTrackingContext(1, 10, 4)
    vm.onShotTypeSelected(ShotType.TEE)
    vm.onClubSelected(driverId)  // flag moves to ~280y
    advanceUntilIdle()

    // Walk to ball at ~280y — still 220y from green. Must NOT trigger CHIP.
    locationFlow.emit(LatLng(33.002, -112.0))
    advanceUntilIdle()

    assertEquals(ShotType.APPROACH, vm.uiState.value.pendingShotType)
}

@Test
fun `CHIP triggers only when within 40 yards of greenAnchor`() = runTest {
    courseRepo.setHole(Hole(greenLat=33.004, greenLng=-112.0))
    vm.setTrackingContext(1, 10, 4)
    advanceUntilIdle()

    // Walk to 35 yards from green
    locationFlow.emit(LatLng(33.00368, -112.0))
    advanceUntilIdle()

    assertEquals(ShotType.CHIP, vm.uiState.value.pendingShotType)
}
```

#### CHIP suggestion stickiness tests

```kotlin
@Test
fun `user-chosen APPROACH is not overridden by GPS proximity`() = runTest {
    courseRepo.setHole(Hole(greenLat=33.004, greenLng=-112.0))
    vm.setTrackingContext(1, 10, 4)
    advanceUntilIdle()

    vm.onShotTypeSelected(ShotType.APPROACH)  // user explicit choice
    locationFlow.emit(LatLng(33.00368, -112.0))  // within 40y of green
    advanceUntilIdle()

    assertEquals(ShotType.APPROACH, vm.uiState.value.pendingShotType)
}
```

### Fakes needed

| Interface | Fake implementation |
|-----------|---------------------|
| `ClubRepository` | `FakeClubRepository(clubs: MutableStateFlow<List<Club>>)` |
| `RoundRepository` | `FakeRoundRepository` — stores `HoleStat`s and `Shot`s in memory, exposes as Flow |
| `CourseRepository` | `FakeCourseRepository` — map of holeId → Hole |
| `StatsRepository` | `FakeStatsRepository` — returns empty/zero stats |
| `UserPreferencesRepository` | `FakeUserPreferencesRepository` — fixed values |
| `WeatherRepository` | `FakeWeatherRepository` — returns null or fixed WeatherData |

These fakes live in `app/src/test/.../testutil/`. They replace the MockK `every { }` style for
stateful objects — simpler and catches more bugs.

---

## Layer 2 — Emulator GPS Simulation Script

### How it works

Android emulators expose `adb emu geo fix <lon> <lat>` which immediately injects a GPS fix into the
running app. The script sends a sequence of fixes with delays to simulate walking.

```
# Inject a fix at Pebble Beach hole 1 tee
adb -e emu geo fix -121.9441 36.5681
```

Note: argument order is **longitude first, then latitude** (matches the original `geo fix` telnet protocol).

### Script: `scripts/simulate_gps_walk.py`

```python
#!/usr/bin/env python3
"""
GPS walk simulator for the Golf Tracker emulator.

Usage:
    python3 simulate_gps_walk.py scenarios/par4_standard.json
    python3 simulate_gps_walk.py scenarios/hole_transition.json --speed 3.0
    python3 simulate_gps_walk.py scenarios/par4_standard.json --list-shots
"""
import json, subprocess, time, math, argparse, sys

def adb_geo_fix(lat: float, lon: float, serial: str = None):
    cmd = ["adb"]
    if serial:
        cmd += ["-s", serial]
    else:
        cmd += ["-e"]   # first emulator
    cmd += ["emu", "geo", "fix", str(lon), str(lat)]
    subprocess.run(cmd, check=True)

def interpolate(a, b, steps):
    """Yield 'steps' evenly-spaced lat/lng points from a to b (exclusive of b)."""
    for i in range(steps):
        t = i / steps
        yield (a[0] + (b[0]-a[0])*t, a[1] + (b[1]-a[1])*t)

def haversine_m(a, b):
    R = 6_371_000
    lat1, lat2 = math.radians(a[0]), math.radians(b[0])
    dlat = math.radians(b[0]-a[0])
    dlon = math.radians(b[1]-a[1])
    h = math.sin(dlat/2)**2 + math.cos(lat1)*math.cos(lat2)*math.sin(dlon/2)**2
    return 2*R*math.asin(math.sqrt(h))

def run_scenario(path: str, speed_mph: float = 3.0, serial: str = None):
    with open(path) as f:
        scenario = json.load(f)

    print(f"Running scenario: {scenario['name']}")
    print(f"  Hole: par {scenario.get('par','?')}")
    print(f"  Walking speed: {speed_mph} mph")

    gps_interval_s = 5.0   # app polls every 5s; send updates at that cadence
    speed_mps = speed_mph * 0.44704
    
    waypoints = scenario["waypoints"]
    for i, wp in enumerate(waypoints):
        dest = (wp["lat"], wp["lng"])
        label = wp.get("label", f"waypoint_{i}")
        pause = wp.get("pause_s", 0)        # intentional pause (e.g., setting up shot)

        if i == 0:
            print(f"\n  → {label} (start)")
            adb_geo_fix(dest[0], dest[1], serial)
            time.sleep(gps_interval_s)
            continue
        
        src = (waypoints[i-1]["lat"], waypoints[i-1]["lng"])
        dist_m = haversine_m(src, dest)
        walk_time_s = dist_m / speed_mps
        steps = max(1, int(walk_time_s / gps_interval_s))
        
        print(f"\n  → {label} ({dist_m:.0f}m, ~{walk_time_s:.0f}s walk, {steps} GPS pings)")
        for lat, lon in interpolate(src, dest, steps):
            adb_geo_fix(lat, lon, serial)
            time.sleep(gps_interval_s)
        
        # Final fix exactly at destination
        adb_geo_fix(dest[0], dest[1], serial)
        
        if pause > 0:
            print(f"     [pausing {pause}s — simulate user interaction]")
            time.sleep(pause)

    print("\n✓ Scenario complete")

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("scenario", help="Path to scenario JSON file")
    parser.add_argument("--speed", type=float, default=3.0, help="Walking speed mph (default 3.0)")
    parser.add_argument("--serial", help="adb device serial (default: first emulator)")
    parser.add_argument("--list-shots", action="store_true", help="Print waypoints and exit")
    args = parser.parse_args()

    with open(args.scenario) as f:
        scenario = json.load(f)

    if args.list_shots:
        for wp in scenario["waypoints"]:
            print(f"  {wp.get('label','?'):30s}  {wp['lat']:.6f}, {wp['lng']:.6f}")
        return

    run_scenario(args.scenario, args.speed, args.serial)

if __name__ == "__main__":
    main()
```

### Scenario files

Each scenario is a JSON file. Coordinates use a real golf hole (you can replace with local course
coordinates once known).

**`scripts/scenarios/par4_standard.json`** — Standard par 4, tests full hole flow:
```json
{
  "name": "Par 4 standard — tee, drive, approach, on green",
  "par": 4,
  "waypoints": [
    { "label": "tee box",          "lat": 36.5681,  "lng": -121.9441, "pause_s": 8  },
    { "label": "drive lands",      "lat": 36.5699,  "lng": -121.9441, "pause_s": 5  },
    { "label": "walk to ball",     "lat": 36.5712,  "lng": -121.9437, "pause_s": 3  },
    { "label": "approach result",  "lat": 36.5730,  "lng": -121.9441, "pause_s": 5  },
    { "label": "on the green",     "lat": 36.5735,  "lng": -121.9441, "pause_s": 0  }
  ]
}
```

**`scripts/scenarios/hole_transition.json`** — Finish a green, walk to next tee box. This is the
regression test for bug 1 (previous green bleed-over):
```json
{
  "name": "Hole transition — verify flag resets between holes",
  "par": 4,
  "notes": "Start on previous hole's green, then walk to next tee. The flag should NOT remain on the old green.",
  "waypoints": [
    { "label": "previous hole green",  "lat": 36.5735,  "lng": -121.9441, "pause_s": 10 },
    { "label": "walking to next tee",  "lat": 36.5720,  "lng": -121.9460, "pause_s": 3  },
    { "label": "next tee box",         "lat": 36.5710,  "lng": -121.9470, "pause_s": 8  }
  ]
}
```

**`scripts/scenarios/near_green_chip_boundary.json`** — Tests the 40-yard CHIP threshold with
`greenAnchor` fix in place:
```json
{
  "name": "Chip boundary test — 50y then 35y from green",
  "par": 4,
  "notes": "At 50y from green with APPROACH selected, chip must NOT trigger. At 35y, it should.",
  "waypoints": [
    { "label": "tee",              "lat": 36.5681,  "lng": -121.9441, "pause_s": 5  },
    { "label": "fairway 200y",     "lat": 36.5706,  "lng": -121.9441, "pause_s": 3  },
    { "label": "50y from green",   "lat": 36.5725,  "lng": -121.9441, "pause_s": 8  },
    { "label": "35y from green",   "lat": 36.5728,  "lng": -121.9441, "pause_s": 8  },
    { "label": "on green",         "lat": 36.5735,  "lng": -121.9441, "pause_s": 0  }
  ]
}
```

**`scripts/scenarios/par3.json`** — Par 3: starts at tee ~160y from green, no tee shot type chip.
```json
{
  "name": "Par 3 — approach from tee",
  "par": 3,
  "waypoints": [
    { "label": "tee box",        "lat": 36.5720,  "lng": -121.9441, "pause_s": 8 },
    { "label": "approach result","lat": 36.5730,  "lng": -121.9441, "pause_s": 5 },
    { "label": "on green",       "lat": 36.5735,  "lng": -121.9441, "pause_s": 0 }
  ]
}
```

**`scripts/scenarios/multi_approach.json`** — Layup then approach: tests that a second APPROACH shot
after a layup has the correct `distanceToPin` calculated from `greenAnchor`.
```json
{
  "name": "Layup + approach — two approach shots",
  "par": 5,
  "waypoints": [
    { "label": "tee",            "lat": 36.5665,  "lng": -121.9441, "pause_s": 8 },
    { "label": "drive lands",    "lat": 36.5690,  "lng": -121.9441, "pause_s": 5 },
    { "label": "layup lands",    "lat": 36.5710,  "lng": -121.9441, "pause_s": 5 },
    { "label": "approach result","lat": 36.5732,  "lng": -121.9441, "pause_s": 5 },
    { "label": "on green",       "lat": 36.5735,  "lng": -121.9441, "pause_s": 0 }
  ]
}
```

### Running the simulation

```bash
# One-time setup
cd /Users/bveber/antigravity/golf_tracker
mkdir -p scripts/scenarios
# (create the JSON files above)

# Boot your emulator in Android Studio, then:
python3 scripts/simulate_gps_walk.py scripts/scenarios/par4_standard.json

# Slower walk for touch-heavy testing
python3 scripts/simulate_gps_walk.py scripts/scenarios/par4_standard.json --speed 1.5

# Print waypoints without running
python3 scripts/simulate_gps_walk.py scripts/scenarios/hole_transition.json --list-shots
```

### Limitations of emulator simulation

- The app's "My Location" blue dot updates from the GPS fix, but the live-user-location stream in
  the ViewModel may have a small lag (FusedLocationClient buffers). Allow 2–3 seconds after the
  script reaches a waypoint before tapping UI elements.
- The 5-second GPS update interval means scenario walks have coarse granularity. A `--speed 1.0`
  (very slow) will produce smoother intermediate fixes but longer scenarios.
- The emulator cannot simulate GPS accuracy variance (all fixes report perfect accuracy). Real-device
  testing on an actual course remains the only way to test GPS jitter handling.

---

## Layer 3 — Manual Test Checklist

These scenarios cannot be automated because they require observing visual/UI behavior:

| # | Scenario | Pass criteria |
|---|---------|---------------|
| 1 | Open GPS on an unmapped hole (no stored tee/green) | Camera centers on current GPS fix. Flag is ~200y ahead. No stale previous-hole flag visible. |
| 2 | Open GPS on a mapped hole | Camera animates to frame tee→green. Flag sits on the green. Distance label shows correct yardage to green. |
| 3 | Select Driver on a TEE shot | Flag/target moves to stock-distance along bearing. Green distance label updates to remaining distance to green anchor (not to flag's new position). |
| 4 | Walk from tee to ball (via GPS script) | Player marker hops along with GPS. User does NOT need to tap "Snap to Me". |
| 5 | Manually drag player marker, then walk | Marker stays at drag position. "Snap to Me" button glows to indicate it's needed. After tapping, resumes auto-follow. |
| 6 | Walk to within 35y of green with APPROACH selected (no explicit chip choice) | Shot type chip auto-selects CHIP. |
| 7 | Manually select APPROACH when 35y from green | CHIP auto-suggestion does NOT override. |
| 8 | Complete hole, open GPS on next hole | Flag is reset to next hole's green (if mapped) or 200y ahead. Never on the previous green. |
| 9 | Tap "Track Shot" without selecting club | Shot records without club. Shot ribbon shows type + "..." distance. No crash. |
| 10 | Delete a tracked shot from the ribbon | Shot is removed from UI and database. Subsequent distances recalculate correctly. |

---

## Implementation Order

1. Add `LocationSource` interface + `FusedLocationSource` (enables all ViewModel unit tests)
2. Write `FakeRoundRepository` and `FakeCourseRepository` (reusable across test suite)
3. Write `GpsViewModelTest` with the cases above
4. Implement Solution 1 changes (tests drive and verify the design)
5. Create `scripts/simulate_gps_walk.py` + 5 scenario files
6. Run all scenarios against a running emulator
7. Walk the manual checklist

This order means the unit tests exist before the implementation — any regression in future changes
is caught without needing to run an emulator.
