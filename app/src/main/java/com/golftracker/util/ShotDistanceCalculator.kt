package com.golftracker.util

import com.golftracker.data.model.ShotOutcome

object ShotDistanceCalculator {

    /**
     * Estimates the straight-line distance a shot traveled based on start distance, end distance, and outcome.
     * Simplifies the geometry to match user intuition: missing slightly left/right or on target near the green
     * suggests they hit the ball the intended distance. 
     */
    fun estimateShotDistance(startDist: Int, endDist: Int, outcome: ShotOutcome?): Int {
        if (endDist == 0) return startDist

        return when (outcome) {
            ShotOutcome.SHORT -> kotlin.math.max(0, startDist - endDist)
            ShotOutcome.LONG -> startDist + endDist
            ShotOutcome.ON_TARGET -> {
                // If it's on target and ends on or near the green (e.g., < 20 yards), 
                // assume the shot traveled the full distance and the remaining distance is just
                // the putt/chip distance. If they missed by a large margin (e.g. chunked it 50 yards short),
                // treat it like a SHORT shot.
                if (endDist <= 20) startDist else kotlin.math.max(0, startDist - endDist)
            }
            ShotOutcome.MISS_LEFT, ShotOutcome.MISS_RIGHT -> {
                // If they missed laterally, they likely still hit it the `startDist` distance forward.
                // However, if the end distance is massive (e.g., >= half the start distance), they
                // probably chunked/topped it badly, so fall back to simple subtraction.
                if (endDist >= startDist / 2) {
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
