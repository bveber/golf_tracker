# Golf Tracker Project Overview

## Project Description
Golf Tracker is a comprehensive Android application designed to help golfers track their performance using advanced statistics, primarily focused on **Strokes Gained (SG)**. It allows users to log shots during a round, track GPS locations for tees and greens, and view detailed performance analytics.

The application uses a local SQLite database (via Room) for persistence and integrates with Google Drive for data backup and synchronization.

## Key Functionality
1.  **Shot Tracking**: Users can log every shot, including club selection, lie type, distance to pin, and outcome.
2.  **GPS Integration**: Real-time GPS tracking for measuring shot distances and setting hole/green locations.
3.  **Strokes Gained (SG) Analytics**:
    *   Calculates SG for every shot (Driving, Approach, Short Game, Putting).
    *   Uses baseline data (e.g., `sg_baseline.csv`) to compare performance against various skill levels.
    *   Handles penalties and recovery shots (e.g., punch out, hazard).
4.  **Stats Dashboard**: Visualizes performance trends, SG breakdowns, dispersion charts, and other key metrics (GIR, Scrambling, etc.).
5.  **Course Management**: Allows users to download and manage golf course data, including tee boxes and hole layouts.
6.  **Data Backup**: Synchronizes round data with Google Drive.

## Detailed Documentation
For a deeper dive into specific components of the application, please refer to the following guides:

### Features & Logic
*   **Strokes Gained Analytics**: [strokes_gained_analytics.md](file:///Users/bveber/antigravity/golf_tracker/docs/features/strokes_gained_analytics.md) - The math behind SG and baseline data.
*   **GPS & Course Management**: [gps_and_course_management.md](file:///Users/bveber/antigravity/golf_tracker/docs/features/gps_and_course_management.md) - How the app handles locations and distances.
*   **Shot Tracking Logic**: [shot_execution_tracking.md](file:///Users/bveber/antigravity/golf_tracker/docs/features/shot_execution_tracking.md) - Core state machine for tracking shots.

### UI & User Workflows
*   **Stats Dashboard**: [stats_dashboard.md](file:///Users/bveber/antigravity/golf_tracker/docs/ui/stats_dashboard.md) - Guide to the analytics visualizations.
*   **Live Round Tracking**: [live_round_tracking.md](file:///Users/bveber/antigravity/golf_tracker/docs/ui/live_round_tracking.md) - The logging workflow and advanced UI options.

## Codebase Map

### Core Logic & Calculators
*   [StrokesGainedCalculator.kt](file:///Users/bveber/antigravity/golf_tracker/app/src/main/java/com/golftracker/util/StrokesGainedCalculator.kt) (See [SG Docs](file:///Users/bveber/antigravity/golf_tracker/docs/features/strokes_gained_analytics.md))
*   [GirCalculator.kt](file:///Users/bveber/antigravity/golf_tracker/app/src/main/java/com/golftracker/util/GirCalculator.kt)
*   [HandicapCalculator.kt](file:///Users/bveber/antigravity/golf_tracker/app/src/main/java/com/golftracker/util/HandicapCalculator.kt)
*   [ShotDistanceCalculator.kt](file:///Users/bveber/antigravity/golf_tracker/app/src/main/java/com/golftracker/util/ShotDistanceCalculator.kt) (See [GPS Docs](file:///Users/bveber/antigravity/golf_tracker/docs/features/gps_and_course_management.md))

### Data Layer (Persistence & Repositories)
*   [StatsRepository.kt](file:///Users/bveber/antigravity/golf_tracker/app/src/main/java/com/golftracker/data/repository/StatsRepository.kt) (See [Stats Docs](file:///Users/bveber/antigravity/golf_tracker/docs/ui/stats_dashboard.md))
*   [RoundRepository.kt](file:///Users/bveber/antigravity/golf_tracker/app/src/main/java/com/golftracker/data/repository/RoundRepository.kt)
*   [AppDatabase.kt](file:///Users/bveber/antigravity/golf_tracker/app/src/main/java/com/golftracker/data/db/AppDatabase.kt)
*   `app/src/main/java/com/golftracker/data/model/` (e.g., [ShotType.kt](file:///Users/bveber/antigravity/golf_tracker/app/src/main/java/com/golftracker/data/model/ShotType.kt))

### UI Layer (Screens & ViewModels)
*   `app/src/main/java/com/golftracker/ui/gps/` (See [GPS Docs](file:///Users/bveber/antigravity/golf_tracker/docs/features/gps_and_course_management.md))
*   `app/src/main/java/com/golftracker/ui/round/` (See [Round Docs](file:///Users/bveber/antigravity/golf_tracker/docs/ui/live_round_tracking.md))
*   `app/src/main/java/com/golftracker/ui/stats/` (See [Stats Docs](file:///Users/bveber/antigravity/golf_tracker/docs/ui/stats_dashboard.md))
*   `app/src/main/java/com/golftracker/ui/course/`

### Resources & Baseline Data
*   [sg_baseline.csv](file:///Users/bveber/antigravity/golf_tracker/app/src/main/res/raw/sg_baseline.csv) (Ref: [SG Docs](file:///Users/bveber/antigravity/golf_tracker/docs/features/strokes_gained_analytics.md))

## Guidance for Future Agents
*   When fixing **SG calculations**, prioritize reviewing `StrokesGainedCalculator.kt` and the baseline CSV.
*   For **UI or State** issues in the dashboard, check the `StatsViewModel` and `StatsRepository.kt`.
*   For **GPS/Marker** issues, focus on the `gps` package and `ShotDistanceCalculator.kt`.
*   Always ensure that any data changes are reflected in the **Room DAOs** and **Repositories**.
