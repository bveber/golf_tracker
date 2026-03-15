package com.golftracker.util

import android.content.Context
import com.golftracker.data.model.ApproachLie
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream

class StrokesGainedCalculatorTest {

    private lateinit var calculator: StrokesGainedCalculator
    private val context: Context = mockk()

    @Before
    fun setup() {
        // Mock the raw resource for baseline data
        val csvData = """
            Header,Distance,Tee,Fairway,Rough,Sand,Recovery,Other,GreenDist,GreenPutts,Other,Other,Other,Other,Other
            ,10,0.0,2.1,2.3,2.5,2.7,,10.0,1.5,,,,
            ,20,0.0,2.2,2.4,2.6,2.8,,20.0,1.8,,,,
            ,100,2.9,2.8,3.0,3.2,3.4,,100.0,2.5,,,,
            ,200,3.1,3.0,3.2,3.4,3.6,,200.0,3.0,,,,
            ,500,4.8,4.7,4.9,5.1,5.3,,500.0,3.5,,,,
        """.trimIndent()
        
        val inputStream = ByteArrayInputStream(csvData.toByteArray())
        every { context.resources.openRawResource(any()) } returns inputStream
        
        calculator = StrokesGainedCalculator(context)
    }

    @Test
    fun testCalculateShotSG_ApproachToGreen() {
        // Start 100y Fairway (Exp 2.8)
        // End on Green at 20ft (Exp 1.8)
        // SG = 2.8 - 1.8 - 1 = 0.0
        val sg = calculator.calculateShotSG(
            startDistanceYs = 100,
            startLie = ApproachLie.FAIRWAY,
            isTeeShot = false,
            endDistanceYs = 0,
            endLie = null,
            endDistanceFeetOnGreen = 20f,
            penaltyStrokes = 0,
            holeAdjustment = 0.0
        )
        assertEquals(0.0, sg, 0.01)
    }

    @Test
    fun testCalculateShotSG_HoledOut() {
        // Start 10y Fairway (Exp 2.1)
        // Holed out (Exp 0.0)
        // SG = 2.1 - 0.0 - 1 = 1.1
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
        assertEquals(1.1, sg, 0.01)
    }

    @Test
    fun testCalculateShotSG_Recovery() {
        // Start 200y Recovery (Exp 3.6)
        // End 100y Fairway (Exp 2.8)
        // SG = 3.6 - 2.8 - 1 = -0.2
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
        assertEquals(-0.2, sg, 0.01)
    }
}
