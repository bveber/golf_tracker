package com.golftracker.ui.gps

import com.google.android.gms.maps.model.LatLng
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Shared GPS utility functions for distance and coordinate calculations.
 *
 * All distance methods use the Haversine formula with pure Kotlin math
 * (no Android [android.location.Location] dependency), making them
 * suitable for both production and unit-test use.
 */
object GpsUtils {

    /** Earth's mean radius in meters. */
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    /** Conversion factor from meters to yards. */
    private const val METERS_TO_YARDS = 1.09361

    /**
     * Calculates the great-circle distance between two [LatLng] points
     * using the Haversine formula and returns the result in **yards**.
     *
     * @param start the starting coordinate.
     * @param end   the ending coordinate.
     * @return distance in whole yards (rounded down).
     */
    fun calculateDistanceYards(start: LatLng, end: LatLng): Int {
        val lat1 = Math.toRadians(start.latitude)
        val lat2 = Math.toRadians(end.latitude)
        val deltaLat = Math.toRadians(end.latitude - start.latitude)
        val deltaLon = Math.toRadians(end.longitude - start.longitude)

        val a = sin(deltaLat / 2) * sin(deltaLat / 2) +
                cos(lat1) * cos(lat2) *
                sin(deltaLon / 2) * sin(deltaLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        val distanceMeters = EARTH_RADIUS_METERS * c

        return (distanceMeters * METERS_TO_YARDS).toInt()
    }

    /**
     * Returns the geographic midpoint between two [LatLng] coordinates.
     *
     * This is a simple arithmetic mean of latitudes and longitudes,
     * which is accurate enough for short distances (< 1 km) typical
     * on a golf course.
     *
     * @param a first coordinate.
     * @param b second coordinate.
     * @return the midpoint as a [LatLng].
     */
    fun midpoint(a: LatLng, b: LatLng): LatLng = LatLng(
        (a.latitude + b.latitude) / 2,
        (a.longitude + b.longitude) / 2
    )

    /**
     * Calculates a destination [LatLng] given a starting point, a distance in yards,
     * and a bearing in degrees.
     *
     * @param start The starting coordinate.
     * @param distanceYards The distance to travel in yards.
     * @param bearingDegrees The heading in degrees (0 = North, 90 = East, etc.)
     * @return The resulting [LatLng] coordinate.
     */
    fun computeOffset(start: LatLng, distanceYards: Double, bearingDegrees: Double): LatLng {
        val distanceRadians = (distanceYards / METERS_TO_YARDS) / EARTH_RADIUS_METERS
        val bearingRadians = Math.toRadians(bearingDegrees)
        
        val lat1 = Math.toRadians(start.latitude)
        val lon1 = Math.toRadians(start.longitude)
        
        val lat2 = kotlin.math.asin(sin(lat1) * cos(distanceRadians) + cos(lat1) * sin(distanceRadians) * cos(bearingRadians))
        val lon2 = lon1 + atan2(sin(bearingRadians) * sin(distanceRadians) * cos(lat1), cos(distanceRadians) - sin(lat1) * sin(lat2))
        
        return LatLng(Math.toDegrees(lat2), Math.toDegrees(lon2))
    }
        
    /**
     * Calculates the initial bearing (heading) from [start] to [end] in degrees.
     *
     * @param start the starting coordinate.
     * @param end   the ending coordinate.
     * @return bearing in degrees (0 to 360).
     */
    fun calculateBearing(start: LatLng, end: LatLng): Double {
        val lat1 = Math.toRadians(start.latitude)
        val lat2 = Math.toRadians(end.latitude)
        val deltaLon = Math.toRadians(end.longitude - start.longitude)
        
        val y = sin(deltaLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)
        val bearingRadians = atan2(y, x)
        
        return (Math.toDegrees(bearingRadians) + 360) % 360
    }
    
    /**
     * Generates a list of [LatLng] coordinates representing an ellipse (polygon) for the dispersion overlay.
     * 
     * @param targetCenter The flag/target location.
     * @param xOffsetYards The average left/right miss offset from the center (Right is positive, Left is negative).
     * @param yOffsetYards The average short/long miss offset from the center (Long is positive, Short is negative).
     * @param radiusXYards The radius (1-sigma or 2-sigma) of the ellipse width.
     * @param radiusYYards The radius (1-sigma or 2-sigma) of the ellipse depth.
     * @param bearingDegrees The heading from the player to the target.
     * @param correlation A value from -1.0 to 1.0 to skew the ellipse (e.g. modelling that left misses go long). 
     *                    0.0 means the ellipse is perfectly axis-aligned with the bearing.
     * @param points The number of vertices to generate for the polygon (higher = smoother curve).
     */
    fun createDispersionPolygon(
        targetCenter: LatLng,
        xOffsetYards: Double,
        yOffsetYards: Double,
        radiusXYards: Double,
        radiusYYards: Double,
        bearingDegrees: Double,
        correlation: Double = 0.0,
        points: Int = 36
    ): List<LatLng> {
        val polygon = mutableListOf<LatLng>()
        val bearingRad = Math.toRadians(bearingDegrees)
        
        // Ensure correlation is bounded
        val r = correlation.coerceIn(-0.99, 0.99)
        
        // 1. Calculate the real center of the dispersion by applying the mean offsets
        // X offset (right/left) is perpendicular to bearing (bearing + 90 deg)
        // Y offset (long/short) is along the bearing
        val lat1 = Math.toRadians(targetCenter.latitude)
        val lon1 = Math.toRadians(targetCenter.longitude)
        
        // Convert yard offsets to radians
        val yOffsetRad = (yOffsetYards / METERS_TO_YARDS) / EARTH_RADIUS_METERS
        val xOffsetRad = (xOffsetYards / METERS_TO_YARDS) / EARTH_RADIUS_METERS
        
        // Center offset Y (Depth/Long-Short)
        val lat2 = kotlin.math.asin(sin(lat1) * cos(yOffsetRad) + cos(lat1) * sin(yOffsetRad) * cos(bearingRad))
        val lon2 = lon1 + atan2(sin(bearingRad) * sin(yOffsetRad) * cos(lat1), cos(yOffsetRad) - sin(lat1) * sin(lat2))
        
        // Center offset X (Width/Right-Left) - apply 90 degree clockwise rotation to bearing for "Right"
        val perpBearingRad = bearingRad + (Math.PI / 2.0)
        val finalCenterLat = kotlin.math.asin(sin(lat2) * cos(xOffsetRad) + cos(lat2) * sin(xOffsetRad) * cos(perpBearingRad))
        val finalCenterLon = lon2 + atan2(sin(perpBearingRad) * sin(xOffsetRad) * cos(lat2), cos(xOffsetRad) - sin(lat2) * sin(finalCenterLat))

        // Center LatLng of the ellipse
        val centerLatRad = finalCenterLat
        val centerLonRad = finalCenterLon

        // 2. Generate points around the ellipse
        val step = (2.0 * Math.PI) / points
        for (i in 0 until points) {
            val t = i * step
            
            // Standard parametric equation for an ellipse (in yards):
            val unrotatedX = radiusXYards * cos(t)
            var unrotatedY = radiusYYards * sin(t)
            
            // Apply correlation (skew) to the Y axis based on X displacement
            // This tilts the ellipse. A negative correlation means positive X (Right miss) results in negative Y (Short).
            unrotatedY += unrotatedX * r * (radiusYYards / radiusXYards)

            // Calculate distance and angle from the local center of the ellipse for this vertex
            val distYards = sqrt(unrotatedX * unrotatedX + unrotatedY * unrotatedY)
            val angleRad = atan2(unrotatedX, unrotatedY) // atan2(X, Y) where Y is straightforward depth
            
            val distRad = (distYards / METERS_TO_YARDS) / EARTH_RADIUS_METERS
            
            // Global bearing to this vertex (target bearing + local offset angle)
            val vertexBearing = bearingRad + angleRad
            
            // Haversine translation from ellipse center to vertex
            val vertexLat = kotlin.math.asin(sin(centerLatRad) * cos(distRad) + cos(centerLatRad) * sin(distRad) * cos(vertexBearing))
            val vertexLon = centerLonRad + atan2(sin(vertexBearing) * sin(distRad) * cos(centerLatRad), cos(distRad) - sin(centerLatRad) * sin(vertexLat))
            
            polygon.add(LatLng(Math.toDegrees(vertexLat), Math.toDegrees(vertexLon)))
        }
        
        return polygon
    }

    /**
     * Data class to hold calculated dispersion yardage offsets.
     */
    data class DispersionOffsets(
        val left: Int? = null,
        val right: Int? = null,
        val short: Int? = null,
        val long: Int? = null
    )

    /**
     * Calculates the dispersion offsets (Left/Right, Short/Long) in yards.
     * 
     * @param start The location where the shot was played from.
     * @param target The intended destination (flag/target).
     * @param actual The location where the ball actually came to rest.
     * @return [DispersionOffsets] in yards.
     */
    fun calculateDispersionOffsets(start: LatLng, target: LatLng, actual: LatLng): DispersionOffsets {
        val bearingTarget = calculateBearing(start, target)
        val bearingActual = calculateBearing(start, actual)
        
        val totalDistActual = calculateDistanceYards(start, actual).toDouble()
        val totalDistTarget = calculateDistanceYards(start, target).toDouble()
        
        if (totalDistTarget == 0.0) return DispersionOffsets()

        // Angle between target line and actual line, normalized to [-180, 180]
        val angleDiffDeg = (bearingActual - bearingTarget + 540) % 360 - 180
        val angleDiffRad = Math.toRadians(angleDiffDeg)
        
        // Project actual distance onto target line (Longitudinal/Depth)
        val projectedDist = totalDistActual * kotlin.math.cos(angleDiffRad)
        val depthError = projectedDist - totalDistTarget
        
        // Lateral error (Left/Right)
        val lateralError = totalDistActual * kotlin.math.sin(angleDiffRad)
        
        return DispersionOffsets(
            left = if (lateralError < -0.5) kotlin.math.abs(lateralError).toInt() else 0,
            right = if (lateralError > 0.5) lateralError.toInt() else 0,
            short = if (depthError < -0.5) kotlin.math.abs(depthError).toInt() else 0,
            long = if (depthError > 0.5) depthError.toInt() else 0
        )
    }

    /**
     * Estimates a ShotOutcome based on dispersion offsets.
     */
    fun estimateOutcome(offsets: DispersionOffsets): com.golftracker.data.model.ShotOutcome {
        val lateralThreshold = 10 // Yards to be considered "off-target" laterally
        val depthThreshold = 15    // Yards to be considered "short/long"
        
        val left = offsets.left ?: 0
        val right = offsets.right ?: 0
        val short = offsets.short ?: 0
        val long = offsets.long ?: 0

        return when {
            left > lateralThreshold -> com.golftracker.data.model.ShotOutcome.MISS_LEFT
            right > lateralThreshold -> com.golftracker.data.model.ShotOutcome.MISS_RIGHT
            short > depthThreshold -> com.golftracker.data.model.ShotOutcome.SHORT
            long > depthThreshold -> com.golftracker.data.model.ShotOutcome.LONG
            else -> com.golftracker.data.model.ShotOutcome.ON_TARGET
        }
    }
}
