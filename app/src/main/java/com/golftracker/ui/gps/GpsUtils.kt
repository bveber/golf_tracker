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
}
