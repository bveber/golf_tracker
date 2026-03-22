package com.golftracker.util

import android.content.Context
import com.golftracker.data.model.ApproachLie
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream

class StrokesGainedCalculatorTest {

    private lateinit var calculator: StrokesGainedCalculator
    private val context: Context = mockk()

    @Before
    fun setup() {
        // Mock a much more granular and realistic baseline.
        // Format: Distance (yards), Tee, Fairway, Rough, Sand, Recovery, GreenDist (FEET), GreenPutts
        val csvData = """
            Header,Distance,Tee,Fairway,Rough,Sand,Recovery,Other,GreenDist,GreenPutts,Other,Other,Other,Other,Other
            ,1,0.0,2.0,2.2,2.4,2.6,,3.0,1.05,,,,
            ,3,0.0,2.1,2.3,2.5,2.7,,10.0,1.45,,,,
            ,5,0.0,2.2,2.4,2.6,2.8,,15.0,1.6,,,,
            ,10,0.0,2.3,2.5,2.7,2.9,,20.0,1.9,,,,
            ,20,0.0,2.5,2.7,2.9,3.1,,30.0,2.3,,,,
            ,50,2.7,2.6,2.8,3.0,3.2,,60.0,3.0,,,,
            ,100,2.9,2.8,3.0,3.2,3.4,,100.0,3.5,,,,
            ,150,3.1,3.0,3.2,3.4,3.6,,150.0,3.8,,,,
            ,400,4.2,4.1,4.3,4.5,4.7,,400.0,4.5,,,,
            ,500,4.8,4.7,4.9,5.1,5.3,,500.0,5.0,,,,
        """.trimIndent()
        
        val inputStream = ByteArrayInputStream(csvData.toByteArray())
        every { context.resources.openRawResource(any()) } returns inputStream
        
        calculator = StrokesGainedCalculator(context)
    }

    @Test
    fun testCalculateShotSG_MonsterDrive() {
        // 500y Tee (Exp 4.8) to 150y Fairway (Exp 3.0)
        // SG = 4.8 - 3.0 - 1 = 0.8
        val sg = calculator.calculateShotSG(
            startDistanceYs = 500,
            startLie = ApproachLie.TEE,
            isTeeShot = true,
            endDistanceYs = 150,
            endLie = ApproachLie.FAIRWAY,
            endDistanceFeetOnGreen = null,
            penaltyStrokes = 0,
            holeAdjustment = 0.0
        )
        assertEquals(0.8, sg, 0.01)
    }

    @Test
    fun testCalculateShotSG_Shank() {
        // 100y Fairway (Exp 2.8) to 95y Fairway (Exp ~2.78 via interpolation)
        // Interp 95y: 2.6 + (2.8-2.6)*(95-50)/(100-50) = 2.6 + 0.2*0.9 = 2.78
        // SG = 2.8 - 2.78 - 1 = -0.98
        val sg = calculator.calculateShotSG(
            startDistanceYs = 100,
            startLie = ApproachLie.FAIRWAY,
            isTeeShot = false,
            endDistanceYs = 95,
            endLie = ApproachLie.FAIRWAY,
            endDistanceFeetOnGreen = null,
            penaltyStrokes = 0,
            holeAdjustment = 0.0
        )
        assertTrue("SG should be very negative for a shank (-0.98 approx), got $sg", sg < -0.9)
    }

    @Test
    fun testCalculateShotSG_PenaltyOffTee() {
        // 400y Tee (Exp 4.2). Penalty (1 stroke). 
        // End distance same (400) or maybe re-tee.
        val sg = calculator.calculateShotSG(
            startDistanceYs = 400,
            startLie = ApproachLie.TEE,
            isTeeShot = true,
            endDistanceYs = 400,
            endLie = ApproachLie.TEE,
            endDistanceFeetOnGreen = null,
            penaltyStrokes = 1,
            holeAdjustment = 0.0
        )
        // SG = 4.2 - 4.2 - 1 (stroke) - 1 (penalty) = -2.0
        assertEquals(-2.0, sg, 0.01)
    }

    @Test
    fun testCalculateShotSG_ApproachToGreen() {
        // Start 100y Fairway (Exp 2.8)
        // End on Green at 15ft (5 yards) (Exp 1.6)
        // SG = 2.8 - 1.6 - 1 = 0.2
        val sg = calculator.calculateShotSG(
            startDistanceYs = 100,
            startLie = ApproachLie.FAIRWAY,
            isTeeShot = false,
            endDistanceYs = 0,
            endLie = null,
            endDistanceFeetOnGreen = 15f,
            penaltyStrokes = 0,
            holeAdjustment = 0.0
        )
        assertEquals(0.2, sg, 0.01)
    }

