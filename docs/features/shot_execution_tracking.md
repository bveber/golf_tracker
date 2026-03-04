# Feature Idea: Shot Execution vs. Outcome Tracking

## Overview
This document outlines a potential future feature for Golf Tracker aimed at distinguishing between **Strike Quality (Execution)** and **Shot Outcome / Strategy**. 

Currently, Strokes Gained (SG) measures the absolute outcome of a shot compared to a baseline. However, SG fails to capture the nuance of a perfectly flushed shot that ends up in a bad spot due to poor club selection, a bad bounce, or unexpected wind. Conversely, a terrible strike might get lucky and end up close to the pin. 

This feature proposes a subjective 0-3 scoring system to track how well the ball was struck, regardless of where it ended up.

## Core Objectives
1. **Identify "Flushed" Shots:** Allow the user to record when they hit the ball exactly as intended, even if the outcome was penalized by SG.
2. **Diagnose Strategy vs. Execution:** By comparing Strike Quality to SG, players can diagnose the root cause of lost strokes:
   - **High Strike Quality + Low SG:** Indicates poor course management, wrong club selection, or bad luck.
   - **Low Strike Quality + Low SG:** Indicates swing mechanics or physical execution issues.
3. **Minimize UI Friction:** Ensure the addition of this metric does not make on-course data entry tedious or overwhelming.

## Proposed 0-3 Strike Quality Scale
Instead of rating the outcome, the user rates the **contact and ball flight** relative to their intent:

*   **3 (Perfect Contact):** Hit exactly on the sweet spot. Perfect trajectory and contact. The ball did exactly what the swing intended.
*   **2 (Fine):** Acceptable contact. Slight mishit or minor deviation, but the ball flight was generally fine.
*   **1 (Just Bad):** Noticeable miss. Heavy chunk, thin, hook/slice. Significant loss of intended distance or control.
*   **0 (Shank / Complete Failure):** Shank, top, or total whiff. 

## UI/UX Integration Ideas (Mitigating "App Busyness")
To avoid overwhelming the user during a round, the implementation must be lightweight. Here are a few approaches if this feature is developed:

### Option 1: The "Outlier" Toggle (Lowest Friction)
Assume every shot is a "2" (Fine) by default. The UI only asks the user to flag the extremes. 
- Introduce a quick "Flushed it!" button (Score 3) and a "Miss" button (Score 0-1) on the standard shot tracking screen. 
- No extra screens required, just an optional quick tap for outliers.

### Option 2: Post-Round Reflection (Zero On-Course Friction)
Do not track this during the round. Instead, present a "Round Review" summary after the round is finalized.
- The app lists approach shots and tee shots where SG was notably low.
- The user is prompted: *"SG was low here. Was this a bad strike or a bad decision?"*
- This encourages post-round reflection without slowing down play.

### Option 3: Unified Post-Hole Slider (Medium Friction)
Instead of rating every single shot, ask for a general "Ball Striking Rating" (0-3) for the entire hole as the user walks to the next tee. 

## Future Data Visualization
If implemented, this data could unlock powerful insights in the Stats dashboard:
- **"The Strategy Tax":** A chart showing shots where Strike Quality was 3, but SG was negative. 
- **"Strike Consistency":** A simple graph tracking the average 0-3 strike rating over the course of the round to identify fatigue in the back 9.
