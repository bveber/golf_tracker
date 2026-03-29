package com.golftracker.data.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.content.Context
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.golftracker.util.StrokesGainedCalculator
import com.golftracker.data.model.ApproachLie

/**
 * Seeds the database with Pebble Beach Golf Links on first creation.
 *
 * Data sourced from official Pebble Beach and PGA Tour scorecards.
 * Tee sets: Black, Blue, White, Red with per-hole yardages.
 */
class SeedDataCallback(private val context: Context) : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        seedPebbleBeach(db)
    }

    private fun seedPebbleBeach(db: SupportSQLiteDatabase) {
        // ── Course ──────────────────────────────────────────────────
        db.execSQL(
            """INSERT INTO courses (id, name, city, state, holeCount) 
               VALUES (1, 'Pebble Beach Golf Links', 'Pebble Beach', 'CA', 18)"""
        )

        // ── Tee Sets ────────────────────────────────────────────────
        //                          id, course_id, name, slope, rating
        val teeSets = listOf(
            arrayOf(1, 1, "Black", 145, 75.5),
            arrayOf(2, 1, "Blue",  143, 74.7),
            arrayOf(3, 1, "White", 132, 71.3),
            arrayOf(4, 1, "Red",   124, 67.6)
        )
        for (ts in teeSets) {
            db.execSQL(
                """INSERT INTO tee_sets (id, course_id, name, slope, rating) 
                   VALUES (${ts[0]}, ${ts[1]}, '${ts[2]}', ${ts[3]}, ${ts[4]})"""
            )
        }

        // ── Holes ───────────────────────────────────────────────────
        for (h in PEBBLE_HOLES) {
            db.execSQL(
                """INSERT INTO holes (id, course_id, hole_number, par, handicap_index) 
                   VALUES (${h[0]}, 1, ${h[0]}, ${h[1]}, ${h[2]})"""
            )
        }

        // ── Hole–Tee Yardages ───────────────────────────────────────
        // Per-hole yardages for each tee set: [Black, Blue, White, Red]
        val yardages = listOf(
            intArrayOf(381, 376, 331, 309),  // Hole 1
            intArrayOf(516, 502, 427, 359),  // Hole 2
            intArrayOf(404, 374, 330, 283),  // Hole 3
            intArrayOf(331, 327, 295, 256),  // Hole 4
            intArrayOf(195, 187, 129, 113),  // Hole 5
            intArrayOf(523, 500, 466, 387),  // Hole 6
            intArrayOf(106, 106, 97,  91),   // Hole 7
            intArrayOf(428, 416, 369, 351),  // Hole 8
            intArrayOf(504, 462, 431, 333),  // Hole 9
            intArrayOf(446, 430, 407, 301),  // Hole 10
            intArrayOf(390, 373, 339, 301),  // Hole 11
            intArrayOf(202, 201, 179, 165),  // Hole 12
            intArrayOf(445, 393, 368, 288),  // Hole 13
            intArrayOf(580, 572, 548, 434),  // Hole 14
            intArrayOf(397, 396, 349, 312),  // Hole 15
            intArrayOf(403, 401, 376, 310),  // Hole 16
            intArrayOf(178, 178, 166, 150),  // Hole 17
            intArrayOf(543, 543, 509, 455)   // Hole 18
        )

        var yardageId = 1
        for (holeIdx in yardages.indices) {
            val holeId = holeIdx + 1
            for (teeIdx in yardages[holeIdx].indices) {
                val teeSetId = teeIdx + 1
                val yds = yardages[holeIdx][teeIdx]
                db.execSQL(
                    """INSERT INTO hole_tee_yardages (id, hole_id, tee_set_id, yardage) 
                       VALUES ($yardageId, $holeId, $teeSetId, $yds)"""
                )
                yardageId++
            }
        }


        // ── Clubs ───────────────────────────────────────────────────
        // Default set of clubs
        val clubs = listOf(
            // Driver & Woods
            Triple("Driver", "DRIVER", 300),
            Triple("3 Wood", "WOOD", 260),
            Triple("5 Wood", "WOOD", 240),
            
            // Irons
            Triple("3 Iron", "IRON", 220),
            Triple("4 Iron", "IRON", 210),
            Triple("5 Iron", "IRON", 200),
            Triple("6 Iron", "IRON", 190),
            Triple("7 Iron", "IRON", 180),
            Triple("8 Iron", "IRON", 170),
            Triple("9 Iron", "IRON", 160),
            
            // Wedges
            Triple("Pitching Wedge", "WEDGE", 150),
            Triple("Gap Wedge", "WEDGE", 135),
            Triple("Sand Wedge", "WEDGE", 120),
            Triple("Lob Wedge", "WEDGE", 100),
            
            // Putter
            Triple("Putter", "PUTTER", 30)
        )

        var sortOrder = 1
        for ((name, type, dist) in clubs) {
            db.execSQL(
                """INSERT INTO clubs (name, type, isRetired, sortOrder, stockDistance) 
                   VALUES ('$name', '$type', 0, $sortOrder, $dist)"""
            )
            sortOrder++
        }
        
        seedSampleRound(db, yardages, clubs)
    }

    private fun seedSampleRound(db: SupportSQLiteDatabase, yardages: List<IntArray>, clubs: List<Triple<String, String, Int>>) {
        val sgCalc = StrokesGainedCalculator(context)
        val roundId = 1
        val date = System.currentTimeMillis() - 86400000 // Yesterday
        
        db.execSQL("INSERT INTO rounds (id, course_id, tee_set_id, date, is_finalized, notes, total_holes, start_hole, is_practice) VALUES ($roundId, 1, 1, $date, 1, '', 18, 1, 0)")

        val random = java.util.Random()
        var holeStatId = 1
        var puttId = 1
        var shotId = 1

        for (holeIdx in PEBBLE_HOLES.indices) {
            val h = PEBBLE_HOLES[holeIdx]
            val holeId = h[0]
            val par = h[1]
            val holeYardage = yardages[holeIdx][0]

            var teeShotDist: Int? = null
            var teeOutcome: String? = null
            var teeClubId: Int? = null
            var teeInTrouble = false
            var currentDistanceToPin = holeYardage

            if (par > 3) {
                teeClubId = 1 // Driver
                val isFairway = random.nextDouble() < 0.40
                teeShotDist = 250 + random.nextInt(60) 
                currentDistanceToPin -= teeShotDist
                if (isFairway) {
                    teeOutcome = "ON_TARGET"
                } else {
                    teeOutcome = if (random.nextBoolean()) "MISS_LEFT" else "MISS_RIGHT"
                    if (random.nextDouble() < 0.30) teeInTrouble = true
                }
            }

            val approachShotDistanceToInsert = if (par == 3) holeYardage else currentDistanceToPin
            val distForGirProb = if (par == 5) 100 else approachShotDistanceToInsert
            val maxGirProb = 0.85
            val minGirProb = 0.05
            val distanceFactor = (distForGirProb - 80).coerceAtLeast(0) / 100.0
            val girProb = Math.max(minGirProb, maxGirProb - (distanceFactor * 0.50))
            val isGir = random.nextDouble() < girProb
            
            val approachOutcome = if (isGir) "ON_TARGET" else {
                val missTypes = arrayOf("MISS_LEFT", "MISS_RIGHT", "MISS_SHORT", "MISS_LONG")
                missTypes[random.nextInt(missTypes.size)]
            }
            
            val approachClubId = when {
                 distForGirProb > 230 -> 2
                 distForGirProb > 210 -> 4
                 distForGirProb > 180 -> 6
                 distForGirProb > 150 -> 8
                 distForGirProb > 130 -> 10
                 else -> 12
            }
            
            var putts = 0
            var chips = 0
            var score = 0
            var initialPuttDistance = 0f
            
            if (isGir) {
                initialPuttDistance = if (approachOutcome == "ON_TARGET") 5f + random.nextInt(26).toFloat() else 30f + random.nextInt(36).toFloat()
                val r = random.nextDouble()
                putts = when {
                    r < 0.20 -> 1
                    r < 0.90 -> 2
                    else -> 3
                }
                score = (par - 2) + putts
            } else {
                val isUpAndDown = random.nextDouble() < 0.50
                chips = 1 
                if (isUpAndDown) {
                    putts = 1
                    score = par
                    initialPuttDistance = 1f + random.nextInt(10).toFloat()
                } else {
                    putts = 2
                    score = par + 1
                    initialPuttDistance = 10f + random.nextInt(16).toFloat()
                }
            }

            seedHoleStat(
                db, holeStatId, roundId, holeId, score, putts, chips, 
                teeOutcome, teeShotDist, teeClubId, teeInTrouble, 
                isGir, approachOutcome, approachClubId, approachShotDistanceToInsert
            )

            val sgPutting = seedPutts(db, puttId, holeStatId, putts, initialPuttDistance, sgCalc, random)
            puttId += putts
            
            val (nextShotId, sgOffTee, sgApproach, sgAroundGreen) = seedShots(
                db, shotId, holeStatId, par, holeYardage, teeShotDist, teeOutcome, teeInTrouble,
                isGir, approachOutcome, approachClubId, chips, initialPuttDistance, sgCalc, h, random
            )
            shotId = nextShotId

            val totalHoleSg = sgOffTee + sgApproach + sgAroundGreen + sgPutting
            db.execSQL("UPDATE hole_stats SET strokes_gained = $totalHoleSg, sg_off_tee = $sgOffTee, sg_approach = $sgApproach, sg_around_green = $sgAroundGreen, sg_putting = $sgPutting, chip_lie = ${if (sgAroundGreen != 0.0) "'ROUGH'" else "NULL"} WHERE id = $holeStatId")

            holeStatId++
        }
    }

    /**
     * Seeds the main HoleStat record.
     */
    private fun seedHoleStat(
        db: SupportSQLiteDatabase, holeStatId: Int, roundId: Int, holeId: Int,
        score: Int, putts: Int, chips: Int,
        teeOutcome: String?, teeShotDist: Int?, teeClubId: Int?, teeInTrouble: Boolean,
        isGir: Boolean, approachOutcome: String?, approachClubId: Int?, approachShotDistance: Int?
    ) {
        val outcomeStr = if (teeOutcome != null) "'${teeOutcome}'" else "NULL"
        val approachOutcomeStr = if (approachOutcome != null) "'${approachOutcome}'" else "NULL"
        val teeDistStr = teeShotDist ?: "NULL"
        val teeClubStr = teeClubId ?: "NULL"
        val approachClubStr = approachClubId ?: "NULL"
        val approachDistStr = approachShotDistance ?: "NULL"
        val troubleStr = if (teeInTrouble) 1 else 0

        db.execSQL(
            """INSERT INTO hole_stats (id, round_id, hole_id, score, putts, chips, sand_shots, 
               tee_outcome, tee_shot_distance, tee_club_id, tee_in_trouble, gir, gir_override, near_gir, approach_outcome, approach_club_id, approach_shot_distance) 
               VALUES ($holeStatId, $roundId, $holeId, $score, $putts, $chips, 0, 
               $outcomeStr, $teeDistStr, $teeClubStr, $troubleStr, ${if(isGir) 1 else 0}, 0, 0, $approachOutcomeStr, $approachClubStr, $approachDistStr)"""
        )
    }

    /**
     * Seeds putt records for the given hole.
     * @return Total Strokes Gained for putting on this hole.
     */
    private fun seedPutts(
        db: SupportSQLiteDatabase, startPuttId: Int, holeStatId: Int,
        putts: Int, initialPuttDistance: Float, sgCalc: StrokesGainedCalculator, random: java.util.Random
    ): Double {
        var sgPutting = 0.0
        var puttId = startPuttId
        var currentPuttDistance = initialPuttDistance
        
        for (p in 1..putts) {
            val isLastPutt = (p == putts)
            val isMade = if (isLastPutt) 1 else 0
            
            var nextPuttDistance: Float? = null
            if (!isLastPutt) {
                val baseRemaining = currentPuttDistance * 0.10f
                val variance = baseRemaining * 0.5f
                val randomVariance = (random.nextFloat() * 2 * variance) - variance
                nextPuttDistance = Math.max(1f, baseRemaining + randomVariance)
            }

            val puttSg = sgCalc.calculatePuttSG(currentPuttDistance, isLastPutt, nextPuttDistance)
            sgPutting += puttSg
            
            db.execSQL(
                """INSERT INTO putts (id, hole_stat_id, putt_number, distance, made, strokes_gained) 
                   VALUES ($puttId, $holeStatId, $p, $currentPuttDistance, $isMade, $puttSg)"""
            )
            puttId++
            
            if (nextPuttDistance != null) {
                currentPuttDistance = nextPuttDistance
            }
        }
        return sgPutting
    }

    /**
     * Data class to return shot-related SG metrics and the next available shot ID.
     */
    private data class ShotData(val nextShotId: Int, val sgOffTee: Double, val sgApproach: Double, val sgAroundGreen: Double)

    /**
     * Seeds the individual shots (excluding putts) for the hole.
     * @return ShotData containing strokes gained categories and next shot ID.
     */
    private fun seedShots(
        db: SupportSQLiteDatabase, startShotId: Int, holeStatId: Int,
        par: Int, holeYardage: Int, teeShotDist: Int?, teeOutcome: String?, teeInTrouble: Boolean,
        isGir: Boolean, approachOutcome: String?, approachClubId: Int?, chips: Int,
        initialPuttDistance: Float, sgCalc: StrokesGainedCalculator, h: IntArray, random: java.util.Random
    ): ShotData {
        var shotId = startShotId
        var shotNumber = 1
        var sgOffTee = 0.0
        var sgApproach = 0.0
        var sgAroundGreen = 0.0

        if (par == 3) {
            val greenFeetTee = if (isGir) initialPuttDistance else null
            val endDistanceTee = if (isGir) 0 else 15
            val endLieTee = if (isGir) null else if (random.nextBoolean()) ApproachLie.ROUGH else ApproachLie.SAND
            
            val teeSg = sgCalc.calculateShotSG(holeYardage, ApproachLie.TEE, true, endDistanceTee, endLieTee, greenFeetTee, 0)
            sgOffTee += teeSg
            
            db.execSQL(
                """INSERT INTO shots (id, hole_stat_id, shot_number, club_id, distance_to_pin, lie, outcome, strokes_gained, is_recovery) 
                   VALUES ($shotId, $holeStatId, $shotNumber, ${approachClubId}, $holeYardage, 'TEE', '${approachOutcome}', $teeSg, 0)"""
            )
            shotId++; shotNumber++
            
            if (!isGir) {
               val chipSg = sgCalc.getExpectedStrokes(endDistanceTee, endLieTee, false) - sgCalc.getExpectedPutts(initialPuttDistance) - chips
               sgAroundGreen += chipSg
            }
        } else {
            val distRemainAfterTee = holeYardage - (teeShotDist ?: 0)
            val endLieTee = if (teeInTrouble) ApproachLie.OTHER else (if (teeOutcome == "ON_TARGET") ApproachLie.FAIRWAY else ApproachLie.ROUGH)
            
            if (par == 5) {
                val distAfterS2 = 80 + random.nextInt(40)
                val teeSg = sgCalc.calculateShotSG(holeYardage, ApproachLie.TEE, true, distRemainAfterTee, endLieTee, null, 0)
                sgOffTee += teeSg
                
                val endLieS2 = ApproachLie.FAIRWAY
                val shot2Sg = sgCalc.calculateShotSG(distRemainAfterTee, endLieTee, false, distAfterS2, endLieS2, null, 0)
                sgApproach += shot2Sg
                
                db.execSQL(
                    """INSERT INTO shots (id, hole_stat_id, shot_number, club_id, distance_to_pin, lie, outcome, strokes_gained, is_recovery) 
                       VALUES ($shotId, $holeStatId, $shotNumber, 2, $distRemainAfterTee, '${endLieTee.name}', 'ON_TARGET', $shot2Sg, 0)"""
                )
                shotId++; shotNumber++

                val greenFeetS3 = if (isGir) initialPuttDistance else null
                val endDistS3 = if (isGir) 0 else 15
                val endLieS3 = if (isGir) null else ApproachLie.ROUGH
                
                val shot3Sg = sgCalc.calculateShotSG(distAfterS2, endLieS2, false, endDistS3, endLieS3, greenFeetS3, 0)
                sgApproach += shot3Sg
                
                db.execSQL(
                    """INSERT INTO shots (id, hole_stat_id, shot_number, club_id, distance_to_pin, lie, outcome, strokes_gained, is_recovery) 
                       VALUES ($shotId, $holeStatId, $shotNumber, ${approachClubId}, $distAfterS2, '${endLieS2.name}', '${approachOutcome}', $shot3Sg, 0)"""
                )
                shotId++; shotNumber++
                
                if (!isGir) {
                   val chipSg = sgCalc.getExpectedStrokes(endDistS3, endLieS3, false) - sgCalc.getExpectedPutts(initialPuttDistance) - chips
                   sgAroundGreen += chipSg
                }
            } else {
                val teeSg = sgCalc.calculateShotSG(holeYardage, ApproachLie.TEE, true, distRemainAfterTee, endLieTee, null, 0)
                sgOffTee += teeSg
                
                val greenFeetS2 = if (isGir) initialPuttDistance else null
                val endDistS2 = if (isGir) 0 else 15
                val endLieS2 = if (isGir) null else ApproachLie.ROUGH
                
                val approachSg = sgCalc.calculateShotSG(distRemainAfterTee, endLieTee, false, endDistS2, endLieS2, greenFeetS2, 0)
                sgApproach += approachSg
                
                db.execSQL(
                    """INSERT INTO shots (id, hole_stat_id, shot_number, club_id, distance_to_pin, lie, outcome, strokes_gained, is_recovery) 
                       VALUES ($shotId, $holeStatId, $shotNumber, ${approachClubId}, $distRemainAfterTee, '${endLieTee.name}', '${approachOutcome}', $approachSg, 0)"""
                )
                shotId++; shotNumber++
                
                if (!isGir) {
                   val chipSg = sgCalc.getExpectedStrokes(endDistS2, endLieS2, false) - sgCalc.getExpectedPutts(initialPuttDistance) - chips
                   sgAroundGreen += chipSg
                }
            }
        }
        return ShotData(shotId, sgOffTee, sgApproach, sgAroundGreen)
    }

    companion object {
        // [holeNumber, par, handicapIndex]
        val PEBBLE_HOLES = listOf(
            intArrayOf(1,  4, 8),
            intArrayOf(2,  5, 10),
            intArrayOf(3,  4, 12),
            intArrayOf(4,  4, 16),
            intArrayOf(5,  3, 14),
            intArrayOf(6,  5, 2),
            intArrayOf(7,  3, 18),
            intArrayOf(8,  4, 6),
            intArrayOf(9,  4, 4),
            intArrayOf(10, 4, 7),
            intArrayOf(11, 4, 5),
            intArrayOf(12, 3, 17),
            intArrayOf(13, 4, 9),
            intArrayOf(14, 5, 1),
            intArrayOf(15, 4, 13),
            intArrayOf(16, 4, 11),
            intArrayOf(17, 3, 15),
            intArrayOf(18, 5, 3)
        )
    }
}
