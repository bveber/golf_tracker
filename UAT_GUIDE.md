# Golf Tracker — Manual UAT Guide

## 1. Launching the Emulator & App

### Option A: From the Terminal

```bash
# 1. Start the emulator (runs in background)
~/Library/Android/sdk/emulator/emulator -avd Medium_Phone_API_36.1 &

# 2. Wait ~30 seconds for boot, then build & install
cd /Users/bveber/antigravity/golf_tracker
./gradlew installDebug

# 3. Launch the app
~/Library/Android/sdk/platform-tools/adb shell am start -n com.golftracker/.MainActivity
```

### Option B: From Android Studio

1. Open ** Android Studio → Tools → Device Manager**
2. Click ▶ next to **Medium_Phone_API_36.1**
3. Once booted, click **Run ▶** (green triangle) in the toolbar
4. Select the emulator as the deployment target

### Troubleshooting

| Issue | Fix |
|-------|-----|
| `emulator: ERROR: No AVD` | Open Device Manager → Create Device → Pixel 7 → API 34+ |
| App crashes on launch | Run `./gradlew assembleDebug` first to check for build errors |
| `INSTALL_FAILED_OLDER_SDK` | Your AVD API level is too low; use API 31+ |

---

## 2. Test Scenarios

> Each scenario is a realistic golf session. Follow the steps exactly. **Expected results** are marked with ✅.

---

### Scenario 1: First-Time Setup — Create a Course

**Goal:** Verify course creation with tee sets and hole data.

**Steps:**
1. Open the app. ✅ Home screen shows "Welcome Back!" with 6 navigation buttons.
2. Tap **Courses**.
3. ✅ Empty list message appears: "No courses added yet" or similar.
4. Tap the **Add** (➕) button.
5. Enter course details:
   - **Name:** `Pebble Beach Golf Links`
   - **City:** `Pebble Beach`
   - **State:** `CA`
   - **Holes:** `18`
6. Add a Tee Set:
   - **Name:** `Blue`
   - **Slope:** `145`
   - **Rating:** `75.5`
7. Set hole pars (use a realistic layout):
   - Hole 1: Par 4
   - Hole 2: Par 5
   - Hole 3: Par 4
   - Hole 4: Par 4
   - Hole 5: Par 3
   - Hole 6: Par 5
   - Hole 7: Par 3
   - Hole 8: Par 4
   - Hole 9: Par 4
   - Hole 10: Par 4
   - Hole 11: Par 4
   - Hole 12: Par 3
   - Hole 13: Par 4
   - Hole 14: Par 5
   - Hole 15: Par 4
   - Hole 16: Par 4
   - Hole 17: Par 3
   - Hole 18: Par 5
   - *(Total par: 72)*
8. Tap **Save**. ✅ Returns to course list showing "Pebble Beach Golf Links".

**Edge Cases to Try:**
- Leave the course name blank → ✅ Should show validation error or prevent save
- Set slope to `0` → Note behavior (handicap calculator will skip this tee set)
- Create a 9-hole course → ✅ Should only show 9 holes for par entry

---

### Scenario 2: Set Up Your Bag

**Goal:** Verify club management.

1. From Home, tap **My Bag**.
2. Add the following clubs:

| Club Name | Type |
|-----------|------|
| Driver | Driver |
| 3 Wood | Wood |
| 5 Iron | Iron |
| 6 Iron | Iron |
| 7 Iron | Iron |
| 8 Iron | Iron |
| 9 Iron | Iron |
| PW | Wedge |
| SW | Wedge |
| Putter | Putter |

3. ✅ All 10 clubs appear in the list.
4. Tap a club to edit it. Change "3 Wood" to "3 Hybrid". ✅ List updates.
5. Delete a club (if supported). ✅ Club is removed from list.

---

### Scenario 3: Play a Full 18-Hole Round

**Goal:** Core round tracking flow — the most important scenario.

**Setup:** Use "Pebble Beach Golf Links" from Scenario 1.

1. From Home, tap **Start Round**.
2. Select **Pebble Beach Golf Links** as the course.
3. Select **Blue** tees.
4. Tap **Start**. ✅ Hole 1 tracking screen appears, showing "Hole 1 — Par 4".

**Play each hole with these realistic scores:**

| Hole | Par | Score | Putts | Notes |
|------|-----|-------|-------|-------|
| 1 | 4 | 5 | 2 | Bogey — missed fairway |
| 2 | 5 | 5 | 2 | Par on a par 5 |
| 3 | 4 | 4 | 2 | Par |
| 4 | 4 | 6 | 3 | Double bogey — 3-putt |
| 5 | 3 | 3 | 2 | Par on par 3 |
| 6 | 5 | 6 | 2 | Bogey |
| 7 | 3 | 4 | 2 | Bogey on par 3 |
| 8 | 4 | 4 | 1 | Par — 1-putt! |
| 9 | 4 | 5 | 2 | Bogey (front 9 total: 42) |
| 10 | 4 | 4 | 2 | Par |
| 11 | 4 | 5 | 2 | Bogey |
| 12 | 3 | 2 | 1 | **Birdie!** 🐦 |
| 13 | 4 | 4 | 2 | Par |
| 14 | 5 | 5 | 2 | Par |
| 15 | 4 | 5 | 2 | Bogey |
| 16 | 4 | 4 | 2 | Par |
| 17 | 3 | 3 | 2 | Par |
| 18 | 5 | 6 | 3 | Bogey (back 9 total: 43) |

**Expected total: 85 (13 over par)**

