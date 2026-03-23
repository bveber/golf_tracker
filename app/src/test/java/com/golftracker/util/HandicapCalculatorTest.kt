package com.golftracker.util

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.Date

class HandicapCalculatorTest {

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun dateInYear(year: Int, dayOfYear: Int = 1): Date =
        Calendar.getInstance().also {
            it.set(Calendar.YEAR, year)
            it.set(Calendar.DAY_OF_YEAR, dayOfYear)
        }.time

    private val currentYear = Calendar.getInstance().get(Calendar.YEAR)

    // ─── Differential Calculation ─────────────────────────────────────────

    @Test
    fun `differential formula is correct`() {
        // (113 / slope) * (gross - rating)
        // slope=113, rating=72 → (113/113) * (90-72) = 18.0
        val round = TestDataFactory.roundWithDetails(
            teeSet = TestDataFactory.teeSet(slope = 113, rating = 72.0),
            scorePerHole = 5,  // 5 * 18 = 90 gross
            parPerHole = 4,
            date = dateInYear(currentYear, 1)
        )
        val diffs = HandicapCalculator.calculateDifferentials(listOf(round), currentYear)
        assertEquals(1, diffs.size)
        assertEquals(18.0, diffs[0].value, 0.01)
    }

    @Test
    fun `differential adjusts for different slope`() {
        // slope=130, rating=72 → (113/130) * (90-72) = 15.6..
        val round = TestDataFactory.roundWithDetails(
            teeSet = TestDataFactory.teeSet(slope = 130, rating = 72.0),
            scorePerHole = 5,  // 90 gross
            date = dateInYear(currentYear, 1)
        )
        val diffs = HandicapCalculator.calculateDifferentials(listOf(round), currentYear)
        val rawExpected = (113.0 / 130.0) * (90 - 72)
        val expected = (rawExpected * 10.0).toInt() / 10.0
        assertEquals(expected, diffs[0].value, 0.01)
    }

    @Test
    fun `rounds with zero slope are skipped`() {
        val round = TestDataFactory.roundWithDetails(
            teeSet = TestDataFactory.teeSet(slope = 0, rating = 72.0),
            date = dateInYear(currentYear, 1)
        )
        val diffs = HandicapCalculator.calculateDifferentials(listOf(round), currentYear)
        assertEquals(0, diffs.size)
    }

    @Test
    fun `rounds with zero rating are skipped`() {
        val round = TestDataFactory.roundWithDetails(
            teeSet = TestDataFactory.teeSet(slope = 113, rating = 0.0),
            date = dateInYear(currentYear, 1)
        )
        val diffs = HandicapCalculator.calculateDifferentials(listOf(round), currentYear)
        assertEquals(0, diffs.size)
    }

    @Test
    fun `rounds with all zero scores are skipped`() {
        val round = TestDataFactory.roundWithDetails(
            scorePerHole = 0,
            date = dateInYear(currentYear, 1)
        )
        val diffs = HandicapCalculator.calculateDifferentials(listOf(round), currentYear)
        assertEquals(0, diffs.size)
    }

    @Test
    fun `maximum 20 differentials are produced in descending order`() {
        // Create 25 rounds, with scores getting better (lower) over time.
        // Round 1 (oldest): scorePerHole=10
        // Round 25 (newest): scorePerHole=2
        val rounds = (1..25).map { i ->
            TestDataFactory.roundWithDetails(
                roundId = i,
                date = dateInYear(currentYear, i),
                scorePerHole = 11 - (i / 3).coerceAtMost(8) // score varies
            )
        }
        val diffs = HandicapCalculator.calculateDifferentials(rounds, currentYear)
        assertEquals(20, diffs.size)
        
        // Verify they are descending by date (newest first)
        assertTrue("Differentials should be in descending order by date", 
            diffs.zipWithNext().all { (a, b) -> !a.date.before(b.date) })
            
        // Verify they are the most recent 20 (roundId 6 to 25)
        val roundIds = diffs.map { it.roundId }.toSet()
        assertTrue("Should contain recent round 25", roundIds.contains(25))
        assertFalse("Should not contain old round 1", roundIds.contains(1))
    }

    // ─── 9-Hole Handling ─────────────────────────────────────────────────

    @Test
    fun `9 hole round without trailing handicap uses doubling fallback`() {
        // No prior rounds → no trailing handicap → raw diff doubled
        // slope=113, rating=72 → half-rating=36
        // gross=45, raw9 = (113/113)*(45−36) = 9.0, doubled = 18.0
        val round = TestDataFactory.roundWithDetails(
            roundId = 1,
            totalHoles = 9,
            scorePerHole = 5, // 5 * 9 = 45
            teeSet = TestDataFactory.teeSet(slope = 113, rating = 72.0),
            date = dateInYear(currentYear, 1)
        )
        val diffs = HandicapCalculator.calculateDifferentials(listOf(round), currentYear)
        assertEquals(1, diffs.size)
        assertEquals(9, diffs[0].totalHoles)
        assertEquals(18.0, diffs[0].value, 0.01)
        assertFalse("Should NOT have used expected score for first round", diffs[0].usedExpectedScore)
    }

