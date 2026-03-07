package com.golftracker.util

import com.golftracker.data.model.ShotOutcome

object ShotDistanceCalculator {

    /**
     * Estimates the straight-line distance a shot traveled using the Law of Cosines.
     * a^2 = b^2 + c^2 - 2bc * cos(A)
     * where 'a' is the distance traveled, 'b' is the starting distance to the pin,
     * 'c' is the ending distance to the pin, and 'A' is the angle of the miss.
     */
    fun estimateShotDistance(startDist: Int, endDist: Int, outcome: ShotOutcome?): Int {
        if (endDist == 0) return startDist
        
        // Convert to double for math
        val b = startDist.toDouble()
        val c = endDist.toDouble()
        
        // Estimate the angle based on the outcome
        val angleDegrees = when (outcome) {
            ShotOutcome.MISS_LEFT, ShotOutcome.MISS_RIGHT -> 15.0 // ~15 degrees off center
            ShotOutcome.SHORT -> 0.0 // Straight but short
            ShotOutcome.LONG -> 180.0 // Over the green, effectively a straight line past it
            ShotOutcome.ON_TARGET -> {
                // If it's on target but not holed out, assume it's slightly offline or short/long
                if (endDist < 10) 5.0 else 0.0 
            }
            null -> 0.0
        }
        
        // Handle straight shots (angle = 0)
        if (angleDegrees == 0.0) {
            return kotlin.math.abs(startDist - endDist)
        }
        // Handle long shots (angle = 180) 
        if (angleDegrees == 180.0) {
            return startDist + endDist
        }

        val angleRadians = Math.toRadians(angleDegrees)
        val aSquared = (b * b) + (c * c) - (2 * b * c * kotlin.math.cos(angleRadians))
        
        return kotlin.math.sqrt(aSquared).toInt()
    }
}