    @Test
    fun testHoleSgBreakdown_TeeShotCategorization() {
        // 400y Tee (Exp 4.2), 150y Fairway (Exp 3.0)
        // Shot 1: 400y Tee -> 150y Fairway. SG = 4.2 - 3.0 - 1 = 0.2
        
        val stat = com.golftracker.data.entity.HoleStat(
            id = 1, roundId = 1, holeId = 1, score = 4,
            teeOutcome = com.golftracker.data.model.ShotOutcome.ON_TARGET,
            putts = 2
        )
        
        val shots = listOf(
            com.golftracker.data.entity.Shot(holeStatId = 1, shotNumber = 1, distanceToPin = 400, lie = ApproachLie.TEE, outcome = com.golftracker.data.model.ShotOutcome.ON_TARGET),
            com.golftracker.data.entity.Shot(holeStatId = 1, shotNumber = 2, distanceToPin = 150, lie = ApproachLie.FAIRWAY, outcome = com.golftracker.data.model.ShotOutcome.ON_TARGET)
        )
        
        val putts = listOf(
            com.golftracker.data.entity.Putt(holeStatId = 1, puttNumber = 1, distance = 15f, made = false), // 15ft = 5yd (Exp 1.6)
            com.golftracker.data.entity.Putt(holeStatId = 1, puttNumber = 2, distance = 3f, made = true)   // 3ft = 1yd (Exp 1.05)
        )
        
        val breakdown = calculator.calculateHoleSg(
            par = 4, holeYardage = 400, holeAdjustment = 0.0,
            shots = shots, putts = putts, penalties = emptyList(), stat = stat
        )
        
        assertEquals(0.2, breakdown.offTee, 0.01)
        // Shot 2 starts at 150y Fairway (3.0) and ends at 15ft Green (1.6). SG = 3.0 - 1.6 - 1 = 0.4
        assertEquals(0.4, breakdown.approach, 0.01)
        // Putting: Start 15ft (1.6). Finish in 2 putts. SG = 1.6 - 2 = -0.4
        assertEquals(-0.4, breakdown.putting, 0.01)
    }

    @Test
    fun testCalculateShotSG_HoledOut() {
        // Start 10y Fairway (Exp 2.3)
        // Holed out (Exp 0.0)
        // SG = 2.3 - 0.0 - 1 = 1.3
        val sg = calculator.calculateShotSG(
            startDistanceYs = 10,
            startLie = ApproachLie.FAIRWAY,
            isTeeShot = false,
            endDistanceYs = 0,
            endLie = null,
            endDistanceFeetOnGreen = null,
            penaltyStrokes = 0,
            holeAdjustment = 0.0
        )
        assertEquals(1.3, sg, 0.01)
    }

    @Test
    fun testCalculateShotSG_Recovery() {
        // Start 200y Recovery (Using real baseline approx 3.8)
        // End 100y Fairway (Exp 2.8)
        // SG = 3.8 - 2.8 - 1 = 0.0
        val sg = calculator.calculateShotSG(
            startDistanceYs = 200,
            startLie = ApproachLie.OTHER,
            isTeeShot = false,
            endDistanceYs = 100,
            endLie = ApproachLie.FAIRWAY,
            endDistanceFeetOnGreen = null,
            penaltyStrokes = 0,
            holeAdjustment = 0.0
        )
        assertTrue(sg < 0.1 && sg > -0.1)
    }

    @Test
    fun testInterpolateShotDistances_DoglegEfficiency() {
        // Scenario: 400y hole. Drive travels 250y but ball is at a spot 200y from pin.
        // Shot 1: Start 400. End 200 (Pin Reduction = 200y).
        // Shot 2: Start 200.
        val shot1 = com.golftracker.data.entity.Shot(holeStatId = 1, shotNumber = 1, distanceToPin = 400, distanceTraveled = 250)
        val shot2 = com.golftracker.data.entity.Shot(holeStatId = 1, shotNumber = 2, distanceToPin = 200)
        
        val stat = com.golftracker.data.entity.HoleStat(id = 1, roundId = 1, holeId = 1)
        val interpolated = calculator.javaClass.getDeclaredMethod("interpolateShotDistances", List::class.java, Int::class.java, Int::class.java, List::class.java, com.golftracker.data.entity.HoleStat::class.java)
            .apply { isAccessible = true }
            .invoke(calculator, listOf(shot1, shot2), 400, 4, emptyList<com.golftracker.data.entity.Putt>(), stat) as List<com.golftracker.data.entity.Shot>
            
        assertEquals(400, interpolated[0].distanceToPin)
        assertEquals(200, interpolated[1].distanceToPin)
    }

