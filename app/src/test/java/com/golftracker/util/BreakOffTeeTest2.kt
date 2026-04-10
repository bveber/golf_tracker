package com.golftracker.util
import org.junit.Test
import kotlin.math.abs

class BreakOffTeeTest2 {
    @Test
    fun debug() {
        val raw1 = -2.0
        val raw3 = -0.05
        val adj = 0.30

        val sg1 = raw1 + adj
        val sg3 = raw3 + adj
        val offTee = raw1 + raw3 + adj + adj

        println("Calculated SG1: " + sg1) // -1.7
        println("Calculated SG3: " + sg3) // 0.25
        println("Calculated Off Tee: " + offTee) // -1.45

        // Let's print exactly how StrokesGainedCalculator does it:
        var offTeeVariable = 0.0
        offTeeVariable += -1.0 // raw1
        offTeeVariable += raw3 // raw3
        offTeeVariable -= 1.0 // penalty
        println("Raw Off Tee Variable: " + offTeeVariable) // -2.05

        var adjustedOffTee = offTeeVariable
        // loop runs twice (user revert):
        adjustedOffTee += adj
        adjustedOffTee += adj
        println("Adjusted Off Tee Variable: " + adjustedOffTee) // -1.45
    }
}
