package com.golftracker.data.repository

import com.golftracker.data.db.dao.RoundDao
import com.golftracker.data.model.ApproachLie
import com.golftracker.data.model.HoleStatWithHole
import com.golftracker.data.model.RoundWithDetails
import com.golftracker.data.model.ShotOutcome
import com.golftracker.util.GirCalculator
import com.golftracker.util.HandicapCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import java.util.Date
import javax.inject.Inject
import kotlin.math.sqrt

// ── Filter ──────────────────────────────────────────────────────────────

data class StatsFilter(
    val courseId: Int? = null,
    val teeSetId: Int? = null,
    val lastNRounds: Int = 0, // Default to All Rounds for dashboard
    val year: Int? = null,
    val startDate: java.util.Date? = null,
    val endDate: java.util.Date? = null,
    val drivingClubId: Int? = null,
    val approachClubId: Int? = null,
    val excludedRoundIds: Set<Int> = emptySet(),
    val includeMishits: Boolean = true
)

// ── Repository ──────────────────────────────────────────────────────────

class StatsRepository @Inject constructor(
    private val roundDao: RoundDao,
    private val courseRepository: CourseRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val sgCalculator: com.golftracker.util.StrokesGainedCalculator
) {
    fun getStatsData(): Flow<StatsData> = getFilteredStatsData(StatsFilter())

    fun getFilteredStatsData(filter: StatsFilter): Flow<StatsData> {
        return kotlinx.coroutines.flow.combine(
            roundDao.getFinalizedRoundsWithDetails(),
            courseRepository.allYardages,
            userPreferencesRepository.estimatedHandicapFlow,
            courseRepository.allHoles
        ) { allRounds, allYardages, estimatedHandicap, allHoles ->
            val parMap = allHoles.groupBy { it.courseId }.mapValues { (_, holes) -> holes.sumOf { it.par } }
            val filtered = applyFilter(allRounds, filter)
            
            // For driving: Map (TeeSetId -> HoleId -> Yardage)
            val yardageMapForDriving = allYardages.groupBy { it.teeSetId }
                .mapValues { (_, list) -> list.associate { it.holeId to it.yardage } }
            
            // For SG: Map (Pair(TeeSetId, HoleId) -> Yardage)
            val yardageMapForSg = allYardages.associateBy { it.teeSetId to it.holeId }
            
            StatsData(
                rounds = filtered,
                scoring = calculateScoringStats(filtered, estimatedHandicap),
                driving = calculateDrivingStats(filtered, yardageMapForDriving, filter.drivingClubId, filter),
                approach = calculateApproachStats(filtered, filter.approachClubId, filter),
                chipping = calculateChippingStats(filtered),
                putting = calculatePuttingStats(filtered),
                sg = calculateSgStats(filtered, yardageMapForSg, parMap, filter)
            )
        }
    }

    private fun applyFilter(rounds: List<RoundWithDetails>, filter: StatsFilter): List<RoundWithDetails> {
        var result = rounds

        // 1. Exclude specific rounds
        if (filter.excludedRoundIds.isNotEmpty()) {
            result = result.filter { it.round.id !in filter.excludedRoundIds }
        }

        // 2. Course & Tee Set
        filter.courseId?.let { cid ->
            result = result.filter { it.round.courseId == cid }
        }
        filter.teeSetId?.let { tid ->
            result = result.filter { it.round.teeSetId == tid }
        }

        // 3. Time-based filters
        filter.year?.let { y ->
            val cal = Calendar.getInstance()
            result = result.filter {
                cal.time = it.round.date
                cal.get(Calendar.YEAR) == y
            }
        }
        filter.startDate?.let { s ->
            result = result.filter { it.round.date >= s }
        }
        filter.endDate?.let { e ->
            result = result.filter { it.round.date <= e }
        }

        // 4. Last N rounds (applied last, after other filters narrow the set)
        if (filter.lastNRounds > 0) {
            result = result.take(filter.lastNRounds) // already sorted DESC by date
        }
        return result
    }

    // ── Scoring ─────────────────────────────────────────────────────────

    private fun calculateScoringStats(rounds: List<RoundWithDetails>, estimatedHandicap: Double?): ScoringStats {
        if (rounds.isEmpty()) return ScoringStats()

        val roundScores = rounds.map { r -> r.holeStats.filter { it.holeStat.score > 0 }.sumOf { it.holeStat.score } }
        val roundPars = rounds.map { r -> r.holeStats.filter { it.holeStat.score > 0 }.sumOf { it.hole.par } }
        val totalScore = roundScores.sum()
        val totalPar = roundPars.sum()
        val roundToPars = roundScores.zip(roundPars) { s, p -> if (s > 0) s - p else 0 }
        val bestToPar = roundToPars.minOrNull() ?: 0
        val worstToPar = roundToPars.maxOrNull() ?: 0

        val allHoles = rounds.flatMap { it.holeStats }.filter { it.holeStat.score > 0 }
        val totalHolesPlayed = allHoles.size
        // Normalization factor: multiply by 18, divide by total holes played.
        // If no holes, we use 18 as a safety to avoid div by zero (but rounds.isEmpty already handles this).
        val normFactor = if (totalHolesPlayed > 0) 18.0 / totalHolesPlayed else 1.0

        val eagles = allHoles.count { it.holeStat.score <= it.hole.par - 2 }
        val birdies = allHoles.count { it.holeStat.score == it.hole.par - 1 }
        val pars = allHoles.count { it.holeStat.score == it.hole.par }
        val bogeys = allHoles.count { it.holeStat.score == it.hole.par + 1 }
        val doubles = allHoles.count { it.holeStat.score == it.hole.par + 2 }
        val worse = allHoles.count { it.holeStat.score > it.hole.par + 2 }

        val handicap = HandicapCalculator.calculateHandicapIndex(rounds) ?: estimatedHandicap

        // Normalize per-round scores to 18-hole equivalent
        val normalizedScores = rounds.map { r ->
            val playedHoles = r.holeStats.count { it.holeStat.score > 0 }
            val score = r.holeStats.filter { it.holeStat.score > 0 }.sumOf { it.holeStat.score }
            if (playedHoles > 0) (score.toDouble() / playedHoles) * 18 else score.toDouble()
        }
        val normalizedToPars = rounds.map { r ->
            val playedHoles = r.holeStats.count { it.holeStat.score > 0 }
            val toPar = r.holeStats.filter { it.holeStat.score > 0 }.sumOf { it.holeStat.score - it.hole.par }
            if (playedHoles > 0) (toPar.toDouble() / playedHoles) * 18 else toPar.toDouble()
        }

        val avgScore = (totalScore.toDouble() / totalHolesPlayed) * 18
        val avgToPar = ((totalScore - totalPar).toDouble() / totalHolesPlayed) * 18

        val diffs = HandicapCalculator.calculateDifferentials(rounds)
        val diffMap = diffs.associateBy { it.roundId }
        val trend = rounds.mapIndexed { index, r ->
            val playedHoles = r.holeStats.count { it.holeStat.score > 0 }
            val toPar = r.holeStats.filter { it.holeStat.score > 0 }.sumOf { it.holeStat.score - it.hole.par }
            val normToPar = if (playedHoles > 0) (toPar.toDouble() / playedHoles) * 18 else toPar.toDouble()
            RoundScoreSummary(
                roundId = r.round.id,
                date = r.round.date,
                toPar = normToPar.toInt(),
                differential = diffMap[r.round.id]?.value,
                courseName = r.course.name,
                totalHoles = r.round.totalHoles
            )
        }.sortedBy { it.date }

        val scoringByPar = allHoles.groupBy { it.hole.par }.mapValues { (par, holes) ->
            ParBreakdown(
                label = "Par $par",
                avgScore = holes.averageOf { it.holeStat.score.toDouble() },
                avgToPar = holes.averageOf { (it.holeStat.score - it.hole.par).toDouble() },
                sampleCount = holes.size
            )
        }

        return ScoringStats(
            avgScore = avgScore,
            scoreMoE = calculateMeanMoE(normalizedScores),
            avgToPar = avgToPar,
            toParMoE = calculateMeanMoE(normalizedToPars),
            roundsPlayed = rounds.size,
            handicapIndex = handicap,
            bestRoundToPar = bestToPar,
            worstRoundToPar = worstToPar,
            eagles = eagles,
            birdies = birdies,
            pars = pars,
            bogeyCount = bogeys,
            doubles = doubles,
            worse = worse,
            scoreDistribution = normalizedScores,
            trend = trend,
            byPar = scoringByPar
        )
    }

    // ── Driving ─────────────────────────────────────────────────────────

    private fun calculateDrivingStats(
        rounds: List<RoundWithDetails>, 
        yardageMap: Map<Int, Map<Int, Int>>, // TeeSetId -> HoleId -> Yardage
        clubIdFilter: Int? = null,
        filter: StatsFilter
    ): DrivingStats {
        val allDrivingHoles = rounds.flatMap { r -> 
            r.holeStats.map { h -> Triple(h, r.round.teeSetId, r.round.courseId) }
        }.filter { (h, _, _) -> 
            h.hole.par > 3 && (h.holeStat.teeOutcome != null || h.holeStat.score > 0) 
        }

        if (allDrivingHoles.isEmpty()) return DrivingStats()

        // Per-club stats (computed from ALL holes, regardless of filter)
        val perClub = mutableMapOf<Int, ClubStats>()
        val holesWithClub = allDrivingHoles.filter { (h, _, _) -> h.holeStat.teeClubId != null && h.holeStat.teeOutcome != null }
        holesWithClub.groupBy { (h, _, _) -> h.holeStat.teeClubId!! }.forEach { (cid, triples) ->
            perClub[cid] = calculateClubStats(triples.map { it.first }, isTeeShot = true)
        }

        // Apply club filter for the main stats
        val drivingTriples = if (clubIdFilter != null) {
            allDrivingHoles.filter { (h, _, _) -> h.holeStat.teeClubId == clubIdFilter }
        } else allDrivingHoles

        if (drivingTriples.isEmpty()) return DrivingStats(perClubStats = perClub, selectedClubId = clubIdFilter)

        val drivingHoles = drivingTriples.map { it.first }

        // Build a lookup from holeStat.id → (holeNumber, roundDate, courseName) for tooltip metadata
        val holeStatContext: Map<Int, Triple<Int, Long, String>> = rounds.flatMap { r ->
            r.holeStats.map { h -> h.holeStat.id to Triple(h.hole.holeNumber, r.round.date.time, r.course.name) }
        }.toMap()

        // Extract raw dispersion points from both HoleStat (TEE fields) and Shot table
        val drivingShotsWithDispersion = drivingHoles.flatMap { h ->
            val points = mutableListOf<Pair<ApproachLie, DispersionPoint>>()
            val ctx = holeStatContext[h.holeStat.id]

            // 1. From HoleStat
            val stat = h.holeStat
            if (stat.teeOutcome != null &&
                (stat.teeDispersionLeft != null || stat.teeDispersionRight != null || stat.teeDispersionShort != null || stat.teeDispersionLong != null)) {

                val isMishit = stat.teeMishit
                points.add(ApproachLie.TEE to DispersionPoint(stat.teeDispersionLeft, stat.teeDispersionRight, stat.teeDispersionShort, stat.teeDispersionLong, isMishit,
                    holeNumber = h.hole.holeNumber, roundDate = ctx?.second, courseName = ctx?.third))
            }

            // 2. From Shot table (if they aren't duplicates of the HoleStat entry managed by GPS)
            // For driving, we typically care about the first shot on par 4/5.
            // However, GPS VM logic saves the TEE shot's dispersion ONLY to HoleStat (see GpsViewModel.kt:729)
            // But if there are secondary drives, they might be in Shot table.
            h.shots.filter { it.shotNumber > 1 && it.lie == ApproachLie.TEE }.forEach { shot ->
                if (shot.outcome != null &&
                    (shot.dispersionLeft != null || shot.dispersionRight != null || shot.dispersionShort != null || shot.dispersionLong != null)) {
                    points.add(ApproachLie.TEE to DispersionPoint(shot.dispersionLeft, shot.dispersionRight, shot.dispersionShort, shot.dispersionLong, shot.isMishit,
                        holeNumber = h.hole.holeNumber, roundDate = ctx?.second, courseName = ctx?.third))
                }
            }
            
            if (!filter.includeMishits) {
                points.removeAll { it.second.isMishit }
            }
            
            points
        }

        val pointsByLie = drivingShotsWithDispersion.groupBy({ it.first }, { it.second })

        val avgLateralMiss = if (drivingShotsWithDispersion.isNotEmpty()) {
            drivingShotsWithDispersion.sumOf { (it.second.right?.toDouble() ?: 0.0) - (it.second.left?.toDouble() ?: 0.0) } / drivingShotsWithDispersion.size
        } else 0.0
        val avgDistanceMiss = if (drivingShotsWithDispersion.isNotEmpty()) {
            drivingShotsWithDispersion.sumOf { (it.second.long?.toDouble() ?: 0.0) - (it.second.short?.toDouble() ?: 0.0) } / drivingShotsWithDispersion.size
        } else 0.0
        

        // Filter everything below for accuracy/distance based on includeMishits toggle
        val effectiveDrivingHoles = if (!filter.includeMishits) {
            drivingHoles.filter { !it.holeStat.teeMishit }
        } else drivingHoles
        
        val effectiveTeeShots = effectiveDrivingHoles.mapNotNull { it.holeStat.teeOutcome }
        val effectiveTotalWithOutcome = effectiveTeeShots.size.coerceAtLeast(1)

        val fairwaysHit = effectiveTeeShots.count { it == ShotOutcome.ON_TARGET }
        val leftMisses = effectiveTeeShots.count { it == ShotOutcome.MISS_LEFT }
        val rightMisses = effectiveTeeShots.count { it == ShotOutcome.MISS_RIGHT }
        val shortMisses = effectiveTeeShots.count { it == ShotOutcome.SHORT }
        val longMisses = effectiveTeeShots.count { it == ShotOutcome.LONG }

        val troubleFreeCount = effectiveDrivingHoles.count { it.holeStat.teeOutcome != null && !it.holeStat.teeInTrouble }
        val troubleTotal = effectiveDrivingHoles.count { it.holeStat.teeOutcome != null }.coerceAtLeast(1)

        val effectiveDrivingTriples = if (!filter.includeMishits) {
            drivingTriples.filter { !it.first.holeStat.teeMishit }
        } else drivingTriples

        // Calculate distances (explicit OR inferred)
        val teeDistances = effectiveDrivingTriples.mapNotNull { (h, teeSetId, _) ->
            if (h.holeStat.teeShotDistance != null && h.holeStat.teeShotDistance > 0) {
                 h.holeStat.teeShotDistance
            } else {
                // Infer distance: Hole Yardage - Approach Distance
                val holeYardage = yardageMap[teeSetId]?.get(h.hole.id)
                val approachDist = h.holeStat.approachShotDistance
                if (holeYardage != null && approachDist != null && approachDist > 0) {
                    val inferred = holeYardage - approachDist
                    if (inferred > 50) inferred else null // Sanity check: drive > 50y
                } else null
            }
        }
        
        val avgTeeDistance = if (teeDistances.isNotEmpty()) teeDistances.average() else 0.0

        val fairwaysHitPct = (fairwaysHit.toDouble() / effectiveTotalWithOutcome) * 100
        val troubleFreePct = (troubleFreeCount.toDouble() / troubleTotal) * 100

        val mishitCount = drivingHoles.count { it.holeStat.teeMishit }
        val mishitTotal = drivingHoles.count { it.holeStat.teeOutcome != null }.coerceAtLeast(1)
        val mishitPct = (mishitCount.toDouble() / mishitTotal) * 100

        // Filtered distances (excluding mishits) - always "clean" for this specific metric
        val cleanDistances = drivingTriples.filter { (h, _, _) -> !h.holeStat.teeMishit }.mapNotNull { (h, teeSetId, _) ->
            if (h.holeStat.teeShotDistance != null && h.holeStat.teeShotDistance > 0) {
                h.holeStat.teeShotDistance
            } else {
                val holeYardage = yardageMap[teeSetId]?.get(h.hole.id)
                val approachDist = h.holeStat.approachShotDistance
                if (holeYardage != null && approachDist != null && approachDist > 0) {
                    val inferred = holeYardage - approachDist
                    if (inferred > 50) inferred else null
                } else null
            }
        }
        val avgDistanceExMishits = if (cleanDistances.isNotEmpty()) cleanDistances.average() else 0.0

        val drivingByPar = effectiveDrivingHoles.groupBy { it.hole.par }.mapValues { (par, holes) ->
            val totalWithOutcome = holes.count { it.holeStat.teeOutcome != null }.coerceAtLeast(1)
            val fairways = holes.count { it.holeStat.teeOutcome == ShotOutcome.ON_TARGET }
            val distances = holes.mapNotNull { it.holeStat.teeShotDistance }.filter { it > 0 }
            ParBreakdown(
                label = "Par $par",
                fairwaysHitPct = (fairways.toDouble() / totalWithOutcome) * 100,
                avgScore = if (distances.isNotEmpty()) distances.average() else 0.0,
                sampleCount = holes.count { it.holeStat.teeOutcome != null }
            )
        }

        return DrivingStats(
            fairwaysHitPct = fairwaysHitPct,
            fairwaysHitMoE = calculateProportionMoE(fairwaysHitPct, effectiveTotalWithOutcome),
            troubleFreePct = troubleFreePct,
            troubleFreeMoE = calculateProportionMoE(troubleFreePct, troubleTotal),
            missLeftPct = (leftMisses.toDouble() / effectiveTotalWithOutcome) * 100,
            missRightPct = (rightMisses.toDouble() / effectiveTotalWithOutcome) * 100,
            missShortPct = (shortMisses.toDouble() / effectiveTotalWithOutcome) * 100,
            missLongPct = (longMisses.toDouble() / effectiveTotalWithOutcome) * 100,
            avgDistance = avgTeeDistance,
            distanceMoE = calculateMeanMoE(teeDistances.map { it.toDouble() }),
            avgDistanceExMishits = avgDistanceExMishits,
            distanceExMishitsMoE = calculateMeanMoE(cleanDistances.map { it.toDouble() }),
            mishitPct = mishitPct,
            mishitMoE = calculateProportionMoE(mishitPct, mishitTotal),
            avgLateralMiss = avgLateralMiss,
            avgDistanceMiss = avgDistanceMiss,
            totalMishits = mishitCount,
            totalDrivingHoles = effectiveDrivingHoles.size,
            perClubStats = perClub,
            selectedClubId = clubIdFilter,
            rawDispersion = RawDispersionData(
                points = pointsByLie.values.flatten(),
                pointsByLie = pointsByLie
            ),
            byPar = drivingByPar
        )
    }

    private fun calculateClubStats(holes: List<HoleStatWithHole>, isTeeShot: Boolean): ClubStats {
        val outcomes = if (isTeeShot) {
            holes.mapNotNull { it.holeStat.teeOutcome }
        } else {
            holes.mapNotNull { it.holeStat.approachOutcome }
        }
        val total = outcomes.size.coerceAtLeast(1)
        val onTarget = outcomes.count { it == ShotOutcome.ON_TARGET }
        val distances = if (isTeeShot) {
            holes.mapNotNull { it.holeStat.teeShotDistance }.filter { it > 0 }
        } else {
            holes.mapNotNull { it.holeStat.approachShotDistance }.filter { it > 0 }
        }
        return ClubStats(
            onTargetPct = (onTarget.toDouble() / total) * 100,
            missLeftPct = (outcomes.count { it == ShotOutcome.MISS_LEFT }.toDouble() / total) * 100,
            missRightPct = (outcomes.count { it == ShotOutcome.MISS_RIGHT }.toDouble() / total) * 100,
            missShortPct = (outcomes.count { it == ShotOutcome.SHORT }.toDouble() / total) * 100,
            missLongPct = (outcomes.count { it == ShotOutcome.LONG }.toDouble() / total) * 100,
            avgDistance = if (distances.isNotEmpty()) distances.average() else 0.0,
            sampleCount = holes.size
        )
    }

    // ── Approach ─────────────────────────────────────────────────────────

    private fun calculateApproachStats(
        rounds: List<RoundWithDetails>, 
        clubIdFilter: Int? = null,
        filter: StatsFilter
    ): ApproachStats {
        val allHoles = rounds.flatMap { it.holeStats }.filter { it.holeStat.score > 0 || it.shots.isNotEmpty() }
        if (allHoles.isEmpty()) return ApproachStats()

        // Build a lookup from holeStat.id → (holeNumber, roundDate, courseName) for tooltip metadata
        val holeStatContext: Map<Int, Triple<Int, Long, String>> = rounds.flatMap { r ->
            r.holeStats.map { h -> h.holeStat.id to Triple(h.hole.holeNumber, r.round.date.time, r.course.name) }
        }.toMap()

        fun isGir(h: HoleStatWithHole): Boolean {
            return GirCalculator.isGir(h.holeStat.score, h.hole.par, h.holeStat.putts)
        }

        fun isNearGir(h: HoleStatWithHole): Boolean {
        if (isGir(h)) return true
        return (h.holeStat.score - h.holeStat.putts == h.hole.par - 1) && 
               (h.holeStat.chips >= 1 && h.holeStat.sandShots == 0) &&
               (h.holeStat.chipDistance ?: 15) <= 15 &&
               !h.holeStat.recoveryChip
    }

        // Per-club stats (computed from ALL shots, assuming they are approach shots)
        val perClub = mutableMapOf<Int, ClubStats>()
        val allShots = allHoles.flatMap { h -> 
            h.shots.map { shot -> 
                Triple(shot, h.holeStat, h.hole) // Context if needed
            } 
        }
        val shotsWithClub = allShots.filter { it.first.clubId != null && it.first.outcome != null }
        shotsWithClub.groupBy { it.first.clubId!! }.forEach { (cid, sShots) ->
             // Helper for club stats from Shots
             val outcomes = sShots.mapNotNull { it.first.outcome }
             val total = outcomes.size.coerceAtLeast(1)
             val onTarget = outcomes.count { it == ShotOutcome.ON_TARGET }
             val distances = sShots.mapNotNull { it.first.distanceToPin }.filter { it > 0 }
             
             perClub[cid] = ClubStats(
                onTargetPct = (onTarget.toDouble() / total) * 100,
                missLeftPct = (outcomes.count { it == ShotOutcome.MISS_LEFT }.toDouble() / total) * 100,
                missRightPct = (outcomes.count { it == ShotOutcome.MISS_RIGHT }.toDouble() / total) * 100,
                missShortPct = (outcomes.count { it == ShotOutcome.SHORT }.toDouble() / total) * 100,
                missLongPct = (outcomes.count { it == ShotOutcome.LONG }.toDouble() / total) * 100,
                avgDistance = if (distances.isNotEmpty()) distances.average() else 0.0,
                sampleCount = sShots.size
             )
        }

        // Calculate mishit rate for approach (independent of toggle)
        val allPrimaryApproachShots = allHoles.mapNotNull { h -> 
            h.shots.maxByOrNull { it.shotNumber } ?: h.holeStat.approachOutcome?.let { h.holeStat }
        }
        val approachMishitCount = allHoles.count { it.holeStat.approachMishit }
        val approachMishitTotal = allHoles.size.coerceAtLeast(1)
        val approachMishitPct = (approachMishitCount.toDouble() / approachMishitTotal) * 100

        // Apply club filter for main stats
        val clubFilteredHoles = if (clubIdFilter != null) {
            allHoles.filter { h -> 
                val finalShot = h.shots.maxByOrNull { it.shotNumber }
                if (finalShot != null) finalShot.clubId == clubIdFilter
                else h.holeStat.approachClubId == clubIdFilter
            }
        } else allHoles

        // Filter by mishit toggle for main metrics (GIR, accuracy, etc.)
        val holes = if (!filter.includeMishits) {
            clubFilteredHoles.filter { !it.holeStat.approachMishit }
        } else clubFilteredHoles

        if (holes.isEmpty()) return ApproachStats(
            perClubStats = perClub, 
            selectedClubId = clubIdFilter,
            mishitPct = approachMishitPct
        )

        val girCount = holes.count { isGir(it) }
        val nearGirCount = holes.count { isNearGir(it) }

        // Determine effective approach details for each hole (Final Shot)
        val holeDetails = holes.map { h ->
            val finalShot = h.shots.maxByOrNull { it.shotNumber }
            val outcome = finalShot?.outcome ?: h.holeStat.approachOutcome
            val lie = finalShot?.lie ?: h.holeStat.approachLie
            val distance = finalShot?.distanceToPin ?: h.holeStat.approachShotDistance
            val dLeft = finalShot?.dispersionLeft
            val dRight = finalShot?.dispersionRight
            val dShort = finalShot?.dispersionShort
            val dLong = finalShot?.dispersionLong
            DataPoint(h, outcome, lie, distance, dLeft, dRight, dShort, dLong)
        }
        
        // Collect dispersion points from the Shot table
        val rawPointsWithLie = allHoles.flatMap { h ->
            val ctx = holeStatContext[h.holeStat.id]
            h.shots.filter { shot ->
                val matchesClub = clubIdFilter == null || shot.clubId == clubIdFilter
                matchesClub && shot.outcome != null &&
                (shot.dispersionLeft != null || shot.dispersionRight != null || shot.dispersionShort != null || shot.dispersionLong != null)
            }.map { shot ->
                (shot.lie ?: ApproachLie.OTHER) to DispersionPoint(shot.dispersionLeft, shot.dispersionRight, shot.dispersionShort, shot.dispersionLong, shot.isMishit,
                    holeNumber = h.hole.holeNumber, roundDate = ctx?.second, courseName = ctx?.third)
            }
        }

        // Avg Dispersion
        val avgLateralMiss = if (rawPointsWithLie.isNotEmpty()) {
            rawPointsWithLie.sumOf { (it.second.right?.toDouble() ?: 0.0) - (it.second.left?.toDouble() ?: 0.0) } / rawPointsWithLie.size
        } else 0.0
        val avgDistanceMiss = if (rawPointsWithLie.isNotEmpty()) {
            rawPointsWithLie.sumOf { (it.second.long?.toDouble() ?: 0.0) - (it.second.short?.toDouble() ?: 0.0) } / rawPointsWithLie.size
        } else 0.0

        val filteredPointsWithLie = if (!filter.includeMishits) {
            rawPointsWithLie.filter { !it.second.isMishit }
        } else rawPointsWithLie

        val pointsByLie = filteredPointsWithLie.groupBy({ it.first }, { it.second })

        // GIR by lie
        val detailsWithLie = holeDetails.filter { it.lie != null }
        val girByLie = ApproachLie.values().associate { lie ->
            val inLie = detailsWithLie.filter { it.lie == lie }
            val girForLie = inLie.count { isGir(it.h) }
            lie to if (inLie.isNotEmpty()) (girForLie.toDouble() / inLie.size) * 100 else 0.0
        }
        val countByLie = ApproachLie.values().associate { lie ->
            lie to detailsWithLie.count { it.lie == lie }
        }

        // Approach outcome
        val approachShots = holeDetails.mapNotNull { it.outcome }
        val approachTotal = approachShots.size.coerceAtLeast(1)
        val approachOnTarget = approachShots.count { it == ShotOutcome.ON_TARGET }
        val approachLeft = approachShots.count { it == ShotOutcome.MISS_LEFT }
        val approachRight = approachShots.count { it == ShotOutcome.MISS_RIGHT }
        val approachShort = approachShots.count { it == ShotOutcome.SHORT }
        val approachLong = approachShots.count { it == ShotOutcome.LONG }

        val approachDistances = holeDetails.mapNotNull { it.distance }.filter { it > 0 }
        val avgApproachDistance = if (approachDistances.isNotEmpty()) approachDistances.average() else 0.0

        val girPct = (girCount.toDouble() / holes.size) * 100
        val nearGirPct = (nearGirCount.toDouble() / holes.size) * 100
        val onTargetPct = (approachOnTarget.toDouble() / approachTotal) * 100

        return ApproachStats(
            girPct = girPct,
            girMoE = calculateProportionMoE(girPct, holes.size),
            nearGirPct = nearGirPct,
            nearGirMoE = calculateProportionMoE(nearGirPct, holes.size),
            totalHoles = holes.size,
            avgDistance = avgApproachDistance,
            distanceMoE = calculateMeanMoE(approachDistances.map { it.toDouble() }),
            avgLateralMiss = avgLateralMiss,
            avgDistanceMiss = avgDistanceMiss,
            mishitPct = approachMishitPct,
            onTargetPct = onTargetPct,
            onTargetMoE = calculateProportionMoE(onTargetPct, approachTotal),
            missLeftPct = (approachLeft.toDouble() / approachTotal) * 100,
            missRightPct = (approachRight.toDouble() / approachTotal) * 100,
            missShortPct = (approachShort.toDouble() / approachTotal) * 100,
            missLongPct = (approachLong.toDouble() / approachTotal) * 100,
            girByLie = girByLie,
            countByLie = countByLie,
            perClubStats = perClub,
            selectedClubId = clubIdFilter,
            totalShots = approachTotal,
            rawDispersion = RawDispersionData(pointsByLie.values.flatten(), pointsByLie)
        )

        // On-target by distance range
        val distanceRanges = listOf(0..99, 100..149, 150..199, 200..Int.MAX_VALUE)
        val rangeLabels = listOf("<100", "100-150", "150-200", "200+")
        val onTargetByRange = distanceRanges.zip(rangeLabels).map { (range, label) ->
            val inRange = holeDetails.filter { d ->
                val dist = d.distance ?: return@filter false
                dist in range && d.outcome != null
            }
            val onT = inRange.count { it.outcome == ShotOutcome.ON_TARGET }
            val total = inRange.size.coerceAtLeast(1)
            OnTargetBreakdown(
                label = label,
                onTargetPct = (onT.toDouble() / total) * 100,
                sampleCount = inRange.size
            )
        }

        // On-target by lie
        val onTargetByLie = ApproachLie.values().map { lie ->
            val lieHoles = holeDetails.filter { it.lie == lie && it.outcome != null }
            val onT = lieHoles.count { it.outcome == ShotOutcome.ON_TARGET }
            val total = lieHoles.size.coerceAtLeast(1)
            OnTargetBreakdown(
                label = lie.name.lowercase().replaceFirstChar { it.uppercase() },
                onTargetPct = (onT.toDouble() / total) * 100,
                sampleCount = lieHoles.size
            )
        }

        val approachByPar = holes.groupBy { it.hole.par }.mapValues { (par, parHoles) ->
            val gir = parHoles.count { h -> GirCalculator.isGir(h.holeStat.score, h.hole.par, h.holeStat.putts) }
            ParBreakdown(
                label = "Par $par",
                girPct = (gir.toDouble() / parHoles.size) * 100,
                sampleCount = parHoles.size
            )
        }

        return ApproachStats(
            girPct = girPct,
            girMoE = calculateProportionMoE(girPct, holes.size),
            nearGirPct = nearGirPct,
            nearGirMoE = calculateProportionMoE(nearGirPct, holes.size),
            girByLie = girByLie,
            countByLie = countByLie,
            totalHoles = holes.size,
            avgDistance = avgApproachDistance,
            distanceMoE = calculateMeanMoE(approachDistances.map { it.toDouble() }),
            onTargetPct = onTargetPct,
            onTargetMoE = calculateProportionMoE(onTargetPct, approachTotal),
            missLeftPct = (approachLeft.toDouble() / approachTotal) * 100,
            missRightPct = (approachRight.toDouble() / approachTotal) * 100,
            missShortPct = (approachShort.toDouble() / approachTotal) * 100,
            missLongPct = (approachLong.toDouble() / approachTotal) * 100,
            perClubStats = perClub,
            selectedClubId = clubIdFilter,
            onTargetByRange = onTargetByRange,
            onTargetByLie = onTargetByLie,
            totalShots = approachShots.size,
            rawDispersion = RawDispersionData(
                points = pointsByLie.values.flatten(),
                pointsByLie = pointsByLie
            ),
            byPar = approachByPar
        )
    }

    private data class DataPoint(
        val h: HoleStatWithHole,
        val outcome: ShotOutcome?,
        val lie: ApproachLie?,
        val distance: Int?,
        val dLeft: Int? = null,
        val dRight: Int? = null,
        val dShort: Int? = null,
        val dLong: Int? = null
    )


    // ── Chipping ────────────────────────────────────────────────────────

    private fun calculateChippingStats(rounds: List<RoundWithDetails>): ChippingStats {
        val holes = rounds.flatMap { it.holeStats }.filter { it.holeStat.score > 0 }
        if (holes.isEmpty()) return ChippingStats()

        fun isGir(h: HoleStatWithHole): Boolean {
            return GirCalculator.isGir(h.holeStat.score, h.hole.par, h.holeStat.putts)
        }

        fun isSandSave(h: HoleStatWithHole): Boolean {
            val wasInSand = h.holeStat.sandShots > 0 || h.holeStat.approachLie == ApproachLie.SAND || 
                            h.shots.any { it.lie == ApproachLie.SAND }
            return wasInSand && h.holeStat.score <= h.hole.par
        }

        fun isParSave(h: HoleStatWithHole): Boolean {
            return !isGir(h) && h.holeStat.score <= h.hole.par
        }

        val missedGirHoles = holes.filter { !isGir(it) }
        val missedCount = missedGirHoles.size.coerceAtLeast(1)

        // Up & Down: any hole where the player chipped, regardless of GIR status.
        // This covers missed-GIR situations as well as cases where a player drives close to
        // the green on a par 4 or reaches the par-5 green area in 2 and still chips on.
        // Success = exactly 1 chip AND at most 1 putt (covers chip-ins and standard up-and-downs).
        // Failure = 2+ chips, or 1 chip + 2+ putts.
        val upAndDownHoles = holes.filter { it.holeStat.chips >= 1 }
        val upAndDownOpportunities = upAndDownHoles.size.coerceAtLeast(1)
        val upAndDownSuccesses = upAndDownHoles.count { it.holeStat.chips == 1 && it.holeStat.putts <= 1 }
        val upAndDownPct = (upAndDownSuccesses.toDouble() / upAndDownOpportunities) * 100

        // Par Save: missed GIR but still made par or better (score-based, unchanged)
        val parSaves = missedGirHoles.count { isParSave(it) }
        val parSavePct = (parSaves.toDouble() / missedCount) * 100

        val sandHoles = holes.filter { it.holeStat.sandShots > 0 || it.shots.any { s -> s.lie == ApproachLie.SAND } }
        val sandSaves = sandHoles.count { isSandSave(it) }
        val sandSavePct = if (sandHoles.isNotEmpty()) (sandSaves.toDouble() / sandHoles.size) * 100 else 0.0

        val doubleChips = holes.count { it.holeStat.chips >= 2 }
        val chipsPerHole = holes.sumOf { it.holeStat.chips }.toDouble() / holes.size

        // Proximity (Avg Next Putt Distance in feet)
        val proximityHoles = holes.filter { it.holeStat.chips > 0 || it.holeStat.sandShots > 0 }
        val proximities = proximityHoles.mapNotNull { it.putts.firstOrNull()?.distance }
        val avgProximity = if (proximities.isNotEmpty()) proximities.average() else 0.0

        // Proximity by Lie
        val proximityByLie = proximityHoles.groupBy {
            if (it.holeStat.sandShots > 0) ApproachLie.SAND else (it.holeStat.chipLie ?: ApproachLie.ROUGH)
        }.mapValues { (_, holesWithLie) ->
            val proximitiesForLie = holesWithLie.mapNotNull { it.putts.firstOrNull()?.distance }
            if (proximitiesForLie.isNotEmpty()) proximitiesForLie.average() else 0.0
        }

        val totalHolesPlayed = holes.size
        val upAndDownsPerRound = (upAndDownSuccesses.toDouble() / totalHolesPlayed) * 18
        val parSavesPerRound = (parSaves.toDouble() / totalHolesPlayed) * 18
        val doubleChipsPerRound = (doubleChips.toDouble() / totalHolesPlayed) * 18

        return ChippingStats(
            upAndDownPct = upAndDownPct,
            upAndDownMoE = calculateProportionMoE(upAndDownPct, upAndDownOpportunities),
            parSavePct = parSavePct,
            parSaveMoE = calculateProportionMoE(parSavePct, missedCount),
            sandSavePct = sandSavePct,
            sandSaveMoE = calculateProportionMoE(sandSavePct, sandHoles.size),
            chipsPerHole = chipsPerHole,
            chipsMoE = calculateMeanMoE(holes.map { it.holeStat.chips.toDouble() }),
            doubleChips = doubleChips,
            avgProximity = avgProximity,
            proximityByLie = proximityByLie,
            totalMissedGir = missedCount,
            totalSandHoles = sandHoles.size,
            upAndDownsPerRound = upAndDownsPerRound,
            parSavesPerRound = parSavesPerRound,
            doubleChipsPerRound = doubleChipsPerRound
        )
    }

    // ── Putting ─────────────────────────────────────────────────────────

    private fun calculatePuttingStats(rounds: List<RoundWithDetails>): PuttingStats {
        val holes = rounds.flatMap { it.holeStats }.filter { it.holeStat.score > 0 }
        if (holes.isEmpty()) return PuttingStats()

        val roundCount = rounds.size.coerceAtLeast(1)
        val totalPutts = holes.sumOf { it.holeStat.putts }

        fun isGir(h: HoleStatWithHole): Boolean {
            return GirCalculator.isGir(h.holeStat.score, h.hole.par, h.holeStat.putts)
        }

        val girHoles = holes.filter { isGir(it) }
        val puttsOnGir = girHoles.sumOf { it.holeStat.putts }

        // Putt count buckets
        val onePutts = holes.count { it.holeStat.putts == 1 }
        val twoPutts = holes.count { it.holeStat.putts == 2 }
        val threePlusPutts = holes.count { it.holeStat.putts >= 3 }

        // Putt distances from the Putt entity (joined via relation)
        val allPutts = holes.flatMap { it.putts }
        val firstPutts = holes.mapNotNull { h ->
            h.putts.filter { it.distance != null && it.distance > 0 }
                .minByOrNull { it.puttNumber }
        }
        val avgFirstPuttDist = if (firstPutts.isNotEmpty()) {
            firstPutts.mapNotNull { it.distance?.toDouble() }.average()
        } else 0.0

        // "Make putt distance" = distance of putts that were the last putt on the hole (made)
        // Approximation: last putt on each hole where putts > 0
        val madePutts = holes.mapNotNull { h ->
            if (h.holeStat.putts > 0) {
                h.putts.filter { it.distance != null && it.distance > 0 }
                    .maxByOrNull { it.puttNumber }
            } else null
        }
        val avgMadePuttDist = if (madePutts.isNotEmpty()) {
            madePutts.mapNotNull { it.distance?.toDouble() }.average()
        } else 0.0

        // Normalize per-round putting totals to 18-hole equivalent
        val normalizedPuttsPerRound = rounds.map { r ->
            val playedHoles = r.holeStats.count { it.holeStat.score > 0 }
            val putts = r.holeStats.filter { it.holeStat.score > 0 }.sumOf { it.holeStat.putts }
            if (playedHoles > 0) (putts.toDouble() / playedHoles) * 18 else putts.toDouble()
        }

        val totalHolesPlayed = holes.size
        val avgPuttsPerRound = (totalPutts.toDouble() / totalHolesPlayed) * 18
        val onePuttsPerRound = (onePutts.toDouble() / totalHolesPlayed) * 18
        val twoPuttsPerRound = (twoPutts.toDouble() / totalHolesPlayed) * 18
        val threePlusPuttsPerRound = (threePlusPutts.toDouble() / totalHolesPlayed) * 18

        val onePuttPct = if (holes.isNotEmpty()) (onePutts.toDouble() / holes.size) * 100 else 0.0

        // ── Lag putting (first putt ≥ 25 ft) ────────────────────────────
        val lagHoles = holes.filter { h ->
            val firstPutt = h.putts.filter { (it.distance ?: 0f) > 0f }.minByOrNull { it.puttNumber }
            (firstPutt?.distance ?: 0f) >= 25f
        }
        val lagPuttCount = lagHoles.size

        // Within 3 ft: holed on the lag (1-putt hole) counts as 0 ft remaining
        val lagWithin3Ft = lagHoles.count { h ->
            if (h.holeStat.putts == 1) {
                true // holed the lag putt
            } else {
                val sorted = h.putts.filter { (it.distance ?: 0f) > 0f }.sortedBy { it.puttNumber }
                val secondDist = sorted.getOrNull(1)?.distance
                secondDist != null && secondDist <= 3f
            }
        }
        val lagWithin3FtPct = if (lagPuttCount > 0) (lagWithin3Ft.toDouble() / lagPuttCount) * 100 else 0.0

        // Remaining % — hole-outs contribute 0 %; only include holes where we can determine the value
        val lagRemainingPcts = lagHoles.mapNotNull { h ->
            val sorted = h.putts.filter { (it.distance ?: 0f) > 0f }.sortedBy { it.puttNumber }
            val firstDist = sorted.firstOrNull()?.distance ?: return@mapNotNull null
            if (firstDist <= 0f) return@mapNotNull null
            if (h.holeStat.putts == 1) {
                0.0 // holed it
            } else {
                val secondDist = sorted.getOrNull(1)?.distance ?: return@mapNotNull null
                (secondDist.toDouble() / firstDist.toDouble()) * 100.0
            }
        }
        val avgLagRemainingPct = if (lagRemainingPcts.isNotEmpty()) lagRemainingPcts.average() else 0.0

        // ── Make % by 5-ft distance buckets ─────────────────────────────
        // Putt.made is not reliably set by the tracking UI, so infer it:
        // the last putt on each hole (highest puttNumber) is always made.
        val puttsWithDist = holes.flatMap { h ->
            val maxPuttNum = h.putts.maxOfOrNull { it.puttNumber } ?: 0
            h.putts.filter { (it.distance ?: 0f) > 0f }
                .map { putt -> putt.copy(made = putt.puttNumber == maxPuttNum) }
        }
        data class BucketDef(val label: String, val min: Float, val max: Float)
        val bucketDefs = listOf(
            BucketDef("0-5 ft",   0f,  5f),
            BucketDef("5-10 ft",  5f, 10f),
            BucketDef("10-15 ft",10f, 15f),
            BucketDef("15-20 ft",15f, 20f),
            BucketDef("20-25 ft",20f, 25f),
            BucketDef("25+ ft",  25f, Float.MAX_VALUE)
        )
        val makePctByDistance = bucketDefs.mapNotNull { (label, min, max) ->
            val inRange = puttsWithDist.filter { it.distance!! >= min && it.distance < max }
            if (inRange.isEmpty()) return@mapNotNull null
            val made = inRange.count { it.made }
            DistanceBucket(label, (made.toDouble() / inRange.size) * 100, inRange.size)
        }

        return PuttingStats(
            totalPutts = totalPutts,
            avgPuttsPerRound = avgPuttsPerRound,
            puttsMoE = calculateMeanMoE(normalizedPuttsPerRound),
            puttsPerGir = if (girHoles.isNotEmpty()) puttsOnGir.toDouble() / girHoles.size else 0.0,
            puttsPerGirMoE = calculateMeanMoE(girHoles.map { it.holeStat.putts.toDouble() }),
            avgFirstPuttDistance = avgFirstPuttDist,
            firstPuttDistMoE = calculateMeanMoE(firstPutts.mapNotNull { it.distance?.toDouble() }),
            avgMakePuttDistance = avgMadePuttDist,
            makePuttDistMoE = calculateMeanMoE(madePutts.mapNotNull { it.distance?.toDouble() }),
            onePuttPct = onePuttPct,
            onePuttMoE = calculateProportionMoE(onePuttPct, holes.size),
            twoPuttPct = if (holes.isNotEmpty()) (twoPutts.toDouble() / holes.size) * 100 else 0.0,
            threePlusPuttPct = if (holes.isNotEmpty()) (threePlusPutts.toDouble() / holes.size) * 100 else 0.0,
            onePuttsPerRound = onePuttsPerRound,
            twoPuttsPerRound = twoPuttsPerRound,
            threePlusPuttsPerRound = threePlusPuttsPerRound,
            puttsPerRoundDistribution = normalizedPuttsPerRound,
            lagPuttCount = lagPuttCount,
            lagWithin3FtPct = lagWithin3FtPct,
            lagWithin3FtMoE = calculateProportionMoE(lagWithin3FtPct, lagPuttCount),
            avgLagRemainingPct = avgLagRemainingPct,
            makePctByDistance = makePctByDistance
        )
    }

    // ── Helper functions for Margin of Error ─────────────────────────────

    /**
     * Calculates Margin of Error for a proportion (percentage) at 95% confidence.
     * Formula: MoE = 1.96 * sqrt(p * (1-p) / n)
     */
    private fun calculateProportionMoE(p: Double, n: Int): Double {
        if (n <= 0) return 0.0
        // p is expected to be 0.0 to 100.0 (internal logic uses 0-100, so we divide by 100)
        val pReal = (p / 100.0).coerceIn(0.0, 1.0)
        return 1.96 * sqrt(pReal * (1.0 - pReal) / n) * 100.0
    }

    /**
     * Calculates Margin of Error for a mean at 95% confidence.
     * Formula: MoE = 1.96 * (stdDev / sqrt(n))
     */
    private fun calculateMeanMoE(values: List<Double>): Double {
        val n = values.size
        if (n < 2) return 0.0
        
        val avg = values.average()
        val variance = values.sumOf { (it - avg) * (it - avg) } / (n - 1)
        val stdDev = sqrt(variance)
        
        return 1.96 * (stdDev / sqrt(n.toDouble()))
    }

    private fun <T> List<T>.averageOf(selector: (T) -> Double): Double {
        if (this.isEmpty()) return 0.0
        return this.sumOf(selector) / this.size
    }

    // ── Strokes Gained ──────────────────────────────────────────────────

    private fun calculateSgStats(
        rounds: List<RoundWithDetails>,
        yardageMap: Map<Pair<Int, Int>, com.golftracker.data.entity.HoleTeeYardage>,
        parMap: Map<Int, Int>,
        filter: StatsFilter
    ): SgStats {
        var totalSgOffTee = 0.0
        var totalSgApproach = 0.0
        var totalSgAroundGreen = 0.0
        var totalSgPutting = 0.0
        var totalSgPenalties = 0.0
        
        val sgByLie = mutableMapOf<ApproachLie, MutableList<Double>>()
        val distanceRanges = listOf(0..30, 31..100, 101..150, 151..200, 201..Int.MAX_VALUE)
        val rangeLabels = listOf("<30y", "30-100y", "100-150y", "150-200y", "200y+")
        val sgByDistance = mutableMapOf<String, MutableList<Double>>()
        
        var totalLiveSg = 0.0

        for (round in rounds) {
            // Calculate difficulty adjustment for the round
            val is9Holes = round.round.totalHoles == 9
            val teeSetId = round.round.teeSetId
            val courseRating = if (is9Holes) round.teeSet.rating / 2.0 else round.teeSet.rating
            
            // Note: StatsRepository usually should have correct ratings.
            // If rating is 0, we'll skip adjustment.
            var totalRoundAdjustment = 0.0
            var courseDiff = 0.0
            if (courseRating > 0.0) {
                var totalPgaExpected = 0.0
                var coursePar = 0
                round.holeStats.forEach { hole ->
                    val yardage = yardageMap[Pair(teeSetId, hole.hole.id)]?.yardage ?: hole.hole.par * 40
                    totalPgaExpected += sgCalculator.getExpectedStrokes(yardage, ApproachLie.TEE, true)
                    coursePar += hole.hole.par
                }
                totalRoundAdjustment = courseRating - totalPgaExpected
                courseDiff = courseRating - coursePar
            }
            val numHoles = if (is9Holes) 9 else 18

            for (hole in round.holeStats) {
                val defaultYardage = yardageMap[Pair(teeSetId, hole.hole.id)]?.yardage ?: 0
                val holeYardage = hole.holeStat.adjustedYardage ?: defaultYardage

                val breakdown = sgCalculator.calculateHoleSg(
                    par = hole.hole.par,
                    holeYardage = holeYardage,
                    shots = hole.shots,
                    putts = hole.putts,
                    penalties = hole.penalties,
                    stat = hole.holeStat,
                    totalRoundAdjustment = totalRoundAdjustment,
                    numHoles = numHoles,
                    courseDiff = courseDiff
                )

                // Apply club filters to SG categories
                val teeClubMatches = filter.drivingClubId == null || hole.holeStat.teeClubId == filter.drivingClubId
                val teeMishitExclude = !filter.includeMishits && hole.holeStat.teeMishit
                
                // For approach, we check if the CLUB filter matches ANY approach shot on the hole
                // or if it matches the "primary" approach club stored in HoleStat.
                val approachClubMatches = filter.approachClubId == null || 
                    hole.shots.any { it.clubId == filter.approachClubId } || 
                    hole.holeStat.approachClubId == filter.approachClubId
                val approachMishitExclude = !filter.includeMishits && hole.holeStat.approachMishit

                if (teeClubMatches && !teeMishitExclude) {
                    totalSgOffTee += breakdown.offTee
                }
                if (approachClubMatches && !approachMishitExclude) {
                    totalSgApproach += breakdown.approach
                }
                
                // Around green and putting currently don't have club filters in the UI
                totalSgAroundGreen += breakdown.aroundGreen
                totalSgPutting += breakdown.putting
                totalSgPenalties += breakdown.penalties
                
                // Total SG for the hole only includes the components that match their respective club filters and mishit toggle
                totalLiveSg += (if (teeClubMatches && !teeMishitExclude) breakdown.offTee else 0.0) +
                               (if (approachClubMatches && !approachMishitExclude) breakdown.approach else 0.0) +
                               breakdown.aroundGreen + breakdown.putting + breakdown.penalties

                // Categorize for distributions
                sgByLie.getOrPut(ApproachLie.TEE) { mutableListOf() }.add(breakdown.offTee)
                // Note: Simplified distribution logic here, but using the live breakdown
                if (breakdown.aroundGreen != 0.0) {
                    val lie = if (hole.holeStat.sandShots > 0) ApproachLie.SAND else ApproachLie.ROUGH
                    sgByLie.getOrPut(lie) { mutableListOf() }.add(breakdown.aroundGreen)
                }
                
                // For distance breakdown, we use the primary shot for each category
                val dist = if (hole.hole.par > 3) holeYardage else hole.shots.firstOrNull()?.distanceToPin ?: holeYardage
                val rangeIdx = distanceRanges.indexOfFirst { dist in it }
                if (rangeIdx != -1) {
                    sgByDistance.getOrPut(rangeLabels[rangeIdx]) { mutableListOf() }.add(breakdown.offTee + breakdown.approach)
                }
            }
        }
        
        val avgLie = sgByLie.mapValues { if (it.value.isNotEmpty()) it.value.sum() else 0.0 }
        val avgDist = sgByDistance.mapValues { if (it.value.isNotEmpty()) it.value.sum() else 0.0 }
        
        val allHolesPlayed = rounds.sumOf { it.holeStats.count { h -> h.holeStat.score > 0 } }
        val normalizationFactor = if (allHolesPlayed > 0) 18.0 / allHolesPlayed else 1.0

        return SgStats(
            totalSgPerRound = totalLiveSg * normalizationFactor,
            sgOffTeePerRound = totalSgOffTee * normalizationFactor,
            sgApproachPerRound = totalSgApproach * normalizationFactor,
            sgAroundGreenPerRound = totalSgAroundGreen * normalizationFactor,
            sgPuttingPerRound = totalSgPutting * normalizationFactor,
            sgPenaltiesPerRound = totalSgPenalties * normalizationFactor,
            sgByLie = avgLie,
            sgByDistance = avgDist
        )
    }
}

