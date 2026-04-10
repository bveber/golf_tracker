package com.golftracker.util
import org.junit.Test
import kotlin.math.abs

class BreakOffTeeTest {
    @Test
    fun debug() {
        val shot1 = -1.7
        val shot3 = 0.25
        val offTee = -1.7
        
        // Let's assume raw1 is -2.0 (OB drive)
        val raw1 = -2.0
        val adjPerShot1 = shot1 - raw1 // 0.3
        
        val raw3 = shot3 - adjPerShot1 // 0.25 - 0.3 = -0.05
        
        // Summing them up exactly as calculator does
        val calculatedOffTee = raw1 + raw3 + adjPerShot1 + adjPerShot1
        println("Calculated offTee: " + calculatedOffTee)
        
        // Which is -2.0 + (-0.05) + 0.3 + 0.3 = -1.45
        // But the ui shows -1.70.
        // What else could make breakdown.offTee = -1.70?
        // IF calculatedOffTee is -1.45, then somehow offTee -= 0.25 OR offTee = -1.70 directly!
    }
}
