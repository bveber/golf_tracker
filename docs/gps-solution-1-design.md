# GPS UX Solution 1 — Design Doc

**Goal:** Eliminate the three concrete UX bugs in the GPS shot-tracking flow without introducing new data
sources or schema changes. All changes live in `GpsViewModel.kt`, `GpsUiState`, and `GpsScreen.kt`.

---

## Root Cause Analysis

### Bug 1 — Flag points at previous green after hole advance

In `setTrackingContext` ([GpsViewModel.kt:300–314](../app/src/main/java/com/golftracker/ui/gps/GpsViewModel.kt#L300)),
when the new hole has no stored tee/green, the fallback is:
```kotlin
playerLocation = state.playerLocation ?: knownTee,
flagLocation   = state.flagLocation   ?: knownGreen
```
This explicitly *retains* the previous hole's marker positions. After finishing hole 4, `flagLocation`
is on hole 4's green; when you open hole 5 (unmapped), the green marker sits on hole 4's green and
never moves until you manually drag it.

### Bug 2 — The pin marker and the shot target are the same object

`GpsUiState.flagLocation` simultaneously serves as:
- The pin / distance-to-green reference
- The target the player aims at for the current shot

When a club is selected for a tee shot, `onClubSelected` moves `flagLocation` to stock distance in the
direction of the current flag ([GpsViewModel.kt:444–450](../app/src/main/java/com/golftracker/ui/gps/GpsViewModel.kt#L444)).
After a 270-yard drive, your ball is now ~270 yards from the tee and the "pin" is also ~270 yards from
the tee — i.e., roughly *where you are*. The 40-yard CHIP threshold then fires.

### Bug 3 — Shot logging requires manual "Snap to Me" tap

`onTrackShot` records from `state.playerLocation`, which is the *draggable* green marker, not
`liveUserLocation`. You must tap "Snap to Me" to sync them before every shot.

---

## Proposed Changes

### Change 1 — Separate `greenAnchor` from `flagLocation` in `GpsUiState`

Add a new field:

```kotlin
// GpsUiState
val greenAnchor: LatLng? = null,  // Persistent pin position; never moved by shot targeting
```

`greenAnchor` is set once when `setTrackingContext` loads a hole's stored green coordinates, and again
(automatically) when `onGreenReached` saves a new green position. It is never dragged by the user and
never overwritten by club selection.

`flagLocation` becomes purely "target for this shot." It is reset to `greenAnchor` after each
`onTrackShot` call. The draggable flag marker and long-press target remain for intentional aim
adjustments (playing to a specific pin position is a legitimate use case).

Affected callsites:
- `checkProximityAndAutoSuggest`: change `state.flagLocation` → `state.greenAnchor` for the CHIP
  distance check. This is the core fix — the CHIP suggestion is now keyed to the *actual pin*, not
  wherever you aimed your last shot.
- `onTrackShot`: change `distToFlag` source to `state.greenAnchor` for `distanceToPin` calculation.
  The shot's `targetLocation` keeps `state.flagLocation` (what you aimed at).
- `persistShotUpdate`: no change needed — `targetLocation` on `TrackedShot` already carries the
  intended aim point.

### Change 2 — Reset markers cleanly on hole change

In the `else` branch of `setTrackingContext` (hole has no stored tee/green), replace the
keep-stale-state fallback:

```kotlin
// BEFORE (retains previous hole's flag)
playerLocation = state.playerLocation ?: knownTee,
flagLocation   = state.flagLocation   ?: knownGreen

// AFTER
playerLocation = knownTee,   // null if not yet mapped; GPS fill-in will seed it
flagLocation   = null,       // force a clean reset
greenAnchor    = knownGreen  // null if unmapped; flag resets to anchor when anchor arrives
```

Then in `updateLocations` (the GPS callback), extend the seeding logic:
```kotlin
// Seed flag → greenAnchor if anchor just arrived; else 200y ahead of player on first fix
flagLocation = state.flagLocation
    ?: state.greenAnchor
    ?: resetFlagLocation(latLng)
greenAnchor = state.greenAnchor ?: resetFlagLocation(latLng)
```

When a hole *is* mapped (both tee and green exist), the existing `knownHoleFrame` camera animation
and marker placement are already correct — no change needed there.

### Change 3 — Player marker auto-follows GPS by default

Add a flag to `GpsUiState`:
```kotlin
val playerFollowsGps: Boolean = true,
```

In `updateLocations`, when `playerFollowsGps` is true, set `playerLocation = latLng` on every GPS
tick (not just the first fix). This makes the green ball hop to your position every 5 seconds without
any tap.

When does the user want to *not* follow GPS?
- They've dragged the marker to a specific spot to log a shot that happened before they opened the
  screen (e.g., they forgot to log the tee shot).
- They long-pressed to place a hypothetical position.

Trigger `playerFollowsGps = false` from `onPlayerDragged`. Reset it to `true` after any
`onTrackShot` call (the logged shot is done; resume following). The "Snap to Me" FAB also resets it.

The effect: walk to your ball, open the panel, tap "Track Shot" — zero extra taps for position.

### Change 4 — Non-sticky CHIP auto-suggestion

`checkProximityAndAutoSuggest` currently fires on every 5-second GPS tick. Once it flips to CHIP, it
never flips back even if you manually selected APPROACH. It also uses `flagLocation` (bug 2).

New logic:
```kotlin
// GpsUiState
val userChoseCurrentShotType: Boolean = false,
```
Set `true` when the user taps a shot-type chip. Clear it after `onTrackShot` (advancing to next shot).

In `checkProximityAndAutoSuggest`:
```kotlin
if (!state.userChoseCurrentShotType
    && state.greenAnchor != null  // use anchor, not flag
    && dist < 40
    && state.pendingShotType == ShotType.APPROACH
) {
    _uiState.update { it.copy(pendingShotType = ShotType.CHIP) }
}
// Never auto-flip back; user walking away from green doesn't un-CHIP
```

`onShotTypeSelected` sets `userChoseCurrentShotType = true`. `onTrackShot` clears it.

---

## State Diagram (simplified)

```
Hole opens (setTrackingContext)
  ├── mapped hole → playerLocation=tee, flagLocation=greenAnchor=green, playerFollowsGps=true
  └── unmapped    → playerLocation=null, flagLocation=null, greenAnchor=null, playerFollowsGps=true
         │
         └── first GPS fix → playerLocation=fix, flagLocation=greenAnchor=fix+200y

User walking (updateLocations every 5s)
  └── playerFollowsGps=true → playerLocation=liveLocation

User selects club (TEE)
  └── flagLocation moves to stockDistance along bearing (UNCHANGED — aim target, not pin)

User taps "Track Shot"
  ├── records TrackedShot(location=playerLocation, targetLocation=flagLocation, distanceToPin=greenAnchor dist)
  ├── flagLocation resets to greenAnchor
  ├── playerFollowsGps = true
  └── userChoseCurrentShotType = false

User drags player marker
  └── playerFollowsGps = false

User taps "Snap to Me"
  └── playerLocation=liveLocation, playerFollowsGps = true

GPS fires, dist-to-greenAnchor < 40y, userChoseCurrentShotType=false
  └── pendingShotType = CHIP

User taps "⛳ Green" (onGreenReached)
  └── finalizes last shot, saves greenAnchor to DB, navigates back
```

---

## Files Changed

| File | Nature of change |
|------|-----------------|
| `ui/gps/GpsViewModel.kt` | Add `greenAnchor`, `playerFollowsGps`, `userChoseCurrentShotType` to state; update `setTrackingContext`, `updateLocations`, `onTrackShot`, `onClubSelected`, `onPlayerDragged`, `checkProximityAndAutoSuggest`, `onGreenReached` |
| `ui/gps/GpsUiState` (same file) | 3 new fields on `GpsUiState` |
| `ui/gps/GpsScreen.kt` | "Snap to Me" FAB calls `viewModel.snapToMe()` instead of inline logic; no structural changes |

No database schema changes. No new Room entities. No migration required.

---

## Out of Scope for Solution 1

- Pre-mapped greens from OSM (Solution 2)
- Front/middle/back-of-green yardages
- Satellite image segmentation (Solution 3)
- Point-in-polygon green detection

---

## Open Questions

1. **Club selection and flag reset:** When the user picks a club for an APPROACH shot from 140y, should
   `flagLocation` stay at `greenAnchor` (the actual pin) rather than moving to stock distance? Currently
   flag-to-stock-distance is *only* on TEE shots, so this is already correct. Worth verifying.

2. **First-hole first-fix ordering:** On the opening hole with no GPS yet, `setTrackingContext` fires
   before the first location callback. This means `greenAnchor` and `flagLocation` are null. The GPS
   seeding in `updateLocations` must handle this gracefully (it already has a null guard for
   `state.flagLocation`; needs a parallel one for `state.greenAnchor`).

3. **Multi-tee courses:** Some courses have different tee positions for the same hole (tournament vs
   member). The stored `hole.teeLat/Lng` is a single point. This is a pre-existing limitation and
   not addressed here.
