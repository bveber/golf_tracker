package com.golftracker.util

import android.content.Context
import com.golftracker.R
import com.golftracker.data.model.ApproachLie
import com.golftracker.data.model.PenaltyType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import com.golftracker.data.entity.HoleStat
import com.golftracker.data.entity.Shot
import com.golftracker.data.entity.Putt
import com.golftracker.data.entity.Penalty
import com.golftracker.data.model.ShotOutcome

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
    val penalties: Double = 0.0,
    val offTeeExpected: Double? = null,
    val shotSgs: List<Pair<Int, Double>> = emptyList(), // Pair(shotNumber, SG)
    val puttSgs: List<Pair<Int, Double>> = emptyList()   // Pair(puttNumber, SG)
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
        if (isTeeShot) return row.tee ?: row.fairway ?: row.rough
        
        return when (lie) {
            ApproachLie.TEE -> row.tee ?: row.fairway ?: row.rough
            ApproachLie.FAIRWAY -> row.fairway ?: row.tee ?: row.rough
            ApproachLie.ROUGH -> row.rough ?: row.fairway ?: row.tee
            ApproachLie.SAND -> row.sand ?: row.rough ?: row.fairway
            ApproachLie.OTHER -> row.recovery ?: ((row.rough ?: row.fairway ?: 0.0) + 0.5)
            null -> row.fairway ?: row.tee ?: row.rough
        }
    }

    /**
     * Calculate the total adjustment needed to align the baseline with the given course rating.
     * New logic: Anchored to Course Par.
     *
     * @param courseRating USGA Course Rating.
     * @param coursePar Total course par (usually 70-72).
     * @return The total adjustment in strokes for the 18-hole round.
     */
    fun calculateCourseAdjustment(
        courseRating: Double,
        coursePar: Int
    ): Double {
        return courseRating - coursePar.toDouble()
    }

    /**
     * Calculate Hole Difficulty Adjustment to apply to the Tee shot.
     * This distributes the total course adjustment across the holes based on handicap.
     *
     * Logic: Hardest holes (Index 1) receive the most positive shift (largest boost or smallest penalty).
     * Easiest holes (Index 18) receive the most negative shift.
     *
     * @param totalCourseAdjustment Result from calculateCourseAdjustment (Rating - Par).
     * @param holeIndex Handicap index of the specific hole (1 = hardest, 18 = easiest).
     * @param holeCount Number of holes in the round (usually 18).
     * @return The adjustment value for this specific hole in strokes.
     */
    fun getHoleAdjustment(totalCourseAdjustment: Double, holeIndex: Int?, holeCount: Int = 18): Double {
        val validIndex = holeIndex ?: 9
        val avgIndex = if (holeCount == 9) 5.0 else 9.5
        
        // Distribute the adjustment based on handicap index.
        // Hardest holes (Index 1) get the most positive shift (largest boost or smallest penalty).
        // This logic ensures sign(holeAdj) == sign(totalCourseAdjustment) and preserves the spread.
        val avgAdj = totalCourseAdjustment / holeCount
        val spreadFactor = Math.abs(avgAdj) / 9.0 // Ensures adjustment never flips sign
        
        return avgAdj + spreadFactor * (avgIndex - validIndex.toDouble())
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
        holeAdjustment: Double = 0.0
    ): Double {
        val expectedStart = getExpectedStrokes(startDistanceYs, startLie, isTeeShot)
        
        val expectedEnd = if (endDistanceYs == 0 && endDistanceFeetOnGreen == null) {
            0.0 // Holed out
        } else if (endDistanceFeetOnGreen != null) {
            getExpectedPutts(endDistanceFeetOnGreen)
        } else {
            getExpectedStrokes(endDistanceYs, endLie, false)
        }
        
        // Correct: Apply hole adjustment to the final SG result for the tee shot only.
        val sg = expectedStart - expectedEnd - 1.0 - penaltyStrokes
        return if (isTeeShot) sg + holeAdjustment else sg
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
        penalties: List<com.golftracker.data.entity.Penalty>,
        stat: com.golftracker.data.entity.HoleStat
    ): HoleSgBreakdown {
        var offTee = 0.0
        var offTeeExpected: Double? = null
        var approach = 0.0
        var aroundGreen = 0.0
        var putting = 0.0
        val shotSgs = mutableListOf<Pair<Int, Double>>()
        val puttSgs = mutableListOf<Pair<Int, Double>>()

        val sortedShots = interpolateShotDistances(shots.sortedBy { it.shotNumber }, holeYardage, par, putts, stat)
        val sortedPutts = putts.sortedBy { it.puttNumber }

        // 1. TEE SHOT (Par 4/5)
        // If tracked shots exist, we'll handle the tee shot in the "Shots" loop below to avoid double counting.
        // CHANGE: Only run heuristic if Shot 1 (the drive) is NOT among the tracked shots.
        // CHANGE: Check for OB penalty first. If Shot 1 is OB, it's a fixed cost.
        val hasObPenaltyOnShot1 = penalties.any { it.shotNumber == 1 && (it.type == PenaltyType.OB || it.type == PenaltyType.LOST_BALL) }
        if (par > 3 && sortedShots.none { it.shotNumber == 1 } && (stat.score > 0 || stat.teeOutcome != null || stat.teeShotDistance != null)) {
            var endDist = 0
            var endLie: ApproachLie? = null
            var greenFeet: Float? = null
            var hasEnd = false

            if (hasObPenaltyOnShot1) {
                endDist = holeYardage
                endLie = ApproachLie.TEE
                hasEnd = true
            } else if (sortedShots.isNotEmpty()) {
                val firstTracked = sortedShots.first()
                endDist = firstTracked.distanceToPin ?: (holeYardage - (stat.teeShotDistance ?: 0)).coerceAtLeast(0)
                endLie = if (firstTracked.isRecovery) ApproachLie.OTHER else firstTracked.lie
                hasEnd = true
            } else if (stat.chips > 0 || stat.sandShots > 0) {
                endDist = stat.chipDistance ?: 15
                endLie = if (stat.sandShots > 0) ApproachLie.SAND else (stat.chipLie ?: ApproachLie.ROUGH)
                hasEnd = true
            } else if (sortedPutts.isNotEmpty()) {
                greenFeet = sortedPutts.first().distance
                hasEnd = true
            } else if (stat.teeShotDistance != null) {
                endDist = ShotDistanceCalculator.deriveEndDistance(holeYardage, stat.teeShotDistance!!, stat.teeOutcome)
                hasEnd = true
            } else if (stat.score > 0) {
                if (par == 3) {
                    endDist = 0
                    hasEnd = true
                }
            }

            if (hasEnd) {
                val hasObPenalty = penalties.any { it.shotNumber == 1 && (it.type == PenaltyType.OB || it.type == PenaltyType.LOST_BALL) }
                val actualLie = if (stat.teeInTrouble && !hasObPenalty) ApproachLie.OTHER else endLie
                val finalEndDist = if (hasObPenalty) holeYardage else endDist
                val finalEndLie = if (hasObPenalty) ApproachLie.TEE else actualLie

                offTeeExpected = getExpectedStrokes(holeYardage, ApproachLie.TEE, true)
                offTee = calculateShotSG(holeYardage, ApproachLie.TEE, true, finalEndDist, finalEndLie, greenFeet, 0, holeAdjustment)
                shotSgs.add(1 to offTee)
            } else if (stat.score > 0) {
                // Heuristic: If no shots tracked and no end state found, estimate based on score.
                val penaltyStrokes = penalties.sumOf { it.strokes }
                val fullSwings = (stat.score - stat.chips - stat.sandShots - stat.putts - penaltyStrokes).coerceAtLeast(1)
                
                // If par 4/5 and at least 2 full swings, assume drive advanced to ~65% of the hole distance
                // unless outcome suggests otherwise.
                val estimatedDriveLanding = (holeYardage * 0.65).toInt()
                val adjustedEndDist = when (stat.teeOutcome) {
                    ShotOutcome.SHORT -> holeYardage - (estimatedDriveLanding * 0.7).toInt() 
                    ShotOutcome.LONG -> holeYardage - (estimatedDriveLanding * 1.2).toInt()
                    else -> holeYardage - estimatedDriveLanding
                }.coerceAtLeast(40)
                
                offTee = calculateShotSG(holeYardage, ApproachLie.TEE, true, adjustedEndDist, null, null, 0, holeAdjustment)
                shotSgs.add(1 to offTee)
                
                // Then the remaining distance is assigned to approach
                if (fullSwings >= 2) {
                    val remainingFullSwings = fullSwings - 1
                    val approachEnd = if (stat.chips > 0 || stat.sandShots > 0) (stat.chipDistance ?: 15) else 0
                    val approachSg = calculateShotSG(adjustedEndDist, ApproachLie.FAIRWAY, false, approachEnd, null, null, 0)
                    approach = approachSg - (remainingFullSwings - 1).coerceAtLeast(0)
                    // We don't have shot numbers for these imaginary shots, but we can list them as 2, 3...
                    if (remainingFullSwings > 0) shotSgs.add(2 to approachSg)
                    for (i in 2 until remainingFullSwings + 1) {
                        shotSgs.add(i + 1 to -1.0) // Subsequent imaginary strokes cost 1.0 each
                    }
                }
            }
        }

        // 2. SHOTS (Tee & Approach)
        for (i in sortedShots.indices) {
            val shot = sortedShots[i]
            // CHANGE: Identify tee shot by its assigned shotNumber rather than index.
            val isFirstShotOfPar3 = (par == 3 && shot.shotNumber == 1)
            // CHANGE: Include all shots from the TEE on par 4/5 in Off-Tee (handles re-tees).
            val isTeeShotOfPar45 = (par > 3 && (shot.shotNumber == 1 || shot.lie == ApproachLie.TEE))
            val isTeeShot = isFirstShotOfPar3 || isTeeShotOfPar45
            
            // CRITICAL: Only apply holeAdjustment to the very first tee shot attempted.
            val currentHoleAdjustment = if (isTeeShot && shotSgs.none { it.first < shot.shotNumber }) holeAdjustment else 0.0
            
            val startDist = shot.distanceToPin ?: if (isTeeShot) holeYardage else continue
            val startLie = if (isTeeShot) ApproachLie.TEE else if (shot.isRecovery) ApproachLie.OTHER else shot.lie ?: ApproachLie.FAIRWAY

            var endDist = 0
            var endLie: ApproachLie? = null
            var greenFeet: Float? = null
            var hasEnd = false

            if (i + 1 < sortedShots.size) {
                val nextShot = sortedShots[i+1]
                endDist = nextShot.distanceToPin ?: 0
                // Use OTHER if the next shot is a recovery
                endLie = if (nextShot.isRecovery) ApproachLie.OTHER else nextShot.lie
                hasEnd = true
            } else if (stat.chips > 0 || stat.sandShots > 0) {
                endDist = stat.chipDistance ?: 15
                endLie = if (stat.sandShots > 0) ApproachLie.SAND else (stat.chipLie ?: ApproachLie.ROUGH)
                hasEnd = true
            } else if (sortedPutts.isNotEmpty()) {
                greenFeet = sortedPutts.first().distance
                hasEnd = true
            } else if (stat.score > 0) {
                // CHANGE: Do NOT assume a tee shot holed out just because score > 0.
                // Keep hasEnd = false for tee shots (including re-tees) if no tracked distance exists.
                // This ensures SG stays at -2.0 (Shot 1 + Penalty) until the re-tee is tracked.
                if (!isTeeShot) {
                    endDist = 0
                    hasEnd = true
                }
            }

            if (!hasEnd && shot.distanceTraveled != null) {
                endDist = ShotDistanceCalculator.deriveEndDistance(startDist, shot.distanceTraveled!!, shot.outcome)
                hasEnd = true
            }

            if (hasEnd) {
                if (isTeeShotOfPar45) {
                    offTeeExpected = getExpectedStrokes(startDist, startLie, isTeeShot)
                }
                
                val hasObPenalty = penalties.any { it.shotNumber == shot.shotNumber && (it.type == PenaltyType.OB || it.type == PenaltyType.LOST_BALL) }

                // CHANGE: If it's a tee shot and the player is "in trouble", the end lie is OTHER (Recovery).
                // EXCEPT: If it hit OB, we ignore "in trouble". 
                // ALSO: Only apply stat.teeInTrouble to the first shot (Shot 1); re-tees use shot.isRecovery.
                val effectiveEndLie = if (isTeeShotOfPar45 && shot.shotNumber == 1 && stat.teeInTrouble && !hasObPenalty) ApproachLie.OTHER else endLie
                
                // CHANGE: If OB, force end distance/lie to match start to ensure SG = -1.0 (before penalty).
                val finalEndDist = if (hasObPenalty) startDist else endDist
                val finalEndLie = if (hasObPenalty) startLie else effectiveEndLie

                val sg = calculateShotSG(startDist, startLie, isTeeShot, finalEndDist, finalEndLie, greenFeet, 0, currentHoleAdjustment)
                
                // CRITICAL: Only the very first recorded shot on a Par 4/5 is "Off Tee".
                // All other full swings/shots (including re-tees) are "Approach".
                if (isTeeShotOfPar45 && shot.shotNumber == 1) {
                    offTee += sg
                } else {
                    approach += sg
                }
                shotSgs.add(shot.shotNumber to sg)
            }
        }

        // 3. ATTRIBUTED PENALTIES
        var attributedPenaltyTotal = 0.0
        for (penalty in penalties) {
            if (penalty.shotNumber != null) {
                val penaltyValue = penalty.strokes.toDouble()
                val shotIndex = shotSgs.indexOfFirst { it.first == penalty.shotNumber }
                if (shotIndex != -1) {
                    // Update the specific shot's SG
                    val currentSg = shotSgs[shotIndex].second
                    shotSgs[shotIndex] = penalty.shotNumber to (currentSg - penaltyValue)
                    
                    // Update components
                    // CHANGE: On Par 4/5, only attribute to Off-Tee if it's the 1st shot.
                    val isTeePenalty = par > 3 && penalty.shotNumber == 1
                    if (isTeePenalty) {
                        offTee -= penaltyValue
                    } else {
                        approach -= penaltyValue
                    }
                    attributedPenaltyTotal += penaltyValue
                }
            }
        }

        // 4. SHORT GAME
        if (stat.chips > 0 || stat.sandShots > 0) {
            val startDist = stat.chipDistance ?: 15
            val startLie = if (stat.sandShots > 0) ApproachLie.SAND else (stat.chipLie ?: ApproachLie.ROUGH)
            var greenFeet: Float? = null
            if (sortedPutts.isNotEmpty()) {
                greenFeet = sortedPutts.first().distance 
            }
            val sg = calculateShotSG(startDist, startLie, false, 0, ApproachLie.FAIRWAY, greenFeet, 0)
            
            // Subtract extra strokes if there were multiple chips/sand shots.
            // calculateShotSG already subtracted 1.0 for the first stroke.
            val extraStrokes = (stat.chips + stat.sandShots - 1).coerceAtLeast(0)
            aroundGreen = sg - extraStrokes
        }

        // 4. PENALTY ATTRIBUTION SHIFT (for Recovery Shots)
        for (i in sortedShots.indices) {
            val shot = sortedShots[i]
            if (shot.isRecovery && shot.penaltyAttribution > 0.0) {
                val attribution = shot.penaltyAttribution
                
                // Find and adjust current shot SG in the list
                val currentIndex = shotSgs.indexOfFirst { it.first == shot.shotNumber }
                if (currentIndex != -1) {
                    val baseSg = shotSgs[currentIndex].second
                    
                    // Apply attribution to the recovery shot (improving its SG)
                    shotSgs[currentIndex] = shot.shotNumber to (baseSg + attribution)

                    // Update components for CURRENT recovery shot
                    // CHANGE: Use shotNumber to determine component
                    // CHANGE: Use shotNumber to determine component. ONLY Shot 1 is Off-Tee.
                    if (par > 3 && shot.shotNumber == 1) offTee += attribution else approach += attribution
                    
                    // Find and adjust PREVIOUS shot SG (penalizing it)
                    val prevShotNumber = shot.shotNumber - 1
                    val prevIndex = shotSgs.indexOfFirst { it.first == prevShotNumber }
                    if (prevIndex != -1) {
                        val prevRawSg = shotSgs[prevIndex].second
                        shotSgs[prevIndex] = prevShotNumber to (prevRawSg - attribution)
                        
                        // Update components for PREVIOUS shot. ONLY Shot 1 is Off-Tee.
                        if (par > 3 && prevShotNumber == 1) offTee -= attribution else approach -= attribution
                    }
                }
            }
        }

        // 5. PUTTING
        for (i in sortedPutts.indices) {
            val putt = sortedPutts[i]
            val nextDist = if (i + 1 < sortedPutts.size) sortedPutts[i+1].distance else null
            val sg = calculatePuttSG(putt.distance ?: 0f, putt.made, nextDist)
            putting += sg
            puttSgs.add(putt.puttNumber to sg)
        }

        val totalPenaltyStrokes = penalties.sumOf { it.strokes }.toDouble()
        val unattributedPenalties = (totalPenaltyStrokes - attributedPenaltyTotal).coerceAtLeast(0.0)

        return HoleSgBreakdown(offTee, approach, aroundGreen, putting, unattributedPenalties, offTeeExpected, shotSgs, puttSgs)
    }

    private fun interpolateShotDistances(
        shots: List<Shot>,
        holeYardage: Int,
        par: Int,
        putts: List<Putt>,
        stat: HoleStat
    ): List<Shot> {
        if (shots.isEmpty()) return emptyList()

        val result = shots.toMutableList()
        
        // 1. First Pass: Propagate known distanceToPin backwards.
        // We work from the final hole state (hole/putt/chip) back to the tee.
        for (i in result.indices.reversed()) {
            if (result[i].distanceToPin != null) continue
            
            val nextDist = if (i + 1 < result.size) {
                result[i+1].distanceToPin
            } else {
                if (stat.chips > 0 || stat.sandShots > 0) {
                    stat.chipDistance ?: 15
                } else if (putts.isNotEmpty()) {
                    (putts.first().distance?.toInt() ?: 15) / 3
                } else {
                    0
                }
            }

            // If we have nextDist and THIS shot has distanceTraveled, we can deduce start distance.
            if (nextDist != null && result[i].distanceTraveled != null) {
                result[i] = result[i].copy(distanceToPin = nextDist + result[i].distanceTraveled!!)
            }
        }

        // 2. Second Pass: Midpoint interpolation for any still-null gaps.
        for (i in result.indices) {
            if (result[i].distanceToPin == null) {
                // CHANGE: If it's the first tracked shot and it's NOT the tee shot on a par 4/5,
                // try to use the heuristic tee distance as the starting point.
                val prevDist = if (i == 0) {
                    if (par > 3 && result[0].shotNumber > 1 && stat.teeShotDistance != null && result[0].lie != ApproachLie.TEE) {
                        (holeYardage - stat.teeShotDistance!!).coerceAtLeast(0)
                    } else {
                        holeYardage
                    }
                } else {
                    result[i - 1].distanceToPin ?: holeYardage
                }
                
                var nextKnownDist: Int? = null
                for (j in i + 1 until result.size) {
                    if (result[j].distanceToPin != null) {
                        nextKnownDist = result[j].distanceToPin
                        break
                    }
                }
                
                if (nextKnownDist == null) {
                    nextKnownDist = if (stat.chips > 0 || stat.sandShots > 0) {
                        stat.chipDistance ?: 15
                    } else if (putts.isNotEmpty()) {
                        (putts.first().distance?.toInt() ?: 15) / 3
                    } else {
                        0
                    }
                }
                
                result[i] = result[i].copy(distanceToPin = (prevDist + nextKnownDist) / 2)
            }
        }
        
        return result
    }
}