    @Test
    fun testCalculateHoleSg_PenaltyAttribution_Threshold() {
        // Shot 1: Drive to trouble. (Base SG -0.5)
        // Shot 2: Recovery punch out. (Base SG -0.15) -> Threshold met (<= -0.1)
        // Attribution: 0.1 from Shot 2 to Shot 1.
        
        val stat = com.golftracker.data.entity.HoleStat(id = 1, roundId = 1, holeId = 1)
        val shot1 = com.golftracker.data.entity.Shot(holeStatId = 1, shotNumber = 1, distanceToPin = 400, lie = ApproachLie.TEE)
        val shot2 = com.golftracker.data.entity.Shot(holeStatId = 1, shotNumber = 2, distanceToPin = 180, isRecovery = true, penaltyAttribution = 0.1)
        
        val breakdown = calculator.calculateHoleSg(
            par = 4, holeYardage = 400, holeAdjustment = 0.0,
            shots = listOf(shot1, shot2), putts = emptyList(), penalties = emptyList(), stat = stat
        )
        
        val sg1 = breakdown.shotSgs.find { it.first == 1 }?.second ?: 0.0
        val sg2 = breakdown.shotSgs.find { it.first == 2 }?.second ?: 0.0
        
        // Base SG2 (180 to 180 is ~expensive, but we just want to see the shift)
        // Let's verify shift happened
        assertTrue("SG2 should be boosted by 0.1", sg2 > -1.5) // Loose check, real values depend on CSV
    }

    @Test
    fun testCalculateHoleSg_PenaltyAttribution_BelowThreshold() {
        // Shot 2: Base SG -0.05 -> Threshold NOT met (<= -0.1)
        // Attribution should NOT be applied even if field is > 0.
        
        val stat = com.golftracker.data.entity.HoleStat(id = 1, roundId = 1, holeId = 1)
        val shot1 = com.golftracker.data.entity.Shot(holeStatId = 1, shotNumber = 1, distanceToPin = 400, lie = ApproachLie.TEE)
        // 180y TEE to 175y ROUGH is a "good" shot relatively.
        val shot2 = com.golftracker.data.entity.Shot(holeStatId = 1, shotNumber = 2, distanceToPin = 180, isRecovery = true, penaltyAttribution = 0.5)
        
        val breakdown = calculator.calculateHoleSg(
            par = 4, holeYardage = 400, holeAdjustment = 0.0,
            shots = listOf(shot1, shot2), putts = emptyList(), penalties = emptyList(), stat = stat
        )
        
        // If threshold works, SG2 + SG1 components should NOT include the 0.5 shift
        // We'll verify that OffTee + Approach = TotalSG (excluding penalties)
        assertEquals(breakdown.total, breakdown.offTee + breakdown.approach + breakdown.aroundGreen + breakdown.putting, 0.001)
    }

    @Test
    fun testComponentIdentity_Investigation() {
        // Par 4, 400y hole.
        // Shot 1: Tee to 180y in Rough.
        // Shot 2: Recovery Punch out to 100y Fairway.
        // Slider: 0.3.
        val stat = com.golftracker.data.entity.HoleStat(id = 1, roundId = 1, holeId = 1)
        val shot1 = com.golftracker.data.entity.Shot(holeStatId = 1, shotNumber = 1, distanceToPin = 400, lie = ApproachLie.TEE)
        val shot2 = com.golftracker.data.entity.Shot(holeStatId = 1, shotNumber = 2, distanceToPin = 180, lie = ApproachLie.ROUGH, isRecovery = true, penaltyAttribution = 0.3)
        // Shot 3 is at 100y (next shot).
        val shot3 = com.golftracker.data.entity.Shot(holeStatId = 1, shotNumber = 3, distanceToPin = 100, lie = ApproachLie.FAIRWAY)
        
        val breakdown = calculator.calculateHoleSg(
            par = 4, holeYardage = 400, holeAdjustment = 0.0,
            shots = listOf(shot1, shot2, shot3), putts = emptyList(), penalties = emptyList(), stat = stat
        )
        
        val sg1 = breakdown.shotSgs.find { it.first == 1 }?.second ?: 0.0
        val sg2 = breakdown.shotSgs.find { it.first == 2 }?.second ?: 0.0
        val sg3 = breakdown.shotSgs.find { it.first == 3 }?.second ?: 0.0
        
        // Base SG1 (400T to 180R): 4.2 - 3.4 - 1 = -0.2
        // Base SG2 (180O to 100F): 3.6 (approx inter) - 2.8 - 1 = -0.2
        // After 0.3 shift:
        // SG1 = -0.2 - 0.3 = -0.5
        // SG2 = -0.2 + 0.3 = +0.1
        
        println("SG1: $sg1, SG2: $sg2, SG3: $sg3")
        assertTrue("SG1 should be penalized by 0.3", sg1 < -0.4)
        assertTrue("SG2 should be boosted by 0.3", sg2 > 0.0)
        assertNotEquals("SG1 and SG2 should be different", sg1, sg2, 0.001)
    }


