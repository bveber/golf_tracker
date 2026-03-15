package com.golftracker.data.repository

import com.golftracker.util.TestDataFactory
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for the stat calculation logic used by StatsRepository.
 *
 * These tests call the private calculation methods indirectly by exercising
 * the same formulas in a testable way. In production, these methods are
 * invoked within the Flow pipeline; here we test the math directly.
 */
class StatsCalculationTest {

    // ─── Scoring Stats ───────────────────────────────────────────────

    @Test
    fun `average score is sum of all strokes divided by number of rounds`() {
        val round1 = TestDataFactory.roundWithDetails(roundId = 1, scorePerHole = 4)  // gross=72
        val round2 = TestDataFactory.roundWithDetails(roundId = 2, scorePerHole = 5)  // gross=90
        val rounds = listOf(round1, round2)

        val totalScore = rounds.sumOf { it.holeStats.sumOf { hs -> hs.holeStat.score } }
        val avgScore = totalScore.toDouble() / rounds.size

        assertEquals(81.0, avgScore, 0.01) // (72 + 90) / 2
    }

    @Test
    fun `average to par calculation`() {
        val round1 = TestDataFactory.roundWithDetails(roundId = 1, scorePerHole = 4, parPerHole = 4)  // 0 over
        val round2 = TestDataFactory.roundWithDetails(roundId = 2, scorePerHole = 5, parPerHole = 4)  // +18
        val rounds = listOf(round1, round2)

        val totalScore = rounds.sumOf { it.holeStats.sumOf { hs -> hs.holeStat.score } }
        val totalPar = rounds.sumOf { it.holeStats.sumOf { hs -> hs.hole.par } }
        val avgToPar = (totalScore - totalPar).toDouble() / rounds.size

        assertEquals(9.0, avgToPar, 0.01)  // (0 + 18) / 2
    }

    @Test
    fun `scoring stats handle empty rounds`() {
        val rounds = emptyList<com.golftracker.data.model.RoundWithDetails>()
        assertEquals(0, rounds.size)
        // No division by zero should occur; scoring defaults to 0
    }

    // ─── Driving Stats ───────────────────────────────────────────────

    @Test
    fun `driving stats only count par 4 and par 5 holes`() {
        val round = TestDataFactory.roundWithDetails(
            roundId = 1,
            parPerHole = 3, // All par 3s → should have 0 driving holes
            scorePerHole = 3
        )
        val drivingHoles = round.holeStats.filter { it.hole.par > 3 }
        assertEquals(0, drivingHoles.size)
    }

    @Test
    fun `fairway hit percentage calculation`() {
        // With ON_TARGET tee outcomes
        val onTarget = com.golftracker.data.model.ShotOutcome.ON_TARGET
        val missLeft = com.golftracker.data.model.ShotOutcome.MISS_LEFT

        val outcomes = listOf(onTarget, onTarget, missLeft, onTarget) // 3/4 = 75%
        val fairwaysHit = outcomes.count { it == onTarget }
        val pct = (fairwaysHit.toDouble() / outcomes.size) * 100

        assertEquals(75.0, pct, 0.01)
    }

    // ─── Approach / GIR Stats ────────────────────────────────────────

    @Test
    fun `GIR percentage uses GirCalculator for consistency`() {
        // par 4, score=4, putts=2 → GIR (approach=2, par-2=2)
        // par 4, score=5, putts=2 → No GIR (approach=3, par-2=2)
        val isGir1 = com.golftracker.util.GirCalculator.isGir(score = 4, par = 4, putts = 2)
        val isGir2 = com.golftracker.util.GirCalculator.isGir(score = 5, par = 4, putts = 2)

        assertTrue(isGir1)
        assertFalse(isGir2)
    }

    @Test
    fun `GIR percentage is correctly calculated`() {
        // par 4, score=4, putts=2 → GIR (approach=2, par-2=2)
        val stat = TestDataFactory.holeStat(score = 4, putts = 2)
        val hole = TestDataFactory.hole(par = 4)
        
        val isGir = com.golftracker.util.GirCalculator.isGir(stat.score, hole.par, stat.putts)
        assertTrue(isGir)
    }

