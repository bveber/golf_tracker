package com.golftracker.util

import com.golftracker.data.model.RoundWithDetails
import java.util.Date
import kotlin.math.floor
import kotlin.math.roundToInt

object HandicapCalculator {

    data class Differential(
        val roundId: Int,
        val date: Date,
        val value: Double,
        val totalHoles: Int = 18
    )

    /**
     * Calculates the handicap index based on a list of finalized rounds.
     * This uses a simplified implementation of the World Handicap System (WHS).
     * Future enhancements might include Net Double Bogey maximums and Playing Conditions Calculations (PCC).
     * 
     * @param rounds The list of rounds with their details.
     * @return The calculated handicap index, or null if there are fewer than 3 rounds.
     */
    fun calculateHandicapIndex(rounds: List<RoundWithDetails>): Double? {
        val differentials = calculateDifferentials(rounds)
        val count = differentials.size
        if (count < 3) return null

        val sortedDiffs = differentials.sortedBy { it.value } // Ascending

        // WHS Table 5.2a ( Simplified / Standard )
        // Number of differentials to use based on number of available differentials
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
        
        // Adjustments for small number of rounds (simplified WHS)
        val adjustment = when (count) {
            3 -> -2.0
            4 -> -1.0
            6 -> -1.0 // Simple soft adjustment for early stages.
            else -> 0.0
        }

        val bestDiffs = sortedDiffs.take(numToUse)
        val avg = bestDiffs.map { it.value }.average()
        
        val index = avg + adjustment
        
        // Truncate to 1 decimal place (standard WHS behavior is truncation, not rounding)
        return floor(index * 10) / 10.0
    }

    /**
     * Calculates WHS score differentials for a collection of rounds.
     * Uses the formula: (113 / Slope Rating) * (Gross Score - Course Rating).
     * 
     * @param rounds The most recent rounds played.
     * @return A list of score differentials.
     */
    fun calculateDifferentials(rounds: List<RoundWithDetails>): List<Differential> {
        // Sort by date descending (newest first)
        val sortedRounds = rounds.sortedByDescending { it.round.date }
        
        // Only consider up to last 20 rounds for calculation context, 
        // but we might need more if we supported pairing 9-hole rounds.
        // For MVP, just iterate all, or last 20 valid ones.
        // WHS uses last 20 *scores* (differentials).
        
        val diffs = mutableListOf<Differential>()
        
        for (roundDetails in sortedRounds) {
            if (diffs.size >= 20) break
            
            val round = roundDetails.round
            val teeSet = roundDetails.teeSet
            
            // Basic validation
            if (teeSet == null || teeSet.slope == 0 || teeSet.rating == 0.0) continue
            
            // Allow 9 and 18 hole rounds
            if (round.totalHoles == 18 || round.totalHoles == 9) {
                // Determine Gross Score
                var grossScore = 0
                roundDetails.holeStats.forEach { 
                    // Simplified Net Double Bogey: Cap score at Par + 5 for handicap purposes
                    val cappedScore = it.holeStat.score.coerceAtMost(it.hole.par + 5)
                    grossScore += cappedScore 
                }
                
                if (grossScore > 0) {
                    val rating = if (round.totalHoles == 9) teeSet.rating / 2.0 else teeSet.rating
                    val rawDiff = (113.0 / teeSet.slope.toDouble()) * (grossScore - rating)
                    
                    // Normalize for 18 holes if it's a 9-hole round
                    val differential = if (round.totalHoles == 9) rawDiff * 2.0 else rawDiff
                    
                    // Standard rounding to tenths
                    val roundedDiff = (differential * 10.0).roundToInt() / 10.0
                    
                    diffs.add(Differential(round.id, round.date, roundedDiff, round.totalHoles))
                }
            }
        }
        
        return diffs
    }
}
