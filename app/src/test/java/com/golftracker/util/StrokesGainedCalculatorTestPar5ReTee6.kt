package com.golftracker.util

import android.content.Context
import com.golftracker.data.model.ApproachLie
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream

class StrokesGainedCalculatorTestPar5ReTee6 {
    private lateinit var context: Context
    private lateinit var calculator: StrokesGainedCalculator

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        val csvData = """
        Header,Distance,Tee,Fairway,Rough,Sand,Recovery,Other,GreenDist,GreenPutts,Other,Other,Other,Other,Other
        ,200,3.12,3.19,3.42,3.55,3.87,,8,1.50,,,,,
        ,220,3.17,3.32,3.53,3.70,3.92,,9,1.56,,,,,
        ,280,3.65,3.69,3.83,4.00,4.10,,20,1.87,,,,,
        ,300,3.72,3.76,3.90,4.07,4.17,,25,1.94,,,,,
        ,320,3.78,3.82,3.96,4.14,4.24,,,,,,,,
        ,500,4.41,4.53,4.72,5.11,5.17,,,,,,,,
        ,520,4.54,4.85,4.85,5.24,5.30,,,,,,,,
        """.trimIndent()
        val inputStream = ByteArrayInputStream(csvData.toByteArray())
        every { context.resources.openRawResource(any()) } returns inputStream
        calculator = StrokesGainedCalculator(context)
    }

    @Test
    fun debugUserRevert() {
        val stat = com.golftracker.data.entity.HoleStat(
            id = 1, roundId = 1, holeId = 1, 
            score = 6,
            putts = 1,
            isScored = true,
            teeOutcome = com.golftracker.data.model.ShotOutcome.MISS_LEFT
        )
        val penalty = com.golftracker.data.entity.Penalty(id = 1, holeStatId = 1, type = com.golftracker.data.model.PenaltyType.OB, strokes = 1, shotNumber = 1)
        val shot1 = com.golftracker.data.entity.Shot(holeStatId = 1, shotNumber = 1, distanceToPin = 516, lie = ApproachLie.TEE)
        val shot3 = com.golftracker.data.entity.Shot(holeStatId = 1, shotNumber = 3, distanceToPin = 516, lie = ApproachLie.TEE)
        val shot4 = com.golftracker.data.entity.Shot(holeStatId = 1, shotNumber = 4, distanceToPin = 216, lie = ApproachLie.FAIRWAY)
        val putts = listOf(com.golftracker.data.entity.Putt(holeStatId = 1, puttNumber = 5, distance = 10f, made = true))

        val bd1 = calculator.calculateHoleSg(
            par = 5, holeYardage = 516,
            shots = listOf(shot1, shot3, shot4), putts = putts, penalties = listOf(penalty), stat = stat,
            totalRoundAdjustment = 5.58, numHoles = 18
        )
        
        println("=== REAL USER SCENARIO ===")
        println("Shot 1: " + bd1.shotSgs.find { it.first == 1 }?.second)
        println("Shot 3: " + bd1.shotSgs.find { it.first == 3 }?.second)
        println("Off Tee Aggregate: " + bd1.offTee)
        
        // Output mathematical expected value
        val shot1Expected = bd1.shotSgs.find { it.first == 1 }?.second ?: 0.0
        val shot3Expected = bd1.shotSgs.find { it.first == 3 }?.second ?: 0.0
        println("EXPECTED OFF TEE: " + (shot1Expected + shot3Expected))
    }
}