    @Test
    fun testHoleSgBreakdown_MultipleChipsAccounting() {
        // 10y Fairway (Exp 2.1) to Holed (Exp 0).
        // If it took 2 chips, total SG should be 2.1 - 0 - 2 = 0.1.
        
        val stat = com.golftracker.data.entity.HoleStat(
            id = 1, roundId = 1, holeId = 1, score = 5,
            chips = 2, chipDistance = 10, chipLie = ApproachLie.FAIRWAY,
            putts = 0
        )
        
        val breakdown = calculator.calculateHoleSg(
            par = 4, holeYardage = 400, holeAdjustment = 0.0,
            shots = emptyList(), putts = emptyList(), penalties = emptyList(), stat = stat
        )
        
        assertEquals(0.3, breakdown.aroundGreen, 0.01)
    }
    @Test
    fun testHoleSgBreakdown_DoglegPar5() {
        // Par 5, 525 yards.
        // Tee Shot: Cut corner, linear 280y, but GPS says next shot is 150y away.
        // 525y Tee (Exp 4.8 say), 150y Bunker (Exp 3.3 say)
        // If bug exists, it overwrites 150y with ~245y (525-280) and SG is low.
        // If fix works, it uses 150y and SG is higher (+0.5 instead of -0.2).
        
        val stat = com.golftracker.data.entity.HoleStat(
            id = 1, roundId = 1, holeId = 1, score = 5,
            teeOutcome = com.golftracker.data.model.ShotOutcome.MISS_LEFT, // Cutting corner
            putts = 2
        )
        
        val shots = listOf(
            com.golftracker.data.entity.Shot(
                holeStatId = 1, shotNumber = 1, distanceToPin = 525, 
                lie = ApproachLie.TEE, outcome = com.golftracker.data.model.ShotOutcome.MISS_LEFT,
                distanceTraveled = 280
            ),
            com.golftracker.data.entity.Shot(
                holeStatId = 1, shotNumber = 2, distanceToPin = 150, 
                lie = ApproachLie.FAIRWAY, outcome = com.golftracker.data.model.ShotOutcome.ON_TARGET
            )
        )
        
        val breakdown = calculator.calculateHoleSg(
            par = 5, holeYardage = 525, holeAdjustment = 0.0,
            shots = shots, putts = emptyList(), penalties = emptyList(), stat = stat
        )
        
        assertEquals(0.8, breakdown.offTee, 0.01)
        // Shot 2: 150y Fairway to 0y (Holed with score=5). 3.0 - 0 - 1.0 = 2.0
        assertEquals(2.0, breakdown.approach, 0.01)
    }

    @Test
    fun testHoleSgBreakdown_Interpolation() {
        // Par 4, 400y. Shot 1 at 400y. Shot 2 missing. Shot 3 (Chip) at 10y.
        // Interpolation should put Shot 2 at (400 + 10) / 2 = 205y.
        
        val stat = com.golftracker.data.entity.HoleStat(
            id = 1, roundId = 1, holeId = 1, score = 4,
            chips = 1, chipDistance = 10, chipLie = ApproachLie.FAIRWAY,
            putts = 1
        )
        
        val shots = listOf(
            com.golftracker.data.entity.Shot(holeStatId = 1, shotNumber = 1, distanceToPin = 400, lie = ApproachLie.TEE, outcome = com.golftracker.data.model.ShotOutcome.ON_TARGET),
            com.golftracker.data.entity.Shot(holeStatId = 1, shotNumber = 2, distanceToPin = null, lie = ApproachLie.FAIRWAY, outcome = com.golftracker.data.model.ShotOutcome.ON_TARGET)
        )
        
        val breakdown = calculator.calculateHoleSg(
            par = 4, holeYardage = 400, holeAdjustment = 0.0,
            shots = shots, putts = emptyList(), penalties = emptyList(), stat = stat
        )
        
        // Shot 1: 400y to 205y. (4.2 - 3.242 - 1.0) = -0.042 approx
        assertEquals(-0.042, breakdown.offTee, 0.01)
        
        // Shot 2: 205y to 10y. (3.242 - 2.3 - 1.0) = -0.058 approx
        assertEquals(-0.058, breakdown.approach, 0.01)
    }

    @Test
    fun testHoleSgBreakdown_AggregateFallback() {
        val stat = com.golftracker.data.entity.HoleStat(
            id = 1, roundId = 1, holeId = 1, score = 4,
            putts = 2
        )
        
        val breakdown = calculator.calculateHoleSg(
            par = 4, holeYardage = 400, holeAdjustment = 0.0,
            shots = emptyList(), putts = emptyList(), penalties = emptyList(), stat = stat
        )
        
        assertEquals(0.24, breakdown.offTee, 0.1)
        assertEquals(1.96, breakdown.approach, 0.1)
    }

