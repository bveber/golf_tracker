package com.golftracker.util

import com.golftracker.data.model.ShotOutcome

object ShotDistanceCalculator {

    /**
     * Estimates the straight-line distance a shot traveled based on start distance, end distance, and outcome.
     * 
     * @param isLastShot True if this is the final approach shot (ends on the green/near the pin).
     */
    fun estimateShotDistance(startDist: Int, endDist: Int, outcome: ShotOutcome?, isLastShot: Boolean = true): Int {
        if (endDist == 0) return startDist

        return when (outcome) {
            ShotOutcome.SHORT -> kotlin.math.max(0, startDist - endDist)
            ShotOutcome.LONG -> startDist + endDist
            ShotOutcome.ON_TARGET -> {
                // Only assume the shot traveled exactly 'startDist' if it landed on the green (isLastShot)
                // and is within a reasonable "on target" radius. 
                // If there's another approach shot coming, then this shot definitely didn't hit the pin.
                if (isLastShot && endDist <= 25) {
                    startDist 
                } else {
                    kotlin.math.max(0, startDist - endDist)
                }
            }
            ShotOutcome.MISS_LEFT, ShotOutcome.MISS_RIGHT -> {
                // Similar to ON_TARGET, only assume full distance if it's the finishing shot.
                if (isLastShot && endDist <= 25) {
                    startDist
                } else if (endDist >= startDist / 2) {
                    kotlin.math.max(0, startDist - endDist)
                } else {
                    startDist
                }
            }
            null -> kotlin.math.max(0, startDist - endDist)
        }
    }

    /**
     * Solves for the ending distance to pin given start distance, distance traveled, and outcome.
     * This is the inverse of estimateShotDistance.
     */
    fun deriveEndDistance(startDist: Int, distanceTraveled: Int, outcome: ShotOutcome?): Int {
        if (distanceTraveled <= 0) return startDist
        
        return when (outcome) {
            ShotOutcome.SHORT -> kotlin.math.max(0, startDist - distanceTraveled)
            ShotOutcome.LONG -> kotlin.math.abs(distanceTraveled - startDist)
            ShotOutcome.ON_TARGET -> {
                // If they hit it 'On Target', assume it's straight-ish.
                // If distanceTraveled > startDist, they went long.
                kotlin.math.abs(startDist - distanceTraveled)
            }
            ShotOutcome.MISS_LEFT, ShotOutcome.MISS_RIGHT -> {
                // Heuristic: If they missed laterally, the end distance is the hypotenuse
                // approx: endDist^2 = startDist^2 + distTraveled^2 - 2*start*dist*cos(10deg)
                val a = distanceTraveled.toDouble()
                val b = startDist.toDouble()
                val angleRad = Math.toRadians(10.0)
                val cSquared = (a * a) + (b * b) - (2 * a * b * kotlin.math.cos(angleRad))
                kotlin.math.sqrt(kotlin.math.max(0.0, cSquared)).toInt()
            }
            null -> kotlin.math.max(0, startDist - distanceTraveled)
        }
    }
}
