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
        // Start 100, end 5 on target, IS last shot -> 100
        assertEquals(100, ShotDistanceCalculator.estimateShotDistance(100, 5, ShotOutcome.ON_TARGET, isLastShot = true))
        // Start 200, end 50 on target, NOT last shot -> 150
        assertEquals(150, ShotDistanceCalculator.estimateShotDistance(200, 50, ShotOutcome.ON_TARGET, isLastShot = false))
        // Start 200, end 5 on target, NOT last shot -> 195 (it was a chip/layup, not a finishing shot)
        assertEquals(195, ShotDistanceCalculator.estimateShotDistance(200, 5, ShotOutcome.ON_TARGET, isLastShot = false))
        
        // Start 100, end 0 -> 100
        assertEquals(100, ShotDistanceCalculator.estimateShotDistance(100, 0, ShotOutcome.ON_TARGET))
    }

    @Test
    fun testEstimateShotDistance_MissLeftRight() {
        // Start 100, miss left, end 20, IS last shot -> 100
        assertEquals(100, ShotDistanceCalculator.estimateShotDistance(100, 20, ShotOutcome.MISS_LEFT, isLastShot = true))
        // Start 200, miss right, end 100, NOT last shot -> 100
        assertEquals(100, ShotDistanceCalculator.estimateShotDistance(200, 100, ShotOutcome.MISS_RIGHT, isLastShot = false))
        // Start 200, miss right, end 10, NOT last shot -> 190 (pin high heuristic only applies to finishing shots)
        assertEquals(190, ShotDistanceCalculator.estimateShotDistance(200, 10, ShotOutcome.MISS_RIGHT, isLastShot = false))
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
    fun testEstimateShotDistance_ZeroAndExtreme() {
        // Tap-in 0 yard shot
        assertEquals(10, ShotDistanceCalculator.estimateShotDistance(10, 0, ShotOutcome.ON_TARGET))
        // Extreme 500yd shot
        assertEquals(500, ShotDistanceCalculator.estimateShotDistance(500, 0, ShotOutcome.ON_TARGET))
    }

    @Test
    fun testDeriveEndDistance_Duff() {
        // Start 100, hit it 5 yards (duff) -> 95 left
        assertEquals(95, ShotDistanceCalculator.deriveEndDistance(100, 5, ShotOutcome.ON_TARGET))
    }

    @Test
    fun testDeriveEndDistance_MissMathVeracity() {
        // Verify that the code actually calculates the hypotenuse for a 10-degree miss.
        // If start=100 and dist=100, end should be ~17.43 -> rounded to 17
        val endDist = ShotDistanceCalculator.deriveEndDistance(100, 100, ShotOutcome.MISS_LEFT)
        assertEquals(17, endDist)
        
        // At 200 yards, a 10 degree miss is double the gap -> ~34.86 -> 34
        val endDist200 = ShotDistanceCalculator.deriveEndDistance(200, 200, ShotOutcome.MISS_RIGHT)
        assertEquals(34, endDist200)
    }
}
