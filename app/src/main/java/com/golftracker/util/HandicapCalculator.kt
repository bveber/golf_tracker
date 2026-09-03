package com.golftracker.util

import com.golftracker.data.entity.Round
import com.golftracker.data.entity.TeeSet
import com.golftracker.data.model.RoundWithDetails
import java.util.Calendar
import java.util.Date
import kotlin.math.floor
import kotlin.math.roundToInt

object HandicapCalculator {

    /** Minimum holes that must actually be scored for a round to produce a differential. */
    private const val MIN_HOLES_FOR_DIFFERENTIAL = 9
    private const val FULL_ROUND_HOLES = 18

    /**
     * A single scored differential, including metadata for display.
     */
    data class Differential(
        val roundId: Int,
        val date: Date,
        val value: Double,
        /** Number of holes actually scored and used to build this differential. */
        val totalHoles: Int = 18,
        /** True if the un-played remainder of the round was estimated with an expected-score
         *  component derived from a trailing handicap. False when the naive scaling
         *  fallback was used (no prior handicap available), or when the round was fully played. */
        val usedExpectedScore: Boolean = false
    )

    /**
     * A point in the handicap history timeline — used to drive the trend chart.
     */
    data class HandicapPoint(
        val date: Date,
        val handicapIndex: Double,
        val year: Int
    )

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Returns the current handicap index (for the current year, or most recent year
     * that has enough rounds) or null if there are fewer than 3 rounds.
     */
    fun calculateHandicapIndex(rounds: List<RoundWithDetails>): Double? {
        val year = currentYear()
        return calculateHandicapIndexForYear(rounds, year)
            ?: calculateHandicapIndexForYear(rounds, year - 1) // fall back to prior year
    }

    /**
     * Calculate the handicap index for a specific calendar year.
     */
    fun calculateHandicapIndexForYear(rounds: List<RoundWithDetails>, year: Int): Double? {
        val yearRounds = rounds.filter { yearOf(it.round.date) == year }
        val differentials = calculateDifferentials(rounds, year)
        return applyWHSFormula(differentials)
    }

    /**
     * Calculates WHS differentials for rounds in [targetYear].
     * Rounds where fewer than 18 (but at least [MIN_HOLES_FOR_DIFFERENTIAL]) holes were
     * scored — whether a genuine 9-hole round or a partial round abandoned partway through —
     * are normalised to 18 holes using a *trailing handicap* (the WHS index calculated from
     * all rounds played **before** that round in the same year) to estimate the un-played
     * remainder. When no trailing handicap is available (first few rounds of the year) a
     * naive proportional-scaling fallback is used instead. Rounds with fewer than
     * [MIN_HOLES_FOR_DIFFERENTIAL] holes scored do not produce a differential at all.
     *
     * At most 20 differentials are returned (WHS rule).
     */
    fun calculateDifferentials(
        rounds: List<RoundWithDetails>,
        targetYear: Int = currentYear()
    ): List<Differential> {
        // All finalized rounds in the target year, oldest first so we can build a
        // running "trailing handicap" as we process them.
        val yearRounds = rounds
            .filter { yearOf(it.round.date) == targetYear }
            .sortedBy { it.round.date }

        // All finalized rounds from previous years – used to seed the first
        // trailing handicap at the start of targetYear.
        val priorRounds = rounds.filter { yearOf(it.round.date) < targetYear }
        var trailingHandicap: Double? = applyWHSFormula(buildDifferentialList(priorRounds))

        val diffs = mutableListOf<Differential>()

        for (roundDetails in yearRounds) {
            val round = roundDetails.round
            val teeSet = roundDetails.teeSet

            if (teeSet.slope == 0 || teeSet.rating == 0.0) continue
            val holesPlayedCount = holesPlayed(roundDetails)
            if (holesPlayedCount < MIN_HOLES_FOR_DIFFERENTIAL) continue

            val grossScore = cappedGrossScore(roundDetails)
            if (grossScore <= 0) continue

            val diff = buildDifferential(round, teeSet, grossScore, holesPlayedCount, trailingHandicap)

            diffs.add(diff)

            // Update trailing handicap for the next round in this year
            trailingHandicap = applyWHSFormula(diffs)
        }

        // Return the most recent 20 in descending order
        return diffs.sortedByDescending { it.date }.take(20)
    }

