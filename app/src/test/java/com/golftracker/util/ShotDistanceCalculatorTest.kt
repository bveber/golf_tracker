package com.golftracker.util

import com.golftracker.data.model.ShotOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

class ShotDistanceCalculatorTest {

    @Test
    fun testEstimateShotDistance_Short() {
        // Start 100, end 20 short -> 80
        assertEquals(80, ShotDistanceCalculator.estimateShotDistance(100, 20, ShotOutcome.SHORT))
        // Start 50, end 60 short (impossible, but should handle -> 0)
        assertEquals(0, ShotDistanceCalculator.estimateShotDistance(50, 60, ShotOutcome.SHORT))
    }

    @Test
    fun testEstimateShotDistance_Long() {
        // Start 100, end 20 long -> 120
        assertEquals(120, ShotDistanceCalculator.estimateShotDistance(100, 20, ShotOutcome.LONG))
    }

    @Test
    fun testEstimateShotDistance_OnTarget() {
        // Start 100, end 5 on target -> 100 (user expectation: "if on target and ends on green, should result in 100")
        assertEquals(100, ShotDistanceCalculator.estimateShotDistance(100, 5, ShotOutcome.ON_TARGET))
        // Start 100, end 0 -> 100
        assertEquals(100, ShotDistanceCalculator.estimateShotDistance(100, 0, ShotOutcome.ON_TARGET))
        // Start 100, end 25 -> 75 (if they were 25 yds out, they probably chunked it)
        assertEquals(75, ShotDistanceCalculator.estimateShotDistance(100, 25, ShotOutcome.ON_TARGET))
    }

    @Test
    fun testEstimateShotDistance_MissLeftRight() {
        // Start 100, miss left, end 20 -> assume they hit it pin high, distance = 100
        assertEquals(100, ShotDistanceCalculator.estimateShotDistance(100, 20, ShotOutcome.MISS_LEFT))
        assertEquals(100, ShotDistanceCalculator.estimateShotDistance(100, 20, ShotOutcome.MISS_RIGHT))
        
        // Start 200, miss right, end 100 -> they hit it wildly short, assume they advanced it somewhat
        assertEquals(100, ShotDistanceCalculator.estimateShotDistance(200, 100, ShotOutcome.MISS_RIGHT))
    }
    
    @Test
    fun testEstimateShotDistance_NullOutcome() {
        // Null outcome falls back to simple subtraction
        assertEquals(80, ShotDistanceCalculator.estimateShotDistance(100, 20, null))
    }

    @Test
    fun testDeriveEndDistance_Basic() {
        // Start 100, hit it 80 short -> 20 left
        assertEquals(20, ShotDistanceCalculator.deriveEndDistance(100, 80, ShotOutcome.SHORT))
        // Start 100, hit it 120 long -> 20 left
        assertEquals(20, ShotDistanceCalculator.deriveEndDistance(100, 120, ShotOutcome.LONG))
        // Start 100, hit it 100 on target -> 0 left
        assertEquals(0, ShotDistanceCalculator.deriveEndDistance(100, 100, ShotOutcome.ON_TARGET))
    }

    @Test
    fun testDeriveEndDistance_Miss() {
        // Start 100, hit it 100 but missed 10 deg -> should be ~17.4 yds
        // sqrt(100^2 + 100^2 - 2*100*100*cos(10deg)) = sqrt(20000 - 20000*0.9848) = sqrt(304) = 17.4
        val endDist = ShotDistanceCalculator.deriveEndDistance(100, 100, ShotOutcome.MISS_LEFT)
        assertEquals(17, endDist)
    }
}
