# Strokes Gained Analytics

## Overview
Strokes Gained (SG) is the core analytical engine of the Golf Tracker application. It measures a golfer's performance by comparing the number of strokes taken to a statistical baseline.

The primary logic resides in [StrokesGainedCalculator.kt](file:///Users/bveber/antigravity/golf_tracker/app/src/main/java/com/golftracker/util/StrokesGainedCalculator.kt).

## The Mathematical Engine
The SG calculation for a single shot is:
`SG = ExpectedStrokes(Start) - ExpectedStrokes(End) - 1.0 - PenaltyStrokes`

*   **ExpectedStrokes**: The average number of strokes a scratch golfer is expected to take from a given distance and lie.
*   **1.0**: Represents the stroke just taken.
*   **PenaltyStrokes**: Any penalty strokes incurred (e.g., OB, water).

### Baseline Data
The calculator uses [sg_baseline.csv](file:///Users/bveber/antigravity/golf_tracker/app/src/main/res/raw/sg_baseline.csv) as its source of truth.
*   **Distances**: The CSV contains data for various distance "buckets" (e.g., 10y, 20y, 100y).
*   **Lies**: Expected strokes are provided for different lies: Tee, Fairway, Rough, Sand, and Recovery.
*   **Interpolation**: Since the golfer's exact distance rarely matches a bucket, the `StrokesGainedCalculator` performs linear interpolation between the two nearest buckets.

## Key Sub-Calculations

### Off-The-Tee (OTT)
OTT is calculated for the first shot on Par 4 and Par 5 holes. 
*   **Hole Adjustment**: Uses the course rating and hole handicap index to shift the baseline, accounting for hole difficulty.
*   **Outcome Influence**: The end lie (Fairway vs. Rough) significantly impacts the SG of the drive.

### Approach (APP)
Approach shots are those intended to hit the green, excluding the tee shot on Par 4/5s and short-game shots.
*   **Distance to Pin**: Calculated based on the starting and ending distance to the hole.
*   **Gir Integration**: Approach performance is often cross-referenced with Greens in Regulation (GIR) metrics.

### Around the Green (ARG)
Short game shots (Chips and Sand shots) from within ~30-50 yards of the green.
*   **Proximity**: SG for ARG is highly dependent on how close the ball finishes to the hole (the starting distance for the first putt).

### Putting (PUTT)
Putting SG is calculated in feet.
*   **One-Putt Probability**: The baseline provides the expected number of putts from various distances. Making a 20-footer yields a high positive SG, while three-putting from 10 feet yields a large negative SG.

## Special Scenarios

### Penalties (OB / Water / Lost Ball)
*   **Fixed Cost**: An Out of Bounds (OB) shot or Lost Ball typically incurs a 2-stroke penalty (the stroke itself plus the penalty stroke).
*   **Re-tee Logic**: When a re-tee occurs, the calculator ensures that the SG remains negative (typically -2.0) until the subsequent shot is tracked.

### Recovery Shots (Recovery/Trouble)
*   **Strokes Gained Penalty Attribution**: When a golfer is "stymied" (e.g., behind a tree) and must take a recovery shot, the app allows for "Penalty Attribution." 
*   **Shift Logic**: A portion of the negative SG from the recovery shot can be shifted back to the shot that put them in trouble, providing a more accurate assessment of which shot caused the loss of strokes.

### Course & Hole Difficulty
*   **Course Rating**: The total SG for a round is adjusted based on the USGA Course Rating vs. Par. 
*   **Hole Index**: This adjustment is distributed across holes using the handicap index, where the hardest holes received a more "lenient" baseline comparison.