    @Test
    fun `9 hole round with trailing handicap uses expected score`() {
        // 3 prior 18-hole rounds give a trailing handicap before the 9-hole round.
        // All at slope=113, rating=72, score=90 → diff=18.0 each
        // After 3 rounds: numToUse=1, adj=-2 → index = 18 - 2 = 16.0
        //
        // Then a 9-hole round: gross=45, raw9=9.0, expected = 16.0/2 = 8.0
        // combined = 9.0 + 8.0 = 17.0
        val tee = TestDataFactory.teeSet(slope = 113, rating = 72.0)
        val prior = (1..3).map { i ->
            TestDataFactory.roundWithDetails(
                roundId = i,
                teeSet = tee,
                scorePerHole = 5, // gross 90
                date = dateInYear(currentYear, i)
            )
        }
        val nineHoleRound = TestDataFactory.roundWithDetails(
            roundId = 4,
            totalHoles = 9,
            teeSet = tee,
            scorePerHole = 5, // gross 45
            date = dateInYear(currentYear, 10)
        )

        val allRounds = prior + nineHoleRound
        val diffs = HandicapCalculator.calculateDifferentials(allRounds, currentYear)

        assertEquals(4, diffs.size)
        val nineDiff = diffs.first()
        assertEquals(9, nineDiff.totalHoles)
        assertTrue("Should have used expected score", nineDiff.usedExpectedScore)
        // trailing handicap = 16.0, expected contribution = 8.0, raw9 = 9.0 → combined = 17.0
        assertEquals(17.0, nineDiff.value, 0.01)
    }

    @Test
    fun `9 hole rounds only appear in their own year`() {
        val tee = TestDataFactory.teeSet(slope = 113, rating = 72.0)
        // Round in previous year
        val lastYearRound = TestDataFactory.roundWithDetails(
            roundId = 1,
            teeSet = tee,
            scorePerHole = 5,
            date = dateInYear(currentYear - 1, 1)
        )
        // 9-hole round in current year
        val thisYearRound = TestDataFactory.roundWithDetails(
            roundId = 2,
            totalHoles = 9,
            teeSet = tee,
            scorePerHole = 5,
            date = dateInYear(currentYear, 1)
        )

        val diffs = HandicapCalculator.calculateDifferentials(listOf(lastYearRound, thisYearRound), currentYear)
        // Only the current-year round should appear in the currentYear differentials list
        assertEquals(1, diffs.size)
        assertEquals(9, diffs[0].totalHoles)
    }

    // ─── Yearly Grouping ─────────────────────────────────────────────────

    @Test
    fun `calculateHandicapIndexForYear only uses rounds in that year`() {
        val tee = TestDataFactory.teeSet(slope = 113, rating = 72.0)
        // 3 rounds in prior year with large differentials
        val priorYear = (1..3).map { i ->
            TestDataFactory.roundWithDetails(
                roundId = i,
                teeSet = tee,
                scorePerHole = 10, // very high score → large diff
                date = dateInYear(currentYear - 1, i)
            )
        }
        // 3 rounds in current year with good scores
        val thisYear = (4..6).map { i ->
            TestDataFactory.roundWithDetails(
                roundId = i,
                teeSet = tee,
                scorePerHole = 5, // gross=90, diff=18
                date = dateInYear(currentYear, i - 3)
            )
        }
        val index = HandicapCalculator.calculateHandicapIndexForYear(priorYear + thisYear, currentYear)
        // current year: 3 rounds, 1 best diff=18, adj=-2 → 16.0
        assertNotNull(index)
        assertEquals(16.0, index!!, 0.01)
    }

    @Test
    fun `returns null for fewer than 3 rounds in year`() {
        val tee = TestDataFactory.teeSet(slope = 113, rating = 72.0)
        val rounds = (1..2).map { i ->
            TestDataFactory.roundWithDetails(
                roundId = i,
                teeSet = tee,
                date = dateInYear(currentYear, i)
            )
        }
        assertNull(HandicapCalculator.calculateHandicapIndexForYear(rounds, currentYear))
    }

    // ─── WHS Index Formula ────────────────────────────────────────────────

    @Test
    fun `3 rounds uses 1 best differential with minus 2 adjustment`() {
        val tee = TestDataFactory.teeSet(slope = 113, rating = 72.0)
        val rounds = (1..3).map { i ->
            TestDataFactory.roundWithDetails(
                roundId = i,
                teeSet = tee,
                scorePerHole = 5, // diff=18.0
                date = dateInYear(currentYear, i)
            )
        }
        val index = HandicapCalculator.calculateHandicapIndex(rounds)
        assertNotNull(index)
        // best diff = 18.0, adjustment = -2.0 → 16.0
        assertEquals(16.0, index!!, 0.01)
    }