    /**
     * Produces a time-series of handicap indices — one entry per round —
     * for use in the trend chart.  Returns data for all years present in [rounds].
     */
    fun buildHandicapTimeSeries(rounds: List<RoundWithDetails>): List<HandicapPoint> {
        val years = rounds.map { yearOf(it.round.date) }.distinct().sorted()
        val points = mutableListOf<HandicapPoint>()

        var cumulativeRounds = rounds.toMutableList()

        for (year in years) {
            val yearRounds = rounds
                .filter { yearOf(it.round.date) == year }
                .sortedBy { it.round.date }

            // Prior rounds used for trailing handicap seed
            val priorRounds = rounds.filter { yearOf(it.round.date) < year }
            var trailingHandicap: Double? = applyWHSFormula(buildDifferentialList(priorRounds))

            val runningDiffs = mutableListOf<Differential>()

            for (roundDetails in yearRounds) {
                val round = roundDetails.round
                val teeSet = roundDetails.teeSet

                if (teeSet.slope == 0 || teeSet.rating == 0.0) continue
                val holesPlayedCount = holesPlayed(roundDetails)
                if (holesPlayedCount < MIN_HOLES_FOR_DIFFERENTIAL) continue
                val grossScore = cappedGrossScore(roundDetails)
                if (grossScore <= 0) continue

                val diff = buildDifferential(round, teeSet, grossScore, holesPlayedCount, trailingHandicap)

                runningDiffs.add(diff)
                val hcp = applyWHSFormula(runningDiffs)
                trailingHandicap = hcp

                if (hcp != null) {
                    points.add(HandicapPoint(round.date, hcp, year))
                }
            }
        }

        return points
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    /** Applies the WHS best-differential averaging and adjustment formula. */
    private fun applyWHSFormula(differentials: List<Differential>): Double? {
        val count = differentials.size
        if (count < 3) return null

        val sorted = differentials.sortedBy { it.value }

        val numToUse = when (count) {
            3, 4, 5 -> 1
            6, 7, 8 -> 2
            9, 10, 11 -> 3
            12, 13, 14 -> 4
            15, 16 -> 5
            17, 18 -> 6
            19 -> 7
            else -> 8
        }

        val adjustment = when (count) {
            3 -> -2.0
            4 -> -1.0
            6 -> -1.0
            else -> 0.0
        }

        val avg = sorted.take(numToUse).map { it.value }.average()
        return floor((avg + adjustment) * 10) / 10.0
    }

    /**
     * Build a simple differential list for [rounds] without any year filtering or
     * expected-score logic — used when seeding the trailing handicap from prior-year data.
     */
    private fun buildDifferentialList(rounds: List<RoundWithDetails>): List<Differential> {
        val sorted = rounds.sortedByDescending { it.round.date }
        val diffs = mutableListOf<Differential>()
        for (rd in sorted) {
            if (diffs.size >= 20) break
            val round = rd.round
            val teeSet = rd.teeSet
            if (teeSet.slope == 0 || teeSet.rating == 0.0) continue
            val holesPlayedCount = holesPlayed(rd)
            if (holesPlayedCount < MIN_HOLES_FOR_DIFFERENTIAL) continue
            val grossScore = cappedGrossScore(rd)
            if (grossScore <= 0) continue
            // No trailing handicap is tracked here — this is only used to seed the
            // trailing handicap for a later, ordered pass — so partial rounds always
            // use the naive proportional-scaling fallback.
            val diff = buildDifferential(round, teeSet, grossScore, holesPlayedCount, trailingHandicap = null)
            diffs.add(diff)
        }
        return diffs
    }

    /** Score capping per simplified Net Double Bogey: max per hole = par + 5 */
    private fun cappedGrossScore(rd: RoundWithDetails): Int {
        var total = 0
        rd.holeStats.forEach { stat ->
            if (stat.holeStat.score > 0) {
                total += stat.holeStat.score.coerceAtMost(stat.hole.par + 5)
            }
        }
        return total
    }

    /** Number of holes with a recorded score (i.e. actually played). */
    private fun holesPlayed(rd: RoundWithDetails): Int =
        rd.holeStats.count { it.holeStat.score > 0 }

    /**
     * Builds an 18-hole-equivalent [Differential] from [holesPlayedCount] actually-scored
     * holes. When fewer than 18 holes were played (a genuine 9-hole round, or a round
     * abandoned partway through with at least [MIN_HOLES_FOR_DIFFERENTIAL] holes scored),
     * the un-played remainder is estimated from [trailingHandicap] — the golfer's WHS index
     * going into the round — scaled to the fraction of the round left to play. With no
     * trailing handicap available, the scored portion's differential is scaled up
     * proportionally instead (equivalent to the classic "double the 9-hole score" rule).
     */
    private fun buildDifferential(
        round: Round,
        teeSet: TeeSet,
        grossScore: Int,
        holesPlayedCount: Int,
        trailingHandicap: Double?
    ): Differential {
        if (holesPlayedCount >= FULL_ROUND_HOLES) {
            val rawDiff = (113.0 / teeSet.slope) * (grossScore - teeSet.rating)
            return Differential(round.id, round.date, roundTenths(rawDiff), FULL_ROUND_HOLES)
        }

        val playedFraction = holesPlayedCount / FULL_ROUND_HOLES.toDouble()
        val ratingForHolesPlayed = teeSet.rating * playedFraction
        val rawDiffPlayed = (113.0 / teeSet.slope) * (grossScore - ratingForHolesPlayed)

        val (fullDiff, usedExpected) = if (trailingHandicap != null) {
            // Expected differential contribution for the un-played holes, proportional
            // to how much of the round remains.
            val remainingFraction = 1.0 - playedFraction
            val expectedRemaining = trailingHandicap * remainingFraction
            Pair(roundTenths(rawDiffPlayed + expectedRemaining), true)
        } else {
            // Fallback: scale the played portion's differential up to a full round
            // (equivalent to doubling for an exact 9-hole round).
            Pair(roundTenths(rawDiffPlayed / playedFraction), false)
        }
        return Differential(round.id, round.date, fullDiff, holesPlayedCount, usedExpected)
    }

    private fun roundTenths(value: Double): Double =
        (value * 10.0).roundToInt() / 10.0

    private fun currentYear(): Int = Calendar.getInstance().get(Calendar.YEAR)

    private fun yearOf(date: Date): Int =
        Calendar.getInstance().also { it.time = date }.get(Calendar.YEAR)
}
