package com.golftracker.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.golftracker.data.model.ApproachLie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StrokesGainedCalculatorTest {

    private lateinit var calculator: StrokesGainedCalculator
    private lateinit var context: Context

    val courseRating = 72.0
    val courseSlope = 113
    val coursePar = 72
    val holeIndex = 9

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        calculator = StrokesGainedCalculator(context)
    }
    
    // Scratch golfers are expected to shoot 0 SG on an average hole. 
    // Allowing a small tolerance (+/- 0.5) for rounding or slight interpolation artifacts in the data table.
    private val sgTolerance = 0.5

    @Test
    fun testPerfectPar3() {
        // Hole: 150 yards, Par 3
        
        // Shot 1: Tee shot to the green, lands 10 feet from the hole
        // Distance: 150 -> 0 (on green)
        val teeSg = calculator.calculateShotSG(
            startDistanceYs = 150,
            startLie = ApproachLie.TEE,
            isTeeShot = true,
            endDistanceYs = 0,
            endLie = null,
            endDistanceFeetOnGreen = 10f,
            penaltyStrokes = 0,
            courseRating = courseRating,
            courseSlope = courseSlope,
            coursePar = coursePar,
            holeIndex = holeIndex
        )
        
        // Shot 2: 10 foot putt, missed, leaves 2 foot putt
        val putt1Sg = calculator.calculatePuttSG(10f, false, 2f)
        
        // Shot 3: 2 foot putt, made
        val putt2Sg = calculator.calculatePuttSG(2f, true, null)
        
        val totalSg = teeSg + putt1Sg + putt2Sg
        
        // Scratch golfer made Par. SG should be roughly 0.
        assertTrue("SG for a perfect Par 3 should be near 0, but was $totalSg", Math.abs(totalSg) < sgTolerance)
    }

    @Test
    fun testTypicalPar4() {
        // Hole: 400 yards, Par 4
        
        // Shot 1: Tee shot to fairway, 140 yards remaining
        // Distance: 400 -> 140
        val teeSg = calculator.calculateShotSG(
            startDistanceYs = 400,
            startLie = ApproachLie.TEE,
            isTeeShot = true,
            endDistanceYs = 140,
            endLie = ApproachLie.FAIRWAY,
            endDistanceFeetOnGreen = null,
            penaltyStrokes = 0,
            courseRating = courseRating,
            courseSlope = courseSlope,
            coursePar = coursePar,
            holeIndex = holeIndex
        )
        
        // Shot 2: Approach shot to green, lands 15 feet from hole
        // Distance: 140 -> 0
        val appSg = calculator.calculateShotSG(
            startDistanceYs = 140,
            startLie = ApproachLie.FAIRWAY,
            isTeeShot = false,
            endDistanceYs = 0,
            endLie = null,
            endDistanceFeetOnGreen = 15f,
            penaltyStrokes = 0,
            courseRating = courseRating,
            courseSlope = courseSlope,
            coursePar = coursePar,
            holeIndex = holeIndex
        )
        
        // Shot 3: 15 foot putt, missed, leaves 3 foot putt
        val putt1Sg = calculator.calculatePuttSG(15f, false, 3f)
        
        // Shot 4: 3 foot putt, made
        val putt2Sg = calculator.calculatePuttSG(3f, true, null)
        
        val totalSg = teeSg + appSg + putt1Sg + putt2Sg
        
        // Scratch golfer made Par. SG should be roughly 0.
        assertTrue("SG for a typical Par 4 should be near 0, but was $totalSg", Math.abs(totalSg) < sgTolerance)
    }

    @Test
    fun testScramblingPar4() {
        // Hole: 420 yards, Par 4
        
        // Shot 1: Tee shot finds the rough, 160 yards left
        val teeSg = calculator.calculateShotSG(420, ApproachLie.TEE, true, 160, ApproachLie.ROUGH, null, 0, courseRating, courseSlope, coursePar, holeIndex)
        
        // Shot 2: Approach misses green, ends up in greenside sand (10 yards left)
        val appSg = calculator.calculateShotSG(160, ApproachLie.ROUGH, false, 10, ApproachLie.SAND, null, 0, courseRating, courseSlope, coursePar, holeIndex)
        
        // Shot 3: Excellent bunker shot to 4 feet
        val chipSg = calculator.calculateShotSG(10, ApproachLie.SAND, false, 0, null, 4f, 0, courseRating, courseSlope, coursePar, holeIndex)
        
        // Shot 4: 4 foot putt made for Par Save
        val puttSg = calculator.calculatePuttSG(4f, true, null)
        
        val totalSg = teeSg + appSg + chipSg + puttSg
        
        // Scratch golfer made Par. The individual shots will deviate wildly, but the sum should still be 0.
        // E.g., Tee shot and approach lost strokes, but bunker shot and putt gained strokes.
        assertTrue("SG for a scrambled Par 4 should balance out near 0, but was $totalSg", Math.abs(totalSg) < sgTolerance)
    }
    
    @Test
    fun testTwoPuttBirdiePar5() {
        // Hole: 540 yards, Par 5
        
        // Shot 1: Brilliant drive down the middle, 240 yards left
        val teeSg = calculator.calculateShotSG(540, ApproachLie.TEE, true, 240, ApproachLie.FAIRWAY, null, 0, courseRating, courseSlope, coursePar, holeIndex)
        
        // Shot 2: Great 3-wood lands on the green, 30 feet for eagle
        val appSg = calculator.calculateShotSG(240, ApproachLie.FAIRWAY, false, 0, null, 30f, 0, courseRating, courseSlope, coursePar, holeIndex)
        
        // Shot 3: 30 foot eagle putt misses, leaves 2 feet
        val putt1Sg = calculator.calculatePuttSG(30f, false, 2f)
        
        // Shot 4: 2 foot putt made for Birdie (4)
        val putt2Sg = calculator.calculatePuttSG(2f, true, null)
        
        val totalSg = teeSg + appSg + putt1Sg + putt2Sg
        
        // A birdie represents 1 stroke gained over a scratch golfer (who averages ~4.8-5.0 on a Par 5).
        // SG should be roughly +1.0. 
        assertTrue("SG for a birdie should be near +1.0, but was $totalSg", Math.abs(totalSg - 1.0) < sgTolerance)
    }
    
    @Test
    fun testDisasterBogeyPar4() {
        // Hole: 380 yards, Par 4
        
        // Shot 1: Tee shot sliced OB (penalty)
        // Re-teeing is functionally shooting from 380 yards TEE again, but with +1 penalty stroke and -1 stroke taken.
        // Actually, the app tracks penalties separately, but the model `SG = ExpectedStart - ExpectedEnd - 1 - Penalty`
        // So hitting OB -> 380 TEE (hitting 3). Next shot expected is 380 TEE.
        val teeSg = calculator.calculateShotSG(
            startDistanceYs = 380, startLie = ApproachLie.TEE, isTeeShot = true, 
            endDistanceYs = 380, endLie = ApproachLie.TEE, endDistanceFeetOnGreen = null, 
            penaltyStrokes = 1, courseRating, courseSlope, coursePar, holeIndex
        )
        
        // Shot 3: Good drive, 120 left
        val shot3Sg = calculator.calculateShotSG(380, ApproachLie.TEE, false, 120, ApproachLie.FAIRWAY, null, 0, courseRating, courseSlope, coursePar, holeIndex)
        
        // Shot 4: Approach to 10 feet
        val shot4Sg = calculator.calculateShotSG(120, ApproachLie.FAIRWAY, false, 0, null, 10f, 0, courseRating, courseSlope, coursePar, holeIndex)
        
        // Shot 5: 10 footer made for Bogey 5
        val puttSg = calculator.calculatePuttSG(10f, true, null)
        
        val totalSg = teeSg + shot3Sg + shot4Sg + puttSg
        
        // A bogey on a Par 4 represents roughly 1 stroke lost (-1.0 SG).
        assertTrue("SG for a bogey with penalty should be near -1.0, but was $totalSg", Math.abs(totalSg - (-1.0)) < sgTolerance)
    }
}