// ── Data Classes ────────────────────────────────────────────────────────

data class StatsData(
    val rounds: List<RoundWithDetails> = emptyList(),
    val scoring: ScoringStats = ScoringStats(),
    val driving: DrivingStats = DrivingStats(),
    val approach: ApproachStats = ApproachStats(),
    val chipping: ChippingStats = ChippingStats(),
    val putting: PuttingStats = PuttingStats(),
    val sg: SgStats = SgStats()
)

data class RoundScoreSummary(
    val roundId: Int,
    val date: Date,
    val toPar: Int,
    val differential: Double?,
    val courseName: String = "",
    val totalHoles: Int = 18
)

data class ScoringStats(
    val avgScore: Double = 0.0,
    val scoreMoE: Double = 0.0,
    val avgToPar: Double = 0.0,
    val toParMoE: Double = 0.0,
    val roundsPlayed: Int = 0,
    val handicapIndex: Double? = null,
    val bestRoundToPar: Int = 0,
    val worstRoundToPar: Int = 0,
    val eagles: Int = 0,
    val birdies: Int = 0,
    val pars: Int = 0,
    val bogeyCount: Int = 0,
    val doubles: Int = 0,
    val worse: Int = 0,
    val scoreDistribution: List<Double> = emptyList(),
    val trend: List<RoundScoreSummary> = emptyList(),
    val byPar: Map<Int, ParBreakdown> = emptyMap()
)