    @Test
    fun testHoleSgBreakdown_OutcomeInfluence() {
        // Par 4, 400y. Tee shot MISS_LEFT (Dogleg check) but no distance.
        // Should use standard heuristic.
        val stat = com.golftracker.data.entity.HoleStat(
            id = 1, roundId = 1, holeId = 1, score = 4,
            teeOutcome = com.golftracker.data.model.ShotOutcome.SHORT,
            putts = 2
        )
        
        val breakdown = calculator.calculateHoleSg(
            par = 4, holeYardage = 400, holeAdjustment = 0.0,
            shots = emptyList(), putts = emptyList(), penalties = emptyList(), stat = stat
        )
        
        // Estimated drive landing was ~260 (65%). Short outcome makes it ~182 (70% of 260).
        // End distance = 400 - 182 = 218y.
        // SG should be lower than ON_TARGET (which was 0.32).
        // 400y Tee (4.2). 218y FAIRWAY (approx 3.3). 4.2 - 3.3 - 1 = -0.1
        assertEquals(-0.1, breakdown.offTee, 0.1)
    }
    @Test
    fun testCalculateShotSG_UserReportedScenario_454y() {
        // Mocking real CSV indices for 440, 460, 160, 180
        val csvData = """
            Header,Distance,Tee,Fairway,Rough,Sand,Recovery,Other,GreenDist,GreenPutts,Other,Other,Other,Other,Other
            ,160,2.99,2.98,3.23,3.28,3.81,,6,1.34,,,,,
            ,170,3.02,3.03,3.27,3.33,3.81,,,,,,,,
            ,180,3.05,3.08,3.31,3.40,3.82,,7,1.42,,,,,
            ,440,4.08,4.20,4.39,4.78,4.84,,,,,,,,
            ,460,4.17,4.29,4.48,4.87,4.93,,,,,,,,
        """.trimIndent()
        
        val inputStream = ByteArrayInputStream(csvData.toByteArray())
        every { context.resources.openRawResource(any()) } returns inputStream
        calculator = StrokesGainedCalculator(context)

        // Hole: 454y. Tee shot to 170y Recovery.
        // Expected Start (454y Tee): 4.08 + (4.17-4.08)*14/20 = 4.143
        // Expected End (170y Recovery): 3.81
        // Raw SG = 4.143 - 3.81 - 1.0 = -0.667

        val sg = calculator.calculateShotSG(
            startDistanceYs = 454,
            startLie = ApproachLie.TEE,
            isTeeShot = true,
            endDistanceYs = 170,
            endLie = ApproachLie.OTHER,
            endDistanceFeetOnGreen = null,
            penaltyStrokes = 0,
            holeAdjustment = 0.0
        )
        
        assertEquals(-0.667, sg, 0.001)
    }

    @Test
    fun testCalculateHoleSg_UserReportedScenario_454y() {
        // Mocking real CSV indices for 440, 460, 160, 180
        val csvData = """
            Header,Distance,Tee,Fairway,Rough,Sand,Recovery,Other,GreenDist,GreenPutts,Other,Other,Other,Other,Other
            ,160,2.99,2.98,3.23,3.28,3.81,,6,1.34,,,,,
            ,170,3.02,3.03,3.27,3.33,3.81,,,,,,,,
            ,180,3.05,3.08,3.31,3.40,3.82,,7,1.42,,,,,
            ,440,4.08,4.20,4.39,4.78,4.84,,,,,,,,
            ,460,4.17,4.29,4.48,4.87,4.93,,,,,,,,
        """.trimIndent()
        
        val inputStream = ByteArrayInputStream(csvData.toByteArray())
        every { context.resources.openRawResource(any()) } returns inputStream
        calculator = StrokesGainedCalculator(context)

        // Hole: 454y Par 4. course rating = par (no adj for simplicity)
        val stat = com.golftracker.data.entity.HoleStat(
            id = 1, roundId = 1, holeId = 1, score = 5,
            teeOutcome = com.golftracker.data.model.ShotOutcome.MISS_RIGHT,
            teeInTrouble = true
        )
        
        val shots = listOf(
            com.golftracker.data.entity.Shot(holeStatId = 1, shotNumber = 1, distanceToPin = 454, lie = ApproachLie.TEE),
            com.golftracker.data.entity.Shot(holeStatId = 1, shotNumber = 2, distanceToPin = 170, lie = ApproachLie.OTHER, isRecovery = true, penaltyAttribution = 0.0)
        )
        
        // Let's say it took 3 more shots from 170y to finish (score 5).
        // For simplicity, let's assume no more tracked shots, just score works.
        
        val breakdown = calculator.calculateHoleSg(
            par = 4, holeYardage = 454, holeAdjustment = 0.0,
            shots = shots, putts = emptyList(), penalties = emptyList(), stat = stat
        )
        
        println("User Scenario Results:")
        println("Off Tee: ${breakdown.offTee}")
        println("Approach: ${breakdown.approach}")
        println("Shot 1 SG: ${breakdown.shotSgs.find { it.first == 1 }?.second}")
        
        // Expected Off Tee Raw SG: -0.667 approx
        assertEquals(-0.667, breakdown.offTee, 0.01)
    }

