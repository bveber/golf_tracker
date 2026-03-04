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
}
