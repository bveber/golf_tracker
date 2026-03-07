package com.golftracker.util

import android.content.Context
import com.golftracker.R
import com.golftracker.data.model.ApproachLie
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

data class BaselineRow(
    val distance: Int,
    val tee: Double?,
    val fairway: Double?,
    val rough: Double?,
    val sand: Double?,
    val recovery: Double?,
    val greenDistance: Int?,
    val greenPutts: Double?
)

data class HoleSgBreakdown(
    val offTee: Double = 0.0,
    val approach: Double = 0.0,
    val aroundGreen: Double = 0.0,
    val putting: Double = 0.0,
    val penalties: Double = 0.0
) {
    val total: Double get() = offTee + approach + aroundGreen + putting - penalties
}

@Singleton
class StrokesGainedCalculator @Inject constructor(@ApplicationContext private val context: Context) {

    private val baselineData: List<BaselineRow>
    
    init {
        baselineData = loadBaselineData()
    }

    private fun loadBaselineData(): List<BaselineRow> {
        val rows = mutableListOf<BaselineRow>()
        try {
            val inputStream = context.resources.openRawResource(R.raw.sg_baseline)
            val reader = BufferedReader(InputStreamReader(inputStream))
            
            var line: String? = reader.readLine()
            // Skip header rows (there are about 6 header rows, we'll look for lines starting with ',10,' or similar)
            // or just skip by line index. The CSV has 5 header lines before data.
            // Let's just skip until we hit a valid distance number.
            while (line != null) {
                val tokens = line.split(",")
                if (tokens.size >= 14) {
                    val distanceStr = tokens[1].trim()
                    val distance = distanceStr.toIntOrNull()
                    
                    if (distance != null) {
                        val row = BaselineRow(
                            distance = distance,
                            tee = tokens[2].trim().toDoubleOrNull(),
                            fairway = tokens[3].trim().toDoubleOrNull(),
                            rough = tokens[4].trim().toDoubleOrNull(),
                            sand = tokens[5].trim().toDoubleOrNull(),
                            recovery = tokens[6].trim().toDoubleOrNull(),
                            greenDistance = tokens[8].trim().toDoubleOrNull()?.toInt(),
                            greenPutts = tokens[9].trim().toDoubleOrNull()
                        )
                        rows.add(row)
                    }
                }
                line = reader.readLine()
            }
            reader.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return rows
    }

    /**
     * Calculates the expected number of strokes to hole out from a given distance and lie.
     * Combines PGA Tour baseline data and interpolates between known distance buckets.
     * 
     * @param distance The distance to the pin in yards. For tee shots, this is the hole yardage.
     * @param lie The lie of the ball.
     * @param isTeeShot True if the shot is a tee shot on a par 4 or par 5.
     * @return The expected number of strokes to finish the hole.
     */
    fun getExpectedStrokes(distance: Int, lie: ApproachLie?, isTeeShot: Boolean): Double {
        if (distance <= 0) return 0.0
        if (baselineData.isEmpty()) return 0.0 // Fail safe
        
        // Find closest buckets
        val sortedRows = baselineData.sortedBy { it.distance }
        
        var lowerRow: BaselineRow? = null
        var upperRow: BaselineRow? = null
        
        for (row in sortedRows) {
            if (row.distance == distance) {
                return extractValueForLie(row, lie, isTeeShot) ?: 0.0
            }
            if (row.distance < distance) {
                lowerRow = row
            } else if (upperRow == null) {
                upperRow = row
                break
            }
        }
        
        if (lowerRow == null && upperRow != null) {
            return extractValueForLie(upperRow, lie, isTeeShot) ?: 0.0
        }
        if (upperRow == null && lowerRow != null) {
            return extractValueForLie(lowerRow, lie, isTeeShot) ?: 0.0
        }
        
        if (lowerRow != null && upperRow != null) {
            val lowerVal = extractValueForLie(lowerRow, lie, isTeeShot)
            val upperVal = extractValueForLie(upperRow, lie, isTeeShot)
            
            if (lowerVal == null && upperVal != null) return upperVal
            if (upperVal == null && lowerVal != null) return lowerVal
            if (lowerVal != null && upperVal != null) {
                // Interpolate
                val fraction = (distance - lowerRow.distance).toDouble() / (upperRow.distance - lowerRow.distance)
                return lowerVal + fraction * (upperVal - lowerVal)
            }
        }
        
        return 0.0
    }
    
    /**
     * Calculates the expected number of putts to hole out from a given distance on the green.
     * Uses baseline data and interpolates for exact distances.
     * 
     * @param distanceFeet The distance to the hole in feet.
     * @return The expected number of putts.
     */
    fun getExpectedPutts(distanceFeet: Float): Double {
        if (distanceFeet <= 0f) return 0.0
        if (baselineData.isEmpty()) return 0.0
        
        val rowsWithPuttData = baselineData.filter { it.greenDistance != null && it.greenPutts != null }.sortedBy { it.greenDistance }
        if (rowsWithPuttData.isEmpty()) return 0.0
        
        var lowerRow: BaselineRow? = null
        var upperRow: BaselineRow? = null
        
        for (row in rowsWithPuttData) {
            val d = row.greenDistance!!.toFloat()
            if (abs(d - distanceFeet) < 0.001f) {
                return row.greenPutts!!
            }
            if (d < distanceFeet) {
                lowerRow = row
            } else if (upperRow == null) {
                upperRow = row
                break
            }
        }
        
        if (lowerRow == null && upperRow != null) return upperRow.greenPutts!!
        if (upperRow == null && lowerRow != null) return lowerRow.greenPutts!!
        
        if (lowerRow != null && upperRow != null) {
            val lowerVal = lowerRow.greenPutts!!
            val upperVal = upperRow.greenPutts!!
            val fraction = (distanceFeet - lowerRow.greenDistance!!).toDouble() / (upperRow.greenDistance!! - lowerRow.greenDistance!!)
            return lowerVal + fraction * (upperVal - lowerVal)
        }
        
        return 0.0
    }

    private fun extractValueForLie(row: BaselineRow, lie: ApproachLie?, isTeeShot: Boolean): Double? {
        if (isTeeShot && row.tee != null) return row.tee
        
        return when (lie) {
            ApproachLie.TEE -> row.tee
            ApproachLie.FAIRWAY -> row.fairway ?: row.tee // fallback
            ApproachLie.ROUGH -> row.rough ?: row.fairway
            ApproachLie.SAND -> row.sand ?: row.rough
            ApproachLie.OTHER -> row.recovery ?: row.rough
            null -> row.fairway // default assumption
        }
    }

    /**
     * Calculate the total adjustment needed to align the PGA Tour Pro baseline
     * with a Scratch Golfer for the given course and tees.
     *
     * @param courseRating USGA Course Rating.
     * @param holes A list of hole yardages and their handicap indices.
     * @return The total adjustment in strokes for the 18-hole round.
     */
    fun calculateCourseAdjustment(
        courseRating: Double,
        holes: List<Pair<Int, Int?>> // Yardage, HandicapIndex
    ): Double {
        var totalProExpected = 0.0
        for ((yardage, _) in holes) {
            totalProExpected += getExpectedStrokes(yardage, ApproachLie.TEE, true)
        }
        return courseRating - totalProExpected
    }

    /**
     * Calculate Hole Difficulty Adjustment to apply to the Tee shot.
     * This adjusts the PGA Tour baseline to account for the specific difficulty
     * of the course and hole being played, anchored to the Course Rating.
     *
     * @param totalCourseAdjustment The result from calculateCourseAdjustment.
     * @param holeIndex Handicap index of the specific hole (1 = hardest, 18 = easiest).
     * @param holeCount Number of holes in the round (usually 18).
     * @return The adjustment value for this specific hole in strokes.
     */
    fun getHoleAdjustment(totalCourseAdjustment: Double, holeIndex: Int?, holeCount: Int = 18): Double {
        val validIndex = holeIndex ?: 9
        // Distribute the total adjustment. Harder holes get slightly more of the "scratch gap".
        // This formula ensures Sum(holeAdjustments) == totalCourseAdjustment.
        // weight = (19 - index) / Sum(19 - i for i in 1..18)
        // Sum(1..18) of (19-i) is 171.
        val factor = if (holeCount == 18) 171.0 else 45.0 // 45 for 9 holes (Sum 1..9 of 10-i)
        val maxIdx = holeCount + 1
        val weight = (maxIdx - validIndex).toDouble() / (holeCount * (maxIdx + 1) / 2.0 - holeCount * (holeCount + 1) / 2.0 + (holeCount * (maxIdx)))
        // Simpler: just use (19-index)/171 for 18 holes.
        return totalCourseAdjustment * (19.0 - validIndex) / 171.0
    }

    /**
     * Calculate Strokes Gained for an approach or tee shot.
     *
     * @param startDistanceYs The starting distance to the pin in yards.
     * @param startLie The starting lie of the ball.
     * @param isTeeShot True if this is the first shot on a par 4 or par 5.
     * @param endDistanceYs The ending distance to the pin in yards (0 if holed).
     * @param endLie The ending lie of the ball, or null if on the green/holed.
     * @param endDistanceFeetOnGreen The ending distance in feet if the ball landed on the green.
     * @param penaltyStrokes Any penalty strokes incurred on this shot.
     * @param holeAdjustment The difficulty adjustment for this specific hole (from getHoleAdjustment).
     * @return The Strokes Gained value for this shot.
     */
    fun calculateShotSG(
        startDistanceYs: Int,
        startLie: ApproachLie?,
        isTeeShot: Boolean,
        endDistanceYs: Int, // 0 if holed
        endLie: ApproachLie?, // GREEN if on green, null if holed
        endDistanceFeetOnGreen: Float?, // if landed on green
        penaltyStrokes: Int,
        holeAdjustment: Double
    ): Double {
        var expectedStart = getExpectedStrokes(startDistanceYs, startLie, isTeeShot)
        
        // Add difficulty adjustment entirely to the tee shot
        if (isTeeShot) {
            expectedStart += holeAdjustment
        }
        
        val expectedEnd = if (endDistanceYs == 0 && endDistanceFeetOnGreen == null) {
            0.0 // Holed out
        } else if (endDistanceFeetOnGreen != null) {
            getExpectedPutts(endDistanceFeetOnGreen)
        } else {
            getExpectedStrokes(endDistanceYs, endLie, false)
        }
        
        return expectedStart - expectedEnd - 1.0 - penaltyStrokes
    }

    /**
     * Calculate Strokes Gained for a single putt.
     *
     * @param startDistanceFeet The starting distance of the putt in feet.
     * @param made True if the putt was holed out.
     * @param nextPuttDistanceFeet The distance of the subsequent putt in feet, if missed.
     * @return The Strokes Gained value for this putt.
     */
    fun calculatePuttSG(
        startDistanceFeet: Float,
        made: Boolean,
        nextPuttDistanceFeet: Float?
    ): Double {
        val expectedStart = getExpectedPutts(startDistanceFeet)
        val expectedEnd = if (made || nextPuttDistanceFeet == null) 0.0 else getExpectedPutts(nextPuttDistanceFeet)
        return expectedStart - expectedEnd - 1.0
    }

    /**
     * Comprehensive Hole Strokes Gained Calculation.
     * Use this for both live playing and historical reports.
     */
    fun calculateHoleSg(
        par: Int,
        holeYardage: Int,
        holeAdjustment: Double,
        shots: List<com.golftracker.data.entity.Shot>,
        putts: List<com.golftracker.data.entity.Putt>,
        penalties: Int,
        stat: com.golftracker.data.entity.HoleStat
    ): HoleSgBreakdown {
        var offTee = 0.0
        var approach = 0.0
        var aroundGreen = 0.0
        var putting = 0.0

        val sortedShots = shots.sortedBy { it.shotNumber }
        val sortedPutts = putts.sortedBy { it.puttNumber }

        // 1. TEE SHOT (Par 4/5)
        if (par > 3 && (stat.score > 0 || stat.teeOutcome != null)) {
            var endDist = 0
            var endLie: ApproachLie? = null
            var greenFeet: Float? = null
            var hasEnd = false

            if (sortedShots.isNotEmpty()) {
                endDist = sortedShots.first().distanceToPin ?: 0
                endLie = sortedShots.first().lie
                hasEnd = true
            } else if (stat.chips > 0 || stat.sandShots > 0) {
                endDist = stat.chipDistance ?: 15
                endLie = if (stat.sandShots > 0) ApproachLie.SAND else ApproachLie.ROUGH
                hasEnd = true
            } else if (sortedPutts.isNotEmpty()) {
                greenFeet = sortedPutts.first().distance
                hasEnd = true
            } else if (stat.score > 0) {
                endDist = 0
                hasEnd = true
            }

            // Manual distance override
            if (stat.teeShotDistance != null) {
                endDist = ShotDistanceCalculator.deriveEndDistance(holeYardage, stat.teeShotDistance!!, stat.teeOutcome)
                hasEnd = true
            }

            if (hasEnd) {
                val actualLie = if (stat.teeInTrouble) ApproachLie.OTHER else endLie
                offTee = calculateShotSG(holeYardage, ApproachLie.TEE, true, endDist, actualLie, greenFeet, 0, holeAdjustment)
            }
        }

        // 2. APPROACH SHOTS
        for (i in sortedShots.indices) {
            val shot = sortedShots[i]
            val isFirstShotOfPar3 = (par == 3 && i == 0)
            val startDist = shot.distanceToPin ?: if (isFirstShotOfPar3) holeYardage else continue
            val startLie = if (isFirstShotOfPar3) ApproachLie.TEE else shot.lie ?: ApproachLie.FAIRWAY

            var endDist = 0
            var endLie: ApproachLie? = null
            var greenFeet: Float? = null
            var hasEnd = false

            if (i + 1 < sortedShots.size) {
                endDist = sortedShots[i+1].distanceToPin ?: 0
                endLie = sortedShots[i+1].lie
                hasEnd = true
            } else if (stat.chips > 0 || stat.sandShots > 0) {
                endDist = stat.chipDistance ?: 15
                endLie = if (stat.sandShots > 0) ApproachLie.SAND else ApproachLie.ROUGH
                hasEnd = true
            } else if (sortedPutts.isNotEmpty()) {
                greenFeet = sortedPutts.first().distance
                hasEnd = true
            } else if (stat.score > 0) {
                endDist = 0
                hasEnd = true
            }

            if (shot.distanceTraveled != null) {
                endDist = ShotDistanceCalculator.deriveEndDistance(startDist, shot.distanceTraveled!!, shot.outcome)
                hasEnd = true
            }

            if (hasEnd) {
                val sg = calculateShotSG(startDist, startLie, isFirstShotOfPar3, endDist, endLie, greenFeet, 0, if (isFirstShotOfPar3) holeAdjustment else 0.0)
                if (isFirstShotOfPar3) offTee += sg else approach += sg
            }
        }

        // 3. SHORT GAME
        if (stat.chips > 0 || stat.sandShots > 0) {
            val startDist = stat.chipDistance ?: 15
            val startLie = if (stat.sandShots > 0) ApproachLie.SAND else (stat.chipLie ?: ApproachLie.ROUGH)
            var greenFeet: Float? = null
            if (sortedPutts.isNotEmpty()) {
                greenFeet = sortedPutts.first().distance 
            }
            val sg = calculateShotSG(startDist, startLie, false, 0, ApproachLie.FAIRWAY, greenFeet, 0, 0.0)
            aroundGreen = sg
        }

        // 4. PUTTING
        for (i in sortedPutts.indices) {
            val putt = sortedPutts[i]
            val nextDist = if (i + 1 < sortedPutts.size) sortedPutts[i+1].distance else null
            putting += calculatePuttSG(putt.distance ?: 0f, putt.made, nextDist)
        }

        return HoleSgBreakdown(offTee, approach, aroundGreen, putting, penalties.toDouble())
    }
}
