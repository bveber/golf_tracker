# Stats Dashboard

## Overview
The Stats Dashboard provides a comprehensive visualization of performance trends and Strokes Gained breakdowns. It consumes aggregated data from the [StatsRepository.kt](file:///Users/bveber/antigravity/golf_tracker/data/repository/StatsRepository.kt).

Key components:
*   [StatsDashboardScreen.kt](file:///Users/bveber/antigravity/golf_tracker/app/src/main/java/com/golftracker/ui/stats/StatsDashboardScreen.kt)
*   [StatsViewModel.kt](file:///Users/bveber/antigravity/golf_tracker/app/src/main/java/com/golftracker/ui/stats/StatsViewModel.kt)

## Analytical Tabs

### 1. Scoring
*   **Handicap Index**: Displays the official or estimated handicap.
*   **Scoring Trend**: A line chart showing "To Par" performance over recent rounds.
*   **Score Breakdown**: A horizontal distribution bar showing the percentage of Eagles, Birdies, Pars, Bogeys, etc.

### 2. Driving
*   **Fairways Hit %**: The primary accuracy metric for OTT.
*   **Proximity/Distance**: Average driving distance, with a separate metric excluding mishits.
*   **Driving Dispersion Chart**: A 2D visualization showing the distribution of misses (Left vs. Right and Short vs. Long).

### 3. Approach
*   **GIR %**: Greens in Regulation, categorized by distance ranges (e.g., <100y, 100-150y, etc.).
*   **Near-GIR**: A proprietary metric for shots that finished just off the green (e.g., in the fringe).
*   **On-Target by Lie**: Shows how GIR% varies between Fairway and Rough lies.

### 4. Chipping & Putting
*   **Up & Down %**: Success rate from around the green.
*   **Avg Proximity**: Average distance of the first putt after a chip.
*   **Putts per Round**: Normalized to 18 holes.
*   **Putt Make Probability**: Statistical visualization of putting performance from various distances.

### 5. Strokes Gained (The "Holy Grail")
*   **Component Breakdown**: Displays the total SG per round, split into OTT, APP, ARG, and PUTT.
*   **Penalty Integration**: Clearly shows the total strokes lost to penalties.
*   **Historical Trends**: Allows users to see if specific areas of their game are improving over time.

## Filtering & Data Management
The dashboard features an advanced filtering system:
*   **Course & Tee Filter**: Narrow stats to specific courses or tee sets.
*   **Time-Based Patterns**: Filter by year, date range, or "Last N Rounds."
*   **Round Management**: Users can manually exclude specific rounds (e.g., a "practice" round or a outlier) from the statistical aggregate.
*   **Mishit Toggle**: Statistics like average driving distance can be toggled to include or exclude shots marked as "mishits."
