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
import kotlin.math.sqrt

// ── Filter ──────────────────────────────────────────────────────────────

data class StatsFilter(
    val courseId: Int? = null,
    val teeSetId: Int? = null,
    val lastNRounds: Int = 20,
    val year: Int? = null,
    val startDate: java.util.Date? = null,
    val endDate: java.util.Date? = null,
    val drivingClubId: Int? = null,
    val approachClubId: Int? = null,
    val excludedRoundIds: Set<Int> = emptySet()
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
            userPreferencesRepository.estimatedHandicapFlow
        ) { allRounds, allYardages, estimatedHandicap ->
            val filtered = applyFilter(allRounds, filter)
            
            // For driving: Map (TeeSetId -> HoleId -> Yardage)
            val yardageMapForDriving = allYardages.groupBy { it.teeSetId }
                .mapValues { (_, list) -> list.associate { it.holeId to it.yardage } }
            
            // For SG: Map (Pair(TeeSetId, HoleId) -> Yardage)
            val yardageMapForSg = allYardages.associateBy { it.teeSetId to it.holeId }
            
            StatsData(
                rounds = filtered,
                scoring = calculateScoringStats(filtered, estimatedHandicap),
                driving = calculateDrivingStats(filtered, yardageMapForDriving, filter.drivingClubId),
                approach = calculateApproachStats(filtered, filter.approachClubId),
                chipping = calculateChippingStats(filtered),
                putting = calculatePuttingStats(filtered),
                sg = calculateSgStats(filtered, yardageMapForSg)
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
                differential = diffMap[r.round.id]?.value
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
        clubIdFilter: Int? = null
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
        val teeShots = drivingHoles.mapNotNull { it.holeStat.teeOutcome }
        val totalWithOutcome = teeShots.size.coerceAtLeast(1)

        val fairwaysHit = teeShots.count { it == ShotOutcome.ON_TARGET }
        val leftMisses = teeShots.count { it == ShotOutcome.MISS_LEFT }
        val rightMisses = teeShots.count { it == ShotOutcome.MISS_RIGHT }
        val shortMisses = teeShots.count { it == ShotOutcome.SHORT }
        val longMisses = teeShots.count { it == ShotOutcome.LONG }
        
        // Extract raw dispersion points
        val rawPoints = drivingHoles.mapNotNull { h ->
            val stat = h.holeStat
            if (stat.teeOutcome != null && stat.teeOutcome != ShotOutcome.ON_TARGET && 
                (stat.teeDispersionLeft != null || stat.teeDispersionRight != null || stat.teeDispersionShort != null || stat.teeDispersionLong != null)) {
                DispersionPoint(
                    left = stat.teeDispersionLeft,
                    right = stat.teeDispersionRight,
                    short = stat.teeDispersionShort,
                    long = stat.teeDispersionLong
                )
            } else null
        }

        val troubleFreeCount = drivingHoles.count { it.holeStat.teeOutcome != null && !it.holeStat.teeInTrouble }
        val troubleTotal = drivingHoles.count { it.holeStat.teeOutcome != null }.coerceAtLeast(1)

        // Calculate distances (explicit OR inferred)
        val teeDistances = drivingTriples.mapNotNull { (h, teeSetId, _) ->
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

        val fairwaysHitPct = (fairwaysHit.toDouble() / totalWithOutcome) * 100
        val troubleFreePct = (troubleFreeCount.toDouble() / troubleTotal) * 100

        val mishitCount = drivingHoles.count { it.holeStat.teeMishit }
        val mishitTotal = drivingHoles.count { it.holeStat.teeOutcome != null }.coerceAtLeast(1)
        val mishitPct = (mishitCount.toDouble() / mishitTotal) * 100

        // Filtered distances (excluding mishits)
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
        val avgCleanDistance = if (cleanDistances.isNotEmpty()) cleanDistances.average() else 0.0

        val drivingByPar = drivingHoles.groupBy { it.hole.par }.mapValues { (par, holes) ->
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
            fairwaysHitMoE = calculateProportionMoE(fairwaysHitPct, totalWithOutcome),
            troubleFreePct = troubleFreePct,
            troubleFreeMoE = calculateProportionMoE(troubleFreePct, troubleTotal),
            missLeftPct = (leftMisses.toDouble() / totalWithOutcome) * 100,
            missRightPct = (rightMisses.toDouble() / totalWithOutcome) * 100,
            missShortPct = (shortMisses.toDouble() / totalWithOutcome) * 100,
            missLongPct = (longMisses.toDouble() / totalWithOutcome) * 100,
            avgDistance = avgTeeDistance,
            distanceMoE = calculateMeanMoE(teeDistances.map { it.toDouble() }),
            avgDistanceExMishits = avgCleanDistance,
            distanceExMishitsMoE = calculateMeanMoE(cleanDistances.map { it.toDouble() }),
            mishitPct = mishitPct,
            mishitMoE = calculateProportionMoE(mishitPct, mishitTotal),
            totalMishits = mishitCount,
            totalDrivingHoles = drivingHoles.size,
            perClubStats = perClub,
            selectedClubId = clubIdFilter,
            rawDispersion = RawDispersionData(rawPoints),
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

    private fun calculateApproachStats(rounds: List<RoundWithDetails>, clubIdFilter: Int? = null): ApproachStats {
        val allHoles = rounds.flatMap { it.holeStats }.filter { it.holeStat.score > 0 }
        if (allHoles.isEmpty()) return ApproachStats()

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

        // Apply club filter for main stats
        val holes = if (clubIdFilter != null) {
            allHoles.filter { h -> 
                val finalShot = h.shots.maxByOrNull { it.shotNumber }
                if (finalShot != null) finalShot.clubId == clubIdFilter
                else h.holeStat.approachClubId == clubIdFilter
            }
        } else allHoles

        if (holes.isEmpty()) return ApproachStats(perClubStats = perClub, selectedClubId = clubIdFilter)

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
        
        val rawPoints = holeDetails.mapNotNull { d ->
            if (d.outcome != null && d.outcome != ShotOutcome.ON_TARGET && 
                (d.dLeft != null || d.dRight != null || d.dShort != null || d.dLong != null)) {
                DispersionPoint(d.dLeft, d.dRight, d.dShort, d.dLong)
            } else null
        }

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
            rawDispersion = RawDispersionData(rawPoints),
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
        val chipsPerRound = (holes.sumOf { it.holeStat.chips }.toDouble() / totalHolesPlayed) * 18
        val parSavesPerRound = (parSaves.toDouble() / totalHolesPlayed) * 18
        val doubleChipsPerRound = (doubleChips.toDouble() / totalHolesPlayed) * 18

        return ChippingStats(
            upAndDownPct = parSavePct,
            upAndDownMoE = calculateProportionMoE(parSavePct, missedCount),
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
            upAndDownsPerRound = parSavesPerRound,
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
            puttsPerRoundDistribution = normalizedPuttsPerRound
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
        yardageMap: Map<Pair<Int, Int>, com.golftracker.data.entity.HoleTeeYardage>
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
            val teeSet = round.round.teeSetId
            // We need ALL yardages for this tee set to calculate the course adjustment correctly (18 holes)
            val allCourseYardages = yardageMap.filterKeys { it.first == teeSet }.values
                .map { Pair(it.yardage, null as Int?) }
            
            val courseAdj = sgCalculator.calculateCourseAdjustment(round.teeSet.rating.toDouble(), allCourseYardages)

            for (hole in round.holeStats) {
                val holeYardage = yardageMap[teeSet to hole.hole.id]?.yardage ?: 0
                val holeAdj = sgCalculator.getHoleAdjustment(courseAdj, hole.hole.handicapIndex, 18)
                
                val breakdown = sgCalculator.calculateHoleSg(
                    par = hole.hole.par,
                    holeYardage = holeYardage,
                    holeAdjustment = holeAdj,
                    shots = hole.shots,
                    putts = hole.putts,
                    penalties = hole.penalties.sumOf { it.strokes },
                    stat = hole.holeStat
                )

                totalSgOffTee += breakdown.offTee
                totalSgApproach += breakdown.approach
                totalSgAroundGreen += breakdown.aroundGreen
                totalSgPutting += breakdown.putting
                totalSgPenalties += breakdown.penalties
                totalLiveSg += breakdown.total

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
    val differential: Double?
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
    val points: List<DispersionPoint> = emptyList()
)

data class DispersionPoint(
    val left: Int? = null,
    val right: Int? = null,
    val short: Int? = null,
    val long: Int? = null
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
    val puttsPerRoundDistribution: List<Double> = emptyList()
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