data class ParBreakdown(
    val label: String = "",
    val avgScore: Double = 0.0, // Or whatever metric is relevant for the tab
    val avgToPar: Double? = null,
    val fairwaysHitPct: Double? = null,
    val girPct: Double? = null,
    val sampleCount: Int = 0
)

data class DrivingStats(
    val fairwaysHitPct: Double = 0.0,
    val fairwaysHitMoE: Double = 0.0,
    val troubleFreePct: Double = 0.0,
    val troubleFreeMoE: Double = 0.0,
    val missLeftPct: Double = 0.0,
    val missRightPct: Double = 0.0,
    val missShortPct: Double = 0.0,
    val missLongPct: Double = 0.0,
    val avgDistance: Double = 0.0,
    val distanceMoE: Double = 0.0,
    val avgDistanceExMishits: Double = 0.0,
    val distanceExMishitsMoE: Double = 0.0,
    val mishitPct: Double = 0.0,
    val mishitMoE: Double = 0.0,
    val avgLateralMiss: Double = 0.0,
    val avgDistanceMiss: Double = 0.0,
    val totalMishits: Int = 0,
    val totalDrivingHoles: Int = 0,
    val perClubStats: Map<Int, ClubStats> = emptyMap(),
    val selectedClubId: Int? = null,
    val rawDispersion: RawDispersionData = RawDispersionData(),
    val byPar: Map<Int, ParBreakdown> = emptyMap()
)

