package com.golftracker.util

import com.golftracker.data.model.ShotOutcome

object ShotDistanceCalculator {

    /**
     * Estimates the straight-line distance a shot traveled using geometry.
     * We know the starting distance to the pin (b), the ending distance to the pin (c),
     * and we can estimate the miss angle at the starting position (A).
     * 
     * Using the Law of Cosines: c^2 = a^2 + b^2 - 2ab * cos(A)
     * Solving for the distance traveled (a) using the quadratic formula:
     * a = b * cos(A) +/- sqrt( c^2 - b^2 * sin^2(A) )
     * Since we want the forward distance, we typically take the + solution.
     */
    fun estimateShotDistance(startDist: Int, endDist: Int, outcome: ShotOutcome?): Int {
        if (endDist == 0) return startDist
        
        // Convert to double for math
        val b = startDist.toDouble()
        val c = endDist.toDouble()
        
        // Estimate the miss angle (A) at the starting position based on the outcome
        val angleDegrees = when (outcome) {
            ShotOutcome.MISS_LEFT, ShotOutcome.MISS_RIGHT -> 10.0 // 10 degrees offline
            ShotOutcome.SHORT -> 0.0 // Straight but short
            ShotOutcome.LONG -> 0.0 // Straight but long
            ShotOutcome.ON_TARGET -> {
                // If it's on target but not holed out, assume it's slightly offline or short/long
                if (endDist < 10) 3.0 else 0.0 
            }
            null -> 0.0
        }
        
        val angleRadians = Math.toRadians(angleDegrees)
        
        // Handle straight shots (angle = 0)
        if (angleDegrees == 0.0) {
            // Is it short or long?
            return if (outcome == ShotOutcome.LONG) {
                (b + c).toInt()
            } else {
                kotlin.math.abs(b - c).toInt()
            }
        }
        
        val discriminant = (c * c) - (b * b * kotlin.math.sin(angleRadians) * kotlin.math.sin(angleRadians))
        
        // If discriminant is negative, the given 'c' is impossible for angle 'A' (the shot couldn't possibly end up that far away).
        // In this case, default to the straight-line subtraction approximation to prevent NaN or crashes.
        if (discriminant < 0) {
            return kotlin.math.abs(b - c).toInt()
        }
        
        // Quadratic formula solution 
        // a = b * cos(A) +/- sqrt( discriminant )
        val term1 = b * kotlin.math.cos(angleRadians)
        val term2 = kotlin.math.sqrt(discriminant)
        
        // We usually hit it forward, so we want the larger positive root. 
        // If the shot was incredibly short, term1 - term2 might be the right answer, but for golf we usually take +
        val a1 = term1 + term2
        val a2 = term1 - term2
        
        // If the outcome is SHORT, and both are positive, we probably want the smaller one if it makes sense,
        // but since we usually take + for forward progress towards the hole:
        val distanceTraveled = if (a1 > 0) a1 else a2
        
        return distanceTraveled.toInt()
    }
}
