# GPS & Course Management

## Overview
Golf Tracker uses real-time GPS tracking to automate shot distance measurements and manage hole/green locations. This feature ensures that the golfer can focus on their game while the app handles the coordinates.

Key components:
*   [GpsViewModel.kt](file:///Users/bveber/antigravity/golf_tracker/app/src/main/java/com/golftracker/ui/gps/GpsViewModel.kt)
*   [GpsUtils.kt](file:///Users/bveber/antigravity/golf_tracker/app/src/main/java/com/golftracker/ui/gps/GpsUtils.kt)
*   [ShotDistanceCalculator.kt](file:///Users/bveber/antigravity/golf_tracker/app/src/main/java/com/golftracker/util/ShotDistanceCalculator.kt)

## Coordinate System
The app works with `Latitude` and `Longitude` coordinates. It uses the `Haversine` formula (via `GpsUtils.kt`) to calculate the distance between two points in yards.

## Location Persistence
To improve the user experience, the app saves locations for future use:
*   **Tee Locations**: Saved per hole and tee set. When a golfer starts a round, the tee marker is automatically placed at the last known location for that tee set.
*   **Green/Hole Locations**: Saved per course and hole. Since green locations rarely change, the app persists the pin location across rounds.
*   **Update Logic**: If a golfer manually moves a marker by more than 10 yards, the app prompts to save this as the new default location for that course.

## Distance Calculations
The `ShotDistanceCalculator` handles the logic for deriving shot lengths:
*   **Forward Solving**: Calculating distance traveled based on start (e.g., Tee) and end (e.g., current GPS position) coordinates.
*   **Reverse Solving**: Estimating the ending distance to the pin if only the distance traveled and shot outcome (e.g., "Short") are known.
*   **Shot Outcome Mapping**: Maps distances and directions (Left/Right/Short/Long) to categorical `ShotOutcome` enums (ON_TARGET, MISS_LEFT, etc.).

## GPS Jitter & Filtering
GPS signals can be "noisy," especially in stationary positions. 
*   **State Updates**: The `GpsViewModel` handles state updates to ensure marker manipulation remains smooth even when GPS coordinates are fluctuating.
*   **Background Processing**: Heavy coordinate calculations are offloaded to background threads to prevent UI lag on the map screen.

## Dispersion Tracking
A secondary but critical use of GPS is calculating **Dispersion Offsets**.
*   **GPS-Derived Dispersion**: When a shot is tracked via GPS, the app automatically calculates how far left/right and short/long the ball finished relative to the target.
*   **Preservation**: This data is preserved even if the user manually updates other shot details, ensuring that the "truth" of the GPS track is maintained for the Stats Dashboard.