    @Test
    fun `4 rounds uses 1 best differential with minus 1 adjustment`() {
        val tee = TestDataFactory.teeSet(slope = 113, rating = 72.0)
        val rounds = (1..4).map { i ->
            TestDataFactory.roundWithDetails(
                roundId = i,
                teeSet = tee,
                scorePerHole = 5, // diff=18.0
                date = dateInYear(currentYear, i)
            )
        }
        val index = HandicapCalculator.calculateHandicapIndex(rounds)
        assertEquals(17.0, index!!, 0.01)
    }

    @Test
    fun `handicap index uses best differentials not most recent`() {
        val tee = TestDataFactory.teeSet(slope = 113, rating = 72.0)
        // 5 rounds → uses 1 best differential, no adjustment
        val rounds = listOf(
            TestDataFactory.roundWithDetails(roundId = 1, teeSet = tee, scorePerHole = 6, date = dateInYear(currentYear, 5)),
            TestDataFactory.roundWithDetails(roundId = 2, teeSet = tee, scorePerHole = 5, date = dateInYear(currentYear, 4)),
            TestDataFactory.roundWithDetails(roundId = 3, teeSet = tee, scorePerHole = 7, date = dateInYear(currentYear, 3)),
            TestDataFactory.roundWithDetails(roundId = 4, teeSet = tee, scorePerHole = 4, date = dateInYear(currentYear, 2)), // best
            TestDataFactory.roundWithDetails(roundId = 5, teeSet = tee, scorePerHole = 5, date = dateInYear(currentYear, 1))
        )
        val index = HandicapCalculator.calculateHandicapIndex(rounds)
        // Best 1 out of 5: score=4 per hole → gross=72 → diff=0.0, no adjustment
        assertEquals(0.0, index!!, 0.01)
    }

    @Test
    fun `truncation not rounding`() {
        // slope=113, rating=71.05 → diff=(113/113)*(90-71.05)=18.95
        // rounded to tenths = 19.0 (roundToInt on differential)
        // 5 rounds → best 1 = 19.0, no adjustment → index = 19.0 truncated = 19.0
        val tee = TestDataFactory.teeSet(slope = 113, rating = 71.05)
        val rounds = (1..5).map { i ->
            TestDataFactory.roundWithDetails(
                roundId = i,
                teeSet = tee,
                scorePerHole = 5,
                date = dateInYear(currentYear, i)
            )
        }
        val index = HandicapCalculator.calculateHandicapIndex(rounds)
        assertNotNull(index)
        assertTrue("Index should be truncated, not rounded up", index!! <= 19.1)
    }

    @Test
    fun `blow-up hole is capped by simplified Net Double Bogey`() {
        // 17 pars (4s) and one "15" on par 4 → capped at 4+5=9
        // Adjusted gross = 68 + 9 = 77, diff = 77 - 72 = 5.0
        val round = TestDataFactory.roundWithDetails(
            teeSet = TestDataFactory.teeSet(slope = 113, rating = 72.0),
            parPerHole = 4,
            date = dateInYear(currentYear, 1)
        )
        val blowupHole = round.holeStats[0].copy(
            holeStat = round.holeStats[0].holeStat.copy(score = 15)
        )
        val adjustedStats = round.holeStats.toMutableList()
        adjustedStats[0] = blowupHole
        val adjustedRound = round.copy(holeStats = adjustedStats)

        val diffs = HandicapCalculator.calculateDifferentials(listOf(adjustedRound), currentYear)
        assertEquals(5.0, diffs[0].value, 0.01)
    }

    // ─── Time Series ──────────────────────────────────────────────────────

    @Test
    fun `time series grows as rounds are added`() {
        val tee = TestDataFactory.teeSet(slope = 113, rating = 72.0)
        val rounds = (1..5).map { i ->
            TestDataFactory.roundWithDetails(
                roundId = i,
                teeSet = tee,
                scorePerHole = 5,
                date = dateInYear(currentYear, i)
            )
        }
        val series = HandicapCalculator.buildHandicapTimeSeries(rounds)
        // First 2 rounds produce no index (need ≥3), so series should have 3 points
        assertEquals(3, series.size)
        assertTrue("Series should be in chronological order", series.zipWithNext().all { (a, b) -> !a.date.after(b.date) })
    }

    @Test
    fun `time series includes handicap point for every year with enough rounds`() {
        val tee = TestDataFactory.teeSet(slope = 113, rating = 72.0)
        val lastYearRounds = (1..3).map { i ->
            TestDataFactory.roundWithDetails(roundId = i, teeSet = tee, scorePerHole = 5, date = dateInYear(currentYear - 1, i))
        }
        val thisYearRounds = (4..6).map { i ->
            TestDataFactory.roundWithDetails(roundId = i, teeSet = tee, scorePerHole = 5, date = dateInYear(currentYear, i - 3))
        }
        val series = HandicapCalculator.buildHandicapTimeSeries(lastYearRounds + thisYearRounds)
        val years = series.map { it.year }.distinct()
        assertTrue("Should contain last year", (currentYear - 1) in years)
        assertTrue("Should contain current year", currentYear in years)
    }
}