data class ApproachStats(
    val girPct: Double = 0.0,
    val girMoE: Double = 0.0,
    val nearGirPct: Double = 0.0,
    val nearGirMoE: Double = 0.0,
    val girByLie: Map<ApproachLie, Double> = emptyMap(),
    val countByLie: Map<ApproachLie, Int> = emptyMap(),
    val totalHoles: Int = 0,
    val avgDistance: Double = 0.0,
    val distanceMoE: Double = 0.0,
    val avgLateralMiss: Double = 0.0,
    val avgDistanceMiss: Double = 0.0,
    val mishitPct: Double = 0.0,
    val onTargetPct: Double = 0.0,
    val onTargetMoE: Double = 0.0,
    val missLeftPct: Double = 0.0,
    val missRightPct: Double = 0.0,
    val missShortPct: Double = 0.0,
    val missLongPct: Double = 0.0,
    val perClubStats: Map<Int, ClubStats> = emptyMap(),
    val selectedClubId: Int? = null,
    val onTargetByRange: List<OnTargetBreakdown> = emptyList(),
    val onTargetByLie: List<OnTargetBreakdown> = emptyList(),
    val totalShots: Int = 0,
    val rawDispersion: RawDispersionData = RawDispersionData(),
    val byPar: Map<Int, ParBreakdown> = emptyMap()
)

