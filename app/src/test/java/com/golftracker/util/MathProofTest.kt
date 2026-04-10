package com.golftracker.util
import org.junit.Test
import kotlin.math.abs

class MathProofTest {
    @Test
    fun testMath() {
        for (sg1 in -300..0) {
            val shot1 = sg1 / 100.0
            for (sg3 in -300..300) {
                val shot3 = sg3 / 100.0
                for (a in 0..100) {
                    val adj = a / 100.0
                    
                    val final1 = shot1 + adj
                    val final3 = shot3 + adj
                    val finalOffTee = shot1 + shot3 + adj + adj
                    
                    if (abs(final1 - -1.69) < 0.02 && abs(final3 - 0.26) < 0.02 && abs(finalOffTee - -1.69) < 0.02) {
                        println("MATCH FOUND: Raw1=$shot1, Raw3=$shot3, Adj=$adj")
                    }
                }
            }
        }
        println("Done testing.")
    }
}
