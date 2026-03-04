package com.golftracker.util

object GirCalculator {
    /**
     * Calculates if a Green in Regulation (GIR) was achieved.
     * GIR is achieved if the ball is on the green in par - 2 strokes.
     * This means the player has 2 putts to make par.
     * 
     * Formula: Score - Putts <= Par - 2
     * 
     * @param score Total strokes on the hole
     * @param par The par for the hole
     * @param putts Number of putts taken
     * @return true if GIR was achieved, false otherwise
     */
    fun isGir(score: Int, par: Int, putts: Int): Boolean {
        if (score == 0) return false // Scores of 0 (not played) count as no GIR
        // If it's a hole-in-one on a Par 3, score=1, putts=0. 1-0 <= 3-2 (1<=1) -> True.
        // If it's a hole-in-one on a Par 4 (Albatross), score=1, putts=0. 1-0 <= 4-2 (1<=2) -> True.
        return (score - putts) <= (par - 2)
    }
}