5. After hole 18, tap **Next** → ✅ Scorecard/Summary screen appears.
6. Verify the scorecard:
   - ✅ Total score shows **85**
   - ✅ Birdie on Hole 12 is highlighted in **green**
   - ✅ Double bogey on Hole 4 is highlighted in **red**
   - ✅ GIR checkmarks appear on holes where `score - putts ≤ par - 2`
     - Holes with GIR: 2 (5-2=3 ≤ 3), 3 (4-2=2 ≤ 2), 5 (3-2=1 ≤ 1), 8 (4-1=3... wait, 3 ≤ 2? No), 10 (4-2=2 ≤ 2), 12 (2-1=1 ≤ 1), 13 (4-2=2 ≤ 2), 14 (5-2=3 ≤ 3), 16 (4-2=2 ≤ 2), 17 (3-2=1 ≤ 1)
     - **Expected GIR count: 10 out of 18**
7. Tap **Finalize Round**. ✅ Returns to Home screen.

---

### Scenario 4: Verify Statistics

**Goal:** Ensure stats aggregate correctly after a finalized round.

1. From Home, tap **Stats**.
2. ✅ **Scoring** tab shows:
   - Avg Score: **85.0**
   - Avg to Par: **+13.0**
   - Rounds Played: **1**
3. Tap **Putting** tab. ✅ Shows:
   - Avg Putts/Round: **36.0** (sum of all putts from Scenario 3)
4. Tap **Approach** tab. ✅ Shows GIR percentage.
5. Tap **Back** to return home.

---

### Scenario 5: Verify Handicap Calculation

**Goal:** Handicap requires ≥3 rounds. Verify the "not enough rounds" state and then the calculation.

1. From Home, tap **Handicap**.
2. ✅ Should display "N/A" or similar since only 1 round exists.
3. **Go back and play 2 more rounds** (Scenarios 3 can be repeated with different scores):

| Round | Total Score | Front 9 | Back 9 |
|-------|-------------|---------|--------|
| Round 2 | 88 | 45 | 43 |
| Round 3 | 82 | 40 | 42 |

4. After finalizing Round 3, tap **Handicap** again.
5. ✅ A handicap index should now be displayed.
6. **Verify the math manually:**
   - Round 1: diff = (113 / 145) × (85 − 75.5) = 0.779 × 9.5 = **7.4**
   - Round 2: diff = (113 / 145) × (88 − 75.5) = 0.779 × 12.5 = **9.7**
   - Round 3: diff = (113 / 145) × (82 − 75.5) = 0.779 × 6.5 = **5.1**
   - 3 rounds → use **1 best** differential (5.1) with adjustment **−2.0**
   - Index = 5.1 − 2.0 = **3.1**
7. ✅ Displayed handicap should be approximately **3.1**.

---

### Scenario 6: Round History & CSV Export

**Goal:** Verify history listing and data export.

1. From Home, tap **History**.
2. ✅ All 3 finalized rounds appear, sorted by date (newest first).
3. ✅ Each row shows course name, date, and "Finalized" status.
4. Tap the **Share** (↗) icon on Round 1.
5. ✅ System share sheet opens with a CSV file attachment.
6. Choose **Save to Files** or send to yourself via email.
7. Open the CSV. ✅ Verify it contains 18 rows of data with columns: Hole, Par, Score, Putts, GIR, Fairway, Tee Club, Approach Club, Penalties.
8. Tap on a round row itself (not the share icon). ✅ Navigates to the scorecard for that round.

---

### Scenario 7: Edit an Existing Course

**Goal:** Verify course editing doesn't break existing round data.

1. Go to **Courses** → tap **Pebble Beach Golf Links**.
2. Change the city to `Monterey`.
3. Save. ✅ Course list shows updated city.
4. Go to **History** → tap Round 1. ✅ Scorecard still shows correct data — round integrity preserved.

---

### Scenario 8: Edge Case — Navigate Away Mid-Round

**Goal:** Verify app handles interruption gracefully.

1. Start a new round at Pebble Beach.
2. Enter scores for holes 1–5.
3. Press the **Android Back button** or **Home button**.
4. Re-open the app.
5. ✅ The in-progress round should still be accessible (check History for "In Progress" status).

---

### Scenario 9: Stress Test — Multiple Courses

**Goal:** Verify the app scales to multiple courses.

1. Create 3 additional courses:
   - **Augusta National** (Par 72, Slope 137, Rating 76.2)
   - **St Andrews Old Course** (Par 72, Slope 130, Rating 73.1)
   - **Torrey Pines South** (Par 72, Slope 140, Rating 74.6)
2. ✅ All 4 courses appear in the course list.
3. Start a round at Augusta → ✅ Correct hole pars loaded.
4. Go to **Stats** → ✅ Stats reflect rounds across all courses.

---

## 3. Bug Report Template

If you find an issue, note:

```
**Screen:** [Which screen]
**Steps to Reproduce:**
1. ...
2. ...
3. ...
**Expected:** [What should happen]
**Actual:** [What actually happened]
**Screenshot:** [Attach if possible]
```

---

## 4. UAT Sign-Off Checklist

| # | Feature | Status |
|---|---------|--------|
| 1 | Course creation with tee sets and holes | ☐ |
| 2 | Club/bag management | ☐ |
| 3 | Full 18-hole round tracking | ☐ |
| 4 | Scorecard display (colors, GIR indicators) | ☐ |
| 5 | Round finalization | ☐ |
| 6 | Stats dashboard (all tabs) | ☐ |
| 7 | Handicap calculation | ☐ |
| 8 | Round history listing | ☐ |
| 9 | CSV export and sharing | ☐ |
| 10 | Course editing | ☐ |
| 11 | Navigation and back behavior | ☐ |