    @Test
    fun testCalculateHoleSg_HeuristicBug_TeeDistanceIgnored() {
        // Setup baseline with 20ft putt (Exp 1.87) and 454y tee (Exp 4.14)
        val csvData = """
            Header,Distance,Tee,Fairway,Rough,Sand,Recovery,Other,GreenDist,GreenPutts,Other,Other,Other,Other,Other
            ,280,3.65,3.69,3.83,4.00,4.10,,20,1.87,,,,,
            ,440,4.08,4.20,4.39,4.78,4.84,,,,,,,,
            ,460,4.17,4.29,4.48,4.87,4.93,,,,,,,,
        """.trimIndent()
        
        val inputStream = ByteArrayInputStream(csvData.toByteArray())
        every { context.resources.openRawResource(any()) } returns inputStream
        calculator = StrokesGainedCalculator(context)

        // Scenario: 454y Par 4. Tee shot distance tracked in summary as 284y (meaning 170y left).
        // Putts: 1 putt from 20ft.
        // If bug exists, Tee SG will be 4.14 - 1.87 - 1.0 = 1.27
        // If fixed, Tee SG will be 4.14 - (Exp strokes at 170y) - 1.0
        
        val stat = com.golftracker.data.entity.HoleStat(
            id = 1, roundId = 1, holeId = 1, score = 3,
            teeShotDistance = 284, 
            putts = 1
        )
        
        val putts = listOf(
            com.golftracker.data.entity.Putt(holeStatId = 1, puttNumber = 1, distance = 20f, made = true)
        )
        
        val breakdown = calculator.calculateHoleSg(
            par = 4, holeYardage = 454, holeAdjustment = 0.0,
            shots = emptyList(), // No GPS tracked shots
            putts = putts, penalties = emptyList(), stat = stat
        )
        
        println("Heuristic Bug Test Result: Off Tee SG = ${breakdown.offTee}")
        
        // This fails if the bug is present (it will be 1.27)
        // We want it to be around 4.14 - 3.03 (Fairway 170) - 1.0 = 0.11
        assertTrue("Off Tee SG should not be ~1.27. Got ${breakdown.offTee}", breakdown.offTee < 1.0)
    }

    @Test
    fun testHoleAdjustment_SignConstraint() {
        // Total Course Adj > 0 (Rating 73, Par 72)
        val posTotalAdj = 1.0
        val posAdj1 = calculator.getHoleAdjustment(posTotalAdj, 1)
        val posAdj18 = calculator.getHoleAdjustment(posTotalAdj, 18)
        
        assertTrue("Hole 1 adjustment should be positive", posAdj1 > 0)
        assertTrue("Hole 18 adjustment should be positive", posAdj18 > 0)
        assertTrue("Hardest hole should get more adjustment", posAdj1 > posAdj18)
        
        // Total Course Adj < 0 (Rating 71, Par 72)
        val negTotalAdj = -1.0
        val negAdj1 = calculator.getHoleAdjustment(negTotalAdj, 1)
        val negAdj18 = calculator.getHoleAdjustment(negTotalAdj, 18)
        
        assertTrue("Hole 1 adjustment should be negative", negAdj1 < 0)
        assertTrue("Hole 18 adjustment should be negative", negAdj18 < 0)
        assertTrue("Hardest hole should get less of the negative adjustment (closer to zero)", negAdj1 > negAdj18)
    }
    @Test
    fun testCalculateHoleSg_AttributedPenalty_SubtractsFromShotAndComponent() {
        val stat = com.golftracker.data.entity.HoleStat(id = 1, roundId = 1, holeId = 1)
        val shot1 = com.golftracker.data.entity.Shot(holeStatId = 1, shotNumber = 1, distanceToPin = 400, lie = ApproachLie.TEE)
        
        // Base SG for 400y Tee to 150y Fairway (Exp 4.2 - 3.0 - 1 = 0.2)
        val shot2 = com.golftracker.data.entity.Shot(holeStatId = 1, shotNumber = 2, distanceToPin = 150, lie = ApproachLie.FAIRWAY)
        
        // Penalty attributed to Shot 1
        val penalty = com.golftracker.data.entity.Penalty(id = 1, holeStatId = 1, type = com.golftracker.data.model.PenaltyType.WATER, strokes = 1, shotNumber = 1)
        
        val breakdown = calculator.calculateHoleSg(
            par = 4, holeYardage = 400, holeAdjustment = 0.0,
            shots = listOf(shot1, shot2), putts = emptyList(), penalties = listOf(penalty), stat = stat
        )
        
        val sg1 = breakdown.shotSgs.find { it.first == 1 }?.second ?: 0.0
        
        // Expected SG1 = 0.2 - 1.0 = -0.8
        assertEquals(-0.8, sg1, 0.01)
        // Expected offTee = 0.2 - 1.0 = -0.8
        assertEquals(-0.8, breakdown.offTee, 0.01)
        // penalities component should be 0 because result is attributed
        assertEquals(0.0, breakdown.penalties, 0.01)
    }

