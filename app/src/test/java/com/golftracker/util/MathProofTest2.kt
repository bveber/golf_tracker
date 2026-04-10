package com.golftracker.util
import org.junit.Test

class MathProofTest2 {
    @Test
    fun testMath() {
        for (sg1 in -300..300) {
            val r1 = sg1 / 100.0
            for (sg3 in -300..300) {
                val r3 = sg3 / 100.0
                for (a1 in 0..100) {
                    val adj1 = a1 / 100.0
                    for (a3 in 0..100) {
                        val adj3 = a3 / 100.0
                        
                        val s1 = r1 + adj1
                        val s3 = r3 + adj3
                        val ot = r1 + r3 + adj1 + adj3
                        
                        if (Math.abs(s1 - -1.7) < 0.02 && Math.abs(s3 - 0.25) < 0.02 && Math.abs(ot - -1.7) < 0.02) {
                            println("MATCH: r1=${r1}, r3=${r3}, adj1=${adj1}, adj3=${adj3}")
                        }
                    }
                }
            }
        }
    }
}