data class ClubStats(
    val onTargetPct: Double = 0.0,
    val missLeftPct: Double = 0.0,
    val missRightPct: Double = 0.0,
    val missShortPct: Double = 0.0,
    val missLongPct: Double = 0.0,
    val avgDistance: Double = 0.0,
    val sampleCount: Int = 0
)

data class RawDispersionData(
    val points: List<DispersionPoint> = emptyList(),
    val pointsByLie: Map<com.golftracker.data.model.ApproachLie, List<DispersionPoint>> = emptyMap()
)

data class DispersionPoint(
    val left: Int? = null,
    val right: Int? = null,
    val short: Int? = null,
    val long: Int? = null,
    val isMishit: Boolean = false,
    val holeNumber: Int? = null,
    val roundDate: Long? = null,
    val courseName: String? = null
)

data class OnTargetBreakdown(
    val label: String = "",
    val onTargetPct: Double = 0.0,
    val sampleCount: Int = 0
)

data class ChippingStats(
    val upAndDownPct: Double = 0.0,
    val upAndDownMoE: Double = 0.0,
    val parSavePct: Double = 0.0,
    val parSaveMoE: Double = 0.0,
    val sandSavePct: Double = 0.0,
    val sandSaveMoE: Double = 0.0,
    val chipsPerHole: Double = 0.0,
    val chipsMoE: Double = 0.0,
    val doubleChips: Int = 0,
    val avgProximity: Double = 0.0,
    val proximityByLie: Map<ApproachLie, Double> = emptyMap(),
    val totalMissedGir: Int = 0,
    val totalSandHoles: Int = 0,
    val upAndDownsPerRound: Double = 0.0,
    val parSavesPerRound: Double = 0.0,
    val doubleChipsPerRound: Double = 0.0
)