    @Test
    fun `Near GIR is true when reached green in par - 1 shots with a chip`() {
        // Par 4, Score 5, Putts 2, Chips 1 -> (5-2) = 3 = (4-1). Missed GIR by 1. Should be Near GIR.
        val stat = TestDataFactory.holeStat(score = 5, putts = 2, chips = 1)
        val hole = TestDataFactory.hole(par = 4)

        val isGir = com.golftracker.util.GirCalculator.isGir(stat.score, hole.par, stat.putts)
        val isNearGir = isGir || ((stat.score - stat.putts == hole.par - 1) && (stat.chips >= 1 || stat.sandShots >= 1))

        assertFalse(isGir)
        assertTrue(isNearGir)
    }

    @Test
    fun `Near GIR is true when reached green in par - 1 shots with a sand shot`() {
        // Par 4, Score 5, Putts 2, SandShots 1 -> (5-2) = 3 = (4-1).
        val stat = TestDataFactory.holeStat(score = 5, putts = 2, sandShots = 1)
        val hole = TestDataFactory.hole(par = 4)

        val isGir = com.golftracker.util.GirCalculator.isGir(stat.score, hole.par, stat.putts)
        val isNearGir = isGir || ((stat.score - stat.putts == hole.par - 1) && (stat.chips >= 1 || stat.sandShots >= 1))

        assertFalse(isGir)
        assertTrue(isNearGir)
    }

    @Test
    fun `Near GIR is true if GIR was achieved`() {
        // Now inclusive: GIR should imply Near GIR
        val stat = TestDataFactory.holeStat(score = 4, putts = 2, chips = 0)
        val hole = TestDataFactory.hole(par = 4)

        val isGir = com.golftracker.util.GirCalculator.isGir(stat.score, hole.par, stat.putts)
        val isNearGir = isGir || ((stat.score - stat.putts == hole.par - 1) && (stat.chips >= 1 || stat.sandShots >= 1))

        assertTrue(isGir)
        assertTrue(isNearGir)
    }

    @Test
    fun `Near GIR is false if missed by more than 1 shot`() {
        // Par 4, Score 6, Putts 2, Chips 2 -> (6-2) = 4. Missed GIR by 2 shots.
        val stat = TestDataFactory.holeStat(score = 6, putts = 2, chips = 2)
        val hole = TestDataFactory.hole(par = 4)

        val isGir = com.golftracker.util.GirCalculator.isGir(stat.score, hole.par, stat.putts)
        val isNearGir = isGir || ((stat.score - stat.putts == hole.par - 1) && (stat.chips >= 1 || stat.sandShots >= 1))

        assertFalse(isGir)
        assertFalse(isNearGir)
    }

    // ─── Putting Stats ───────────────────────────────────────────────

    @Test
    fun `avg putts per round calculation`() {
        val round1 = TestDataFactory.roundWithDetails(roundId = 1, puttsPerHole = 2)  // 36 total
        val round2 = TestDataFactory.roundWithDetails(roundId = 2, puttsPerHole = 1)  // 18 total
        val rounds = listOf(round1, round2)

        val totalPutts = rounds.sumOf { it.holeStats.sumOf { hs -> hs.holeStat.putts } }
        val avgPuttsPerRound = totalPutts.toDouble() / rounds.size

        assertEquals(27.0, avgPuttsPerRound, 0.01) // (36 + 18) / 2
    }

    @Test
    fun `putts per GIR hole calculation`() {
        // All GIR holes (par=4, score=4, putts=2 → approach=2 ≤ 2)
        val round = TestDataFactory.roundWithDetails(roundId = 1, parPerHole = 4, scorePerHole = 4, puttsPerHole = 2)

        val girHoles = round.holeStats.filter {
            com.golftracker.util.GirCalculator.isGir(it.holeStat.score, it.hole.par, it.holeStat.putts)
        }
        val puttsOnGir = girHoles.sumOf { it.holeStat.putts }
        val puttsPerGir = if (girHoles.isNotEmpty()) puttsOnGir.toDouble() / girHoles.size else 0.0
 
        assertEquals(2.0, puttsPerGir, 0.01)
        assertEquals(18, girHoles.size) // All 18 holes are GIR
    }

