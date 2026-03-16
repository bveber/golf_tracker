package com.golftracker.util

import com.golftracker.data.entity.HoleStat
import com.golftracker.data.entity.Shot
import com.golftracker.data.entity.Putt
import com.golftracker.data.model.ApproachLie
import com.golftracker.data.model.ShotOutcome
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import android.content.Context
import io.mockk.every
import java.io.ByteArrayInputStream

class StrokesGainedBugInvestigation {

    private val mockContext = mockk<Context>(relaxed = true)
    
    private val baselineCsv = """
,DISTANCE,TEE,FAIRWAY,ROUGH,SAND,RECOVERY,,,,,TEE,APPROACH,SHORT,PUTT
,10,,2.20,2.49,2.41,,,,,5,3.31,4.29,0.97,1.42
,20,,2.40,2.59,2.53,,,,,10,3.61,6.11,1.33,2.77
,50,,2.65,2.85,2.98,,,,,15,4.86,8.54,4.46,4.46
,80,,2.75,2.96,3.24,,,,,20,5.75,11.26,4.48,4.48
,85,,2.77,2.97,3.24,,,,,,,,,
,100,2.92,2.80,3.02,3.23,3.80,,3,1.04,,,,,
,130,2.97,2.88,3.11,3.21,3.78,,20,1.87,,,,,
,200,3.12,3.19,3.42,3.55,3.87,,10,1.61,,,,,
,270,3.55,3.63,3.78,3.96,4.07,,20,1.87,,,,,
,400,3.99,4.11,4.30,4.69,4.75,,,,,,,,
""".trimIndent()

    init {
        val inputStream = ByteArrayInputStream(baselineCsv.toByteArray())
        every { mockContext.resources.openRawResource(any()) } returns inputStream
    }

    private val calculator = StrokesGainedCalculator(mockContext)

    @Test
    fun testUserScenario_270yDrive_86yApproach() {
        // Hole: 400y Par 4.
        // Shot 1: Tee, 400y. Drive 270y, Miss Right, ends in Rough.
        // Shot 2: From Rough (130y remaining). Hit 86y. Ends 20ft on Green.
        // Total shots to green: 2. Putts: 2. Score: 4.
        
        val stat = HoleStat(
            roundId = 1, holeId = 1, score = 4, putts = 2,
            adjustedYardage = 400
        )
        
        val shots = listOf(
            Shot(id = 1, holeStatId = 1, shotNumber = 1, distanceToPin = 400, distanceTraveled = 270, lie = ApproachLie.TEE, outcome = ShotOutcome.MISS_RIGHT),
            Shot(id = 2, holeStatId = 1, shotNumber = 2, distanceToPin = 130, distanceTraveled = 86, lie = ApproachLie.ROUGH, outcome = ShotOutcome.ON_TARGET)
        )
        
        val putts = listOf(
            Putt(id = 1, holeStatId = 1, puttNumber = 1, distance = 20f, made = false),
            Putt(id = 2, holeStatId = 1, puttNumber = 2, distance = 3f, made = true)
        )
        
        val breakdown = calculator.calculateHoleSg(
            par = 4, holeYardage = 400, shots = shots, putts = putts, penalties = 0, stat = stat, holeAdjustment = 0.0
        )
        
        println("Drive SG: ${breakdown.offTee}")
        println("Approach SG: ${breakdown.approach}")
        println("Around Green SG: ${breakdown.aroundGreen}")
        println("Putting SG: ${breakdown.putting}")
        println("Total SG: ${breakdown.total}")
        println("Shot Details: ${breakdown.shotSgs}")

        // Expected Drive SG: 3.99 - 1.0 - Expected(130y Rough) = 3.99 - 1.0 - 3.11 = -0.12
        // If current code results in -2.81, this test will reveal it.
    }

    @Test
    fun testUserScenario_315yFairway() {
        // Hole: 400y Par 4.
        // Shot 1: Tee, 400y. Drive 315y, On Target, in Fairway.
        // Shot 2: From Fairway (85y remaining) to hole in 2.
        
        val stat = HoleStat(
            roundId = 1, holeId = 1, score = 3, putts = 1,
            adjustedYardage = 400
        )
        
        val shots = listOf(
            Shot(id = 1, holeStatId = 1, shotNumber = 1, distanceToPin = 400, distanceTraveled = 315, lie = ApproachLie.TEE, outcome = ShotOutcome.ON_TARGET),
            Shot(id = 2, holeStatId = 1, shotNumber = 2, distanceToPin = 85, distanceTraveled = 85, lie = ApproachLie.FAIRWAY, outcome = ShotOutcome.HOLED_OUT)
        )
        
        val putts = listOf(
            Putt(id = 1, holeStatId = 1, puttNumber = 1, distance = 5f, made = true)
        )
        
        val breakdown = calculator.calculateHoleSg(
            par = 4, holeYardage = 400, shots = shots, putts = putts, penalties = 0, stat = stat, holeAdjustment = 0.0
        )
        
        println("315y Fairway Drive SG: ${breakdown.offTee}")
        
        // Expected: 3.99 - 2.77 - 1.0 = 0.22
    }

    @Test
    fun testRecoveryShot_LateralChipOut() {
        // Hole: 400y Par 4.
        // Shot 1: Tee, 400y. Drive 250y to the trees (OTHER). 150y left to pin.
        // Shot 2: Recovery (OTHER), 150y to pin. Chipped out 20y laterally to FAIRWAY. 140y left.
        
        val stat = HoleStat(
            roundId = 1, holeId = 1, score = 4, putts = 1,
            adjustedYardage = 400
        )
        
        val shots = listOf(
            Shot(id = 1, holeStatId = 1, shotNumber = 1, distanceToPin = 400, distanceTraveled = 250, lie = ApproachLie.TEE, outcome = ShotOutcome.MISS_RIGHT),
            Shot(id = 2, holeStatId = 1, shotNumber = 2, distanceToPin = 150, distanceTraveled = 20, lie = ApproachLie.OTHER, outcome = ShotOutcome.ON_TARGET),
            Shot(id = 3, holeStatId = 1, shotNumber = 3, distanceToPin = 140, distanceTraveled = 140, lie = ApproachLie.FAIRWAY, outcome = ShotOutcome.ON_TARGET)
        )
        
        val breakdown = calculator.calculateHoleSg(
            par = 4, holeYardage = 400, holeAdjustment = 0.0,
            shots = shots, putts = emptyList(), penalties = 0, stat = stat
        )
        
        println("Recovery Shot Details: ${breakdown.shotSgs}")
        // We want to see the SG for shot 2 (the recovery).
    }
}