data class DistanceBucket(
    val label: String,
    val makePct: Double,
    val attempts: Int
)

data class PuttingStats(
    val totalPutts: Int = 0,
    val avgPuttsPerRound: Double = 0.0,
    val puttsMoE: Double = 0.0,
    val puttsPerGir: Double = 0.0,
    val puttsPerGirMoE: Double = 0.0,
    val avgFirstPuttDistance: Double = 0.0,
    val firstPuttDistMoE: Double = 0.0,
    val avgMakePuttDistance: Double = 0.0,
    val makePuttDistMoE: Double = 0.0,
    val onePuttPct: Double = 0.0,
    val onePuttMoE: Double = 0.0,
    val twoPuttPct: Double = 0.0,
    val threePlusPuttPct: Double = 0.0,
    val onePuttsPerRound: Double = 0.0,
    val twoPuttsPerRound: Double = 0.0,
    val threePlusPuttsPerRound: Double = 0.0,
    val puttsPerRoundDistribution: List<Double> = emptyList(),
    // Lag putting (first putt ≥ 25 ft)
    val lagPuttCount: Int = 0,
    val lagWithin3FtPct: Double = 0.0,
    val lagWithin3FtMoE: Double = 0.0,
    val avgLagRemainingPct: Double = 0.0,
    // Make % by 5-ft distance buckets
    val makePctByDistance: List<DistanceBucket> = emptyList()
)

data class SgStats(
    val totalSgPerRound: Double = 0.0,
    val sgOffTeePerRound: Double = 0.0,
    val sgApproachPerRound: Double = 0.0,
    val sgAroundGreenPerRound: Double = 0.0,
    val sgPuttingPerRound: Double = 0.0,
    val sgPenaltiesPerRound: Double = 0.0,
    val sgByLie: Map<ApproachLie, Double> = emptyMap(),
    val sgByDistance: Map<String, Double> = emptyMap()
)