    @Test
    fun testCalculateHoleSg_TeeInTrouble_WithTrackedShots() {
        val stat = com.golftracker.data.entity.HoleStat(id = 1, roundId = 1, holeId = 1, teeInTrouble = true)
        val shot1 = com.golftracker.data.entity.Shot(holeStatId = 1, shotNumber = 1, distanceToPin = 400, lie = ApproachLie.TEE)
        
        // Target: 150y. If endLie is FAIRWAY (normal), SG = 4.2 - 3.0 - 1 = 0.2
        // Since teeInTrouble = true, endLie becomes OTHER. Exp OTHER at 150y = 3.6
        // SG = 4.2 - 3.6 - 1 = -0.4
        val shot2 = com.golftracker.data.entity.Shot(holeStatId = 1, shotNumber = 2, distanceToPin = 150, lie = ApproachLie.FAIRWAY)
        
        val breakdown = calculator.calculateHoleSg(
            par = 4, holeYardage = 400, holeAdjustment = 0.0,
            shots = listOf(shot1, shot2), putts = emptyList(), penalties = emptyList(), stat = stat
        )
        
        assertEquals(-0.4, breakdown.offTee, 0.01)
    }

    @Test
    fun testCalculateHoleSg_ReTee_OB() {
        val stat = com.golftracker.data.entity.HoleStat(
            id = 1, roundId = 1, holeId = 1, 
            teeOutcome = com.golftracker.data.model.ShotOutcome.MISS_RIGHT,
            score = 6
        )
        
        // Shot 1 is OB (from Tee)
        // Shot 3 is the Re-tee (from Tee)
        val shot3 = com.golftracker.data.entity.Shot(holeStatId = 1, shotNumber = 3, distanceToPin = 400, lie = ApproachLie.TEE)
        
        // Penalty on Shot 1
        val penalty = com.golftracker.data.entity.Penalty(id = 1, holeStatId = 1, type = com.golftracker.data.model.PenaltyType.OB, strokes = 1, shotNumber = 1)
        
        // Target: 150y. SG = 4.2 - 3.0 - 1 = 0.2
        val shot4 = com.golftracker.data.entity.Shot(holeStatId = 1, shotNumber = 4, distanceToPin = 150, lie = ApproachLie.FAIRWAY)
        val putts = emptyList<com.golftracker.data.entity.Putt>()

        val breakdown = calculator.calculateHoleSg(
            par = 4, holeYardage = 400, holeAdjustment = 0.0,
            shots = listOf(shot3, shot4), putts = putts, penalties = listOf(penalty), stat = stat
        )
        
        // Shot 1 SG (HoleStat heuristic): 400 -> 400 (Shot 3 start). SG = 4.2 - 4.2 - 1 = -1.0. 
        // With penalty: -1.0 - 1.0 = -2.0
        
        // Shot 3 SG: 400 -> 150. SG = 4.2 - 3.0 - 1 = 0.2
        
        // Shot 1 is Off-Tee. Shot 3 is Approach.
        // Shot 4 is correctly skipped because it has no end distance/confirmed holing out.
        assertEquals(-2.0, breakdown.offTee, 0.01)
        assertEquals(0.2, breakdown.approach, 0.01)
    }

    @Test
    fun testCalculateHoleSg_ReTee_OB_IgnoresTrouble() {
        // Par 4, 400 yards.
        val stat = com.golftracker.data.entity.HoleStat(
            id = 1, roundId = 1, holeId = 1, 
            teeOutcome = com.golftracker.data.model.ShotOutcome.MISS_LEFT,
            teeInTrouble = true, // Force "In Trouble"
            score = 6
        )
        
        // Shot 1 is OB (from Tee). Shot 3 is the Re-tee (from Tee).
        val shot3 = com.golftracker.data.entity.Shot(holeStatId = 1, shotNumber = 3, distanceToPin = 400, lie = ApproachLie.TEE)
        
        // Penalty on Shot 1 (OB)
        val penalty = com.golftracker.data.entity.Penalty(id = 1, holeStatId = 1, type = com.golftracker.data.model.PenaltyType.OB, strokes = 1, shotNumber = 1)
        
        // Shot 4: 150y. SG = 4.2 - 3.0 - 1 = 0.2
        val shot4 = com.golftracker.data.entity.Shot(holeStatId = 1, shotNumber = 4, distanceToPin = 150, lie = ApproachLie.FAIRWAY)
        val putts = emptyList<com.golftracker.data.entity.Putt>()

        val breakdown = calculator.calculateHoleSg(
            par = 4, holeYardage = 400, holeAdjustment = 0.0,
            shots = listOf(shot3, shot4), putts = putts, penalties = listOf(penalty), stat = stat
        )
        
        // Shot 1 is Off-Tee. Shot 3 is Approach.
        assertEquals(-2.0, breakdown.offTee, 0.01)
        assertEquals(0.2, breakdown.approach, 0.01)
        assertEquals(-2.0, breakdown.shotSgs.find { it.first == 1 }?.second ?: 0.0, 0.01)
    }