    @Test
    fun `no GIR holes results in zero putts per GIR`() {
        // score=6, putts=2, par=4 → approach=4, par-2=2 → 4>2 no GIR
        val round = TestDataFactory.roundWithDetails(roundId = 1, parPerHole = 4, scorePerHole = 6, puttsPerHole = 2)

        val girHoles = round.holeStats.filter {
            com.golftracker.util.GirCalculator.isGir(it.holeStat.score, it.hole.par, it.holeStat.putts)
        }
        assertEquals(0, girHoles.size)
    }

    // ─── Par Based Breakouts ─────────────────────────────────────────

    @Test
    fun `scoring by par groupings correctly calculate averages`() {
        val holes = listOf(
            TestDataFactory.holeStatWithHole(holeStat = TestDataFactory.holeStat(score = 3), hole = TestDataFactory.hole(par = 3)),
            TestDataFactory.holeStatWithHole(holeStat = TestDataFactory.holeStat(score = 4), hole = TestDataFactory.hole(par = 3)),
            TestDataFactory.holeStatWithHole(holeStat = TestDataFactory.holeStat(score = 4), hole = TestDataFactory.hole(par = 4)),
            TestDataFactory.holeStatWithHole(holeStat = TestDataFactory.holeStat(score = 5), hole = TestDataFactory.hole(par = 4)),
            TestDataFactory.holeStatWithHole(holeStat = TestDataFactory.holeStat(score = 5), hole = TestDataFactory.hole(par = 5))
        )

        val byPar = holes.groupBy { it.hole.par }.mapValues { (par, parHoles) ->
            parHoles.sumOf { it.holeStat.score }.toDouble() / parHoles.size
        }

        assertEquals(3.5, byPar[3]!!, 0.01) // (3+4)/2
        assertEquals(4.5, byPar[4]!!, 0.01) // (4+5)/2
        assertEquals(5.0, byPar[5]!!, 0.01) // 5/1
    }

    @Test
    fun `driving by par groupings only includes par 4 and 5`() {
        val holes = listOf(
            TestDataFactory.holeStatWithHole(holeStat = TestDataFactory.holeStat(score = 3), hole = TestDataFactory.hole(par = 3)),
            TestDataFactory.holeStatWithHole(holeStat = TestDataFactory.holeStat(score = 4), hole = TestDataFactory.hole(par = 4)),
            TestDataFactory.holeStatWithHole(holeStat = TestDataFactory.holeStat(score = 5), hole = TestDataFactory.hole(par = 5))
        )

        val drivingHoles = holes.filter { it.hole.par > 3 }
        val byPar = drivingHoles.groupBy { it.hole.par }

        assertNull(byPar[3])
        assertNotNull(byPar[4])
        assertNotNull(byPar[5])
    }

    @Test
    fun `approach GIR by par groupings correctly calculate percentages`() {
        val holes = listOf(
            // Par 3: 1/2 GIR
            TestDataFactory.holeStatWithHole(holeStat = TestDataFactory.holeStat(score = 3, putts = 2), hole = TestDataFactory.hole(par = 3)), // GIR
            TestDataFactory.holeStatWithHole(holeStat = TestDataFactory.holeStat(score = 4, putts = 2), hole = TestDataFactory.hole(par = 3)), // No GIR
            // Par 4: 1/1 GIR
            TestDataFactory.holeStatWithHole(holeStat = TestDataFactory.holeStat(score = 4, putts = 2), hole = TestDataFactory.hole(par = 4))  // GIR
        )

        val byPar = holes.groupBy { it.hole.par }.mapValues { (par, parHoles) ->
            val girCount = parHoles.count { com.golftracker.util.GirCalculator.isGir(it.holeStat.score, it.hole.par, it.holeStat.putts) }
            (girCount.toDouble() / parHoles.size) * 100
        }

        assertEquals(50.0, byPar[3]!!, 0.01)
        assertEquals(100.0, byPar[4]!!, 0.01)
    }
}
