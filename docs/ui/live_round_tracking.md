# Live Round Tracking

## Overview
The "Live Round" tracking workflow is the primary data entry point for the application. It is designed for efficiency on the course, allowing for quick logging of shots with optional deep-dives into advanced metrics.

Key components:
*   [HoleTrackingScreen.kt](file:///Users/bveber/antigravity/golf_tracker/app/src/main/java/com/golftracker/ui/round/HoleTrackingScreen.kt)
*   [RoundViewModel.kt](file:///Users/bveber/antigravity/golf_tracker/app/src/main/java/com/golftracker/ui/round/RoundViewModel.kt)

## The Logging Workflow

### 1. Tee Shot (Off-the-Tee)
On Par 4 and Par 5 holes, the workflow begins with the drive.
*   **Club Selection**: Users select the club used. The app suggests the default driver or a club based on hole yardage.
*   **Distance**: Driving distance can be entered manually or calculated via GPS.
*   **Outcome**: Users log the result (Fairway, Left, Right, etc.).
*   **In Trouble**: A toggle indicates if the tee shot resulted in a "stymied" or recovery situation.

### 2. Approach Shots
Approach shots are logged sequentially.
*   **Sequential Loading**: The app tracks `Shot 1`, `Shot 2`, etc.
*   **Lie Type**: Crucial for SG calculations (Fairway, Rough, Sand, Tee, Other).
*   **Recovery Toggle**: Indicates if the shot was a "punch out" or recovery attempt.
*   **Distance to Pin**: The primary metric for approach performance.

### 3. Short Game (Around the Green)
Once near the green, the UI shifts focus to "Short Game" metrics.
*   **Chips & Sand Shots**: Users log the number of attempts from off the green.
*   **Up & Down Tracking**: The app automatically detects "Up & Down" opportunities and successes based on GIR (Greens in Regulation) and Score.

### 4. Putting
*   **Putt Count**: Quick entry for the total number of putts.
*   **Putt Distances**: Detailed distance logging for each putt is available for more accurate SG Putting analytics.

## Advanced Data Entry

### Re-Tee (OB / Lost Ball)
A dedicated "Re-tee" button handles situations where a stroke is lost off the tee. This automatically:
*   Incurs the appropriate penalty stroke.
*   Maintains the "Off-the-Tee" category for the subsequent shot.
*   Sets the end distance to match the starting distance (for OB) to ensure accurate SG attribution.

### Penalty Attribution (Recovery Shots)
For recovery shots, a slider appears in the UI (if the shot's base SG is negative).
*   **Slider Logic**: Allows the golfer to "attribute" the penalty of being stymied back to the previous shot.
*   **Visual Feedback**: Real-time SG updates show how the attribution affects both the recovery shot and the previous "errant" shot.

### Dispersion Options
Every full-swing shot has a "Dispersion Options" dialog.
*   **Manual Entry**: Users can manually enter Left/Right and Short/Long offsets in yards if GPS was not used.
*   **GPS Validation**: A warning icon appears if the manually selected `ShotOutcome` contradicts the GPS-calculated dispersion data.
