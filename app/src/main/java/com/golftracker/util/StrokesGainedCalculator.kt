package com.golftracker.util

import android.content.Context
import com.golftracker.R
import com.golftracker.data.model.ApproachLie
import java.io.BufferedReader
import java.io.InputStreamReader
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

class StrokesGainedCalculator(private val context: Context) {

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
                            greenDistance = tokens[8].trim().toIntOrNull(),
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
     * Calculate Hole Difficulty Adjustment to apply to the Tee shot.
     * This adjusts the PGA Tour baseline (which assumes a scratch golfer on an average difficulty course)
     * to account for the specific difficulty of the course and hole being played.
     * 
     * @param courseRating USGA Course Rating.
     * @param courseSlope USGA Course Slope.
     * @param coursePar Total par for the course.
     * @param holeIndex Handicap index of the specific hole (1 = hardest, 18 = easiest).
     * @return The adjustment value in strokes.
     */
    fun getHoleAdjustment(courseRating: Double, courseSlope: Int, coursePar: Int, holeIndex: Int?): Double {
        val courseScratchDifference = (courseRating - coursePar) * (courseSlope / 113.0)
        // Hardest hole (index 1) gets the highest adjustment, easiest (18) gets lowest.
        val validIndex = holeIndex ?: 9 // default to middle if unknown
        return courseScratchDifference * (19.0 - validIndex) / 171.0
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
     * @param courseRating The USGA course rating.
     * @param courseSlope The USGA course slope.
     * @param coursePar The course par.
     * @param holeIndex The handicap index of the hole (1 is hardest, 18 is easiest).
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
        courseRating: Double,
        courseSlope: Int,
        coursePar: Int,
        holeIndex: Int?
    ): Double {
        var expectedStart = getExpectedStrokes(startDistanceYs, startLie, isTeeShot)
        
        // Add difficulty adjustment entirely to the tee shot
        if (isTeeShot) {
            expectedStart += getHoleAdjustment(courseRating, courseSlope, coursePar, holeIndex)
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
}
