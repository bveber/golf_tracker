package com.golftracker.ui.gps

import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [GpsUtils] distance and midpoint calculations.
 *
 * These tests validate the pure-Kotlin Haversine implementation against
 * known geographic distances so the utility can be relied on throughout
 * the GPS rangefinder and shot-tracking features.
 */
class GpsUtilsTest {

    // --- calculateDistanceYards ---

    @Test
    fun `same point returns zero yards`() {
        val point = LatLng(33.4484, -112.0740) // Phoenix, AZ
        assertEquals(0, GpsUtils.calculateDistanceYards(point, point))
    }

    @Test
    fun `known short distance returns expected yards`() {
        // Two points ~100 yards apart (verified via Google Maps)
        val tee = LatLng(33.44840, -112.07400)
        // ~91 meters ≈ 100 yards due north
        val flag = LatLng(33.44922, -112.07400)
        val distance = GpsUtils.calculateDistanceYards(tee, flag)
        // Allow ±5 yard tolerance for floating-point rounding
        assertTrue(
            "Expected ~100 yds but got $distance",
            distance in 95..105
        )
    }

    @Test
    fun `typical par-4 distance is reasonable`() {
        // ~400 yard hole: points separated by ~366 meters
        val tee = LatLng(33.44840, -112.07400)
        val green = LatLng(33.45174, -112.07400)
        val distance = GpsUtils.calculateDistanceYards(tee, green)
        assertTrue(
            "Expected ~400 yds but got $distance",
            distance in 380..420
        )
    }

    @Test
    fun `distance is symmetric`() {
        val a = LatLng(33.44840, -112.07400)
        val b = LatLng(33.44922, -112.07300)
        assertEquals(
            GpsUtils.calculateDistanceYards(a, b),
            GpsUtils.calculateDistanceYards(b, a)
        )
    }

    // --- midpoint ---

    @Test
    fun `midpoint of identical points returns same point`() {
        val point = LatLng(33.4484, -112.0740)
        val mid = GpsUtils.midpoint(point, point)
        assertEquals(point.latitude, mid.latitude, 1e-10)
        assertEquals(point.longitude, mid.longitude, 1e-10)
    }

    @Test
    fun `midpoint is between two points`() {
        val a = LatLng(33.0, -112.0)
        val b = LatLng(34.0, -111.0)
        val mid = GpsUtils.midpoint(a, b)
        assertEquals(33.5, mid.latitude, 1e-10)
        assertEquals(-111.5, mid.longitude, 1e-10)
    }

    @Test
    fun `midpoint latitude is average of inputs`() {
        val a = LatLng(10.0, 20.0)
        val b = LatLng(30.0, 40.0)
        val mid = GpsUtils.midpoint(a, b)
        assertEquals(20.0, mid.latitude, 1e-10)
        assertEquals(30.0, mid.longitude, 1e-10)
    }

    // --- calculateDispersionOffsets ---

    @Test
    fun `perfect shot returns all zero offsets`() {
        val start = LatLng(0.0, 0.0)
        val target = LatLng(0.01, 0.0) // ~1200 yards North
        val actual = LatLng(0.01, 0.0)
        val offsets = GpsUtils.calculateDispersionOffsets(start, target, actual)
        assertEquals(0, offsets.left)
        assertEquals(0, offsets.right)
        assertEquals(0, offsets.short)
        assertEquals(0, offsets.long)
    }

    @Test
    fun `short left miss returns correct offsets`() {
        val start = LatLng(0.0, 0.0)
        val target = LatLng(0.001, 0.0) // ~120 yards North
        // Move slightly South (Short) and West (Left)
        val actual = LatLng(0.0009, -0.00005)
        val offsets = GpsUtils.calculateDispersionOffsets(start, target, actual)
        
        // At the equator, 0.0001 degrees latitude is ~11 meters (~12 yards)
        // 0.00005 degrees longitude is ~5.5 meters (~6 yards)
        assertTrue("Expected short to be > 0, got ${offsets.short}", (offsets.short ?: 0) > 0)
        assertTrue("Expected left to be > 0, got ${offsets.left}", (offsets.left ?: 0) > 0)
        assertEquals(0, offsets.right)
        assertEquals(0, offsets.long)
    }

    @Test
    fun `cross-equator distance is correct`() {
        val north = LatLng(1.0, 0.0)
        val south = LatLng(-1.0, 0.0)
        // 2 degrees latitude = ~222,222 meters ≈ 243,025 yards
        val distance = GpsUtils.calculateDistanceYards(north, south)
        assertEquals(243000.0, distance.toDouble(), 500.0) // Huge distance, allow reasonable delta
    }

    @Test
    fun `antimeridian crossing distance check`() {
        val west = LatLng(0.0, 179.9)
        val east = LatLng(0.0, -179.9)
        // Gap of 0.2 degrees at equator ≈ 22,222 meters ≈ 24,300 yards
        val distance = GpsUtils.calculateDistanceYards(west, east)
        assertTrue("Distance should be short across antimeridian", distance < 30000)
    }

    @Test
    fun `tiny distance precision check`() {
        // Move 1 meter (approx 0.000009 degrees lat)
        val p1 = LatLng(33.0, -112.0)
        val p2 = LatLng(33.000009, -112.0)
        val distance = GpsUtils.calculateDistanceYards(p1, p2)
        // 1 meter ≈ 1.09 yards
        assertTrue("Expected approx 1 yard, got $distance", distance in 0..2)
    }

    // --- calculateDispersionOffsets (Rotated Shots) ---

    @Test
    fun `rotated shot calculates correct relative offsets`() {
        // Start 0,0. Target 0, 0.001 (Due East, ~110 yards)
        val start = LatLng(0.0, 0.0)
        val target = LatLng(0.0, 0.001)
        
        // Missed "Long" would be further East
        // Missed "Right" would be further South
        val actual = LatLng(-0.00005, 0.0011) // South and slightly more East
        val offsets = GpsUtils.calculateDispersionOffsets(start, target, actual)
        
        assertTrue("Expected long offset, got ${offsets.long}", (offsets.long ?: 0) > 0)
        assertTrue("Expected right offset, got ${offsets.right}", (offsets.right ?: 0) > 0)
        assertEquals(0, offsets.left)
        assertEquals(0, offsets.short)
    }
}