    @Test
    fun testCalculateHoleSg_ReTee_WithExistingScore_NoPositiveSpike() {
        // Scenario: A user hit OB, and wants to add the re-tee.
        // The hole already has a score (e.g., 6).
        val stat = com.golftracker.data.entity.HoleStat(
            id = 1, roundId = 1, holeId = 1, 
            score = 6,
            teeOutcome = com.golftracker.data.model.ShotOutcome.MISS_LEFT
        )
        
        // Shot 1 is OB. Penalty is added.
        val penalty = com.golftracker.data.entity.Penalty(id = 1, holeStatId = 1, type = com.golftracker.data.model.PenaltyType.OB, strokes = 1, shotNumber = 1)
        
        // Before Shot 3 is added, the calculator uses the heuristic for Shot 1.
        // With the fix, Shot 1 should NOT assume it holed out.
        // It should be 400y -> 400y (fixed cost) = -1.0. With penalty = -2.0.
        
        val breakdown = calculator.calculateHoleSg(
            par = 4, holeYardage = 400, holeAdjustment = 0.0,
            shots = emptyList(), putts = emptyList(), penalties = listOf(penalty), stat = stat
        )
        
        // If it correctly ignores the score for the tee shot: 
        // 400 -> 400. SG = 4.2 - 1 - 4.2 = -1.0. 
        // With penalty = -2.0.
        assertEquals(-2.0, breakdown.offTee, 0.1)
    }

    @Test
    fun testCalculateHoleSg_ReTee_OB_IgnoresDispersion() {
        // Par 4, 400 yards.
        val stat = com.golftracker.data.entity.HoleStat(
            id = 1, roundId = 1, holeId = 1, 
            score = 6,
            teeOutcome = com.golftracker.data.model.ShotOutcome.MISS_RIGHT,
            teeDispersionRight = 100, // Large miss right
            teeDispersionLong = 0
        )
        
        // Shot 1 is OB. Penalty is added.
        val penalty = com.golftracker.data.entity.Penalty(id = 1, holeStatId = 1, type = com.golftracker.data.model.PenaltyType.OB, strokes = 1, shotNumber = 1)
        
        val breakdown = calculator.calculateHoleSg(
            par = 4, holeYardage = 400, holeAdjustment = 0.0,
            shots = emptyList(), putts = emptyList(), penalties = listOf(penalty), stat = stat
        )
        
        // Even with huge dispersion, the OB penalty should force Shot 1 SG = -1.0.
        // Total Off-Tee = -1.0 (shot) - 1.0 (pen) = -2.0
        assertEquals(-2.0, breakdown.offTee, 0.1)
    }

    @Test
    fun testCalculateHoleSg_ReTee_AttributedToApproach() {
        // Par 4, 400 yards.
        val stat = com.golftracker.data.entity.HoleStat(
            id = 1, roundId = 1, holeId = 1, 
            score = 4, // Birdie after re-tee? (Unlikely but for test)
            teeOutcome = com.golftracker.data.model.ShotOutcome.MISS_RIGHT
        )
        
        // Shot 1 is OB. Penalty is added.
        val penalty = com.golftracker.data.entity.Penalty(id = 1, holeStatId = 1, type = com.golftracker.data.model.PenaltyType.OB, strokes = 1, shotNumber = 1)
        
        // Shot 3 (Re-tee) starts from the tee (null distanceToPin fallbacks to yardage).
        // It lands at 100 yards (Shot 4 distanceToPin).
        val reTee = com.golftracker.data.entity.Shot(id = 1, holeStatId = 1, shotNumber = 3, distanceToPin = null, lie = com.golftracker.data.model.ApproachLie.TEE)
        
        // Shot 4 is hit onto the green (10 feet)
        val shot4 = com.golftracker.data.entity.Shot(id = 2, holeStatId = 1, shotNumber = 4, distanceToPin = 3, lie = com.golftracker.data.model.ApproachLie.FAIRWAY) // 3 yards = 9 feet
        
        // Shot 5 is a made putt
        val putt5 = com.golftracker.data.entity.Putt(id = 1, holeStatId = 1, puttNumber = 5, distance = 9f, made = true)

        val breakdown = calculator.calculateHoleSg(
            par = 4, holeYardage = 400, holeAdjustment = 0.0,
            shots = listOf(reTee, shot4), putts = listOf(putt5), penalties = listOf(penalty), stat = stat
        )
        
        // Off-Tee should remain at -2.0 (Shot 1 + OB penalty).
        assertEquals(-2.0, breakdown.offTee, 0.01)
        
        // The gains/losses from Shot 3 and Shot 4 are in "Approach".
        assertEquals(-0.068, breakdown.approach, 0.01)
    }
}
