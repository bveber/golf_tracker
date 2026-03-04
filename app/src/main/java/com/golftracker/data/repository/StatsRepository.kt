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
    val startDate: Date? = null,
    val endDate: Date? = null,
    val drivingClubId: Int? = null,
    val approachClubId: Int? = null
)

// ── Repository ──────────────────────────────────────────────────────────

class StatsRepository @Inject constructor(
    private val roundDao: RoundDao,
    private val courseRepository: CourseRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    fun getStatsData(): Flow<StatsData> = getFilteredStatsData(StatsFilter())

    fun getFilteredStatsData(filter: StatsFilter): Flow<StatsData> {
        return kotlinx.coroutines.flow.combine(
            roundDao.getFinalizedRoundsWithDetails(),
            courseRepository.allYardages,
            userPreferencesRepository.estimatedHandicapFlow
        ) { allRounds, allYardages, estimatedHandicap ->
            val filtered = applyFilter(allRounds, filter)
            
            // Map (TeeSetId, HoleId) -> Yardage
            val yardageMap = allYardages.groupBy { it.teeSetId }
                .mapValues { (_, list) -> list.associate { it.holeId to it.yardage } }

            StatsData(
                rounds = filtered,
                scoring = calculateScoringStats(filtered, estimatedHandicap),
                driving = calculateDrivingStats(filtered, yardageMap, filter.drivingClubId),
                approach = calculateApproachStats(filtered, filter.approachClubId),
                chipping = calculateChippingStats(filtered),
                putting = calculatePuttingStats(filtered),
                sg = calculateSgStats(filtered)
            )
        }
    }

    private fun applyFilter(rounds: List<RoundWithDetails>, filter: StatsFilter): List<RoundWithDetails> {
        var result = rounds

        filter.courseId?.let { cid ->
            result = result.filter { it.round.courseId == cid }
        }
        filter.teeSetId?.let { tid ->
            result = result.filter { it.round.teeSetId == tid }
        }
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
        // Last N rounds (applied last, after other filters narrow the set)
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
        val eagles = allHoles.count { it.holeStat.score <= it.hole.par - 2 }
        val birdies = allHoles.count { it.holeStat.score == it.hole.par - 1 }
        val pars = allHoles.count { it.holeStat.score == it.hole.par }
        val bogeys = allHoles.count { it.holeStat.score == it.hole.par + 1 }
        val doubles = allHoles.count { it.holeStat.score == it.hole.par + 2 }
        val worse = allHoles.count { it.holeStat.score > it.hole.par + 2 }

        val handicap = HandicapCalculator.calculateHandicapIndex(rounds) ?: estimatedHandicap

        val scores = roundScores.map { it.toDouble() }
        val toPars = roundToPars.map { it.toDouble() }
        val avgScore = totalScore.toDouble() / rounds.size
        val avgToPar = (totalScore - totalPar).toDouble() / rounds.size

        val diffs = HandicapCalculator.calculateDifferentials(rounds)
        val diffMap = diffs.associateBy { it.roundId }
        val trend = rounds.mapIndexed { index, r ->
            RoundScoreSummary(
                roundId = r.round.id,
                date = r.round.date,
                toPar = roundToPars[index],
                differential = diffMap[r.round.id]?.value
            )
        }.sortedBy { it.date }

        return ScoringStats(
            avgScore = avgScore,
            scoreMoE = calculateMeanMoE(scores),
            avgToPar = avgToPar,
            toParMoE = calculateMeanMoE(toPars),
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
            scoreDistribution = scores,
            trend = trend
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
            totalDrivingHoles = drivingHoles.size,
            perClubStats = perClub,
            selectedClubId = clubIdFilter
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
        // We filter HOLES based on whether they have a *relevant* approach shot with that club?
        // Or if the *final* approach shot used that club?
        // Usually filtering stats by club implies "Show me stats when I used X club".
        // If we filter by club, we should probably stick to holes where the *primary* (final) approach was with that club.
        val holes = if (clubIdFilter != null) {
            allHoles.filter { h -> 
                val finalShot = h.shots.maxByOrNull { it.shotNumber }
                // Fallback to holeStat if no shots (old data)
                if (finalShot != null) finalShot.clubId == clubIdFilter
                else h.holeStat.approachClubId == clubIdFilter
            }
        } else allHoles

        if (holes.isEmpty()) return ApproachStats(perClubStats = perClub, selectedClubId = clubIdFilter)

        val girCount = holes.count { isGir(it) }
        val nearGirCount = holes.count { isNearGir(it) }

        // Determine effective approach details for each hole (Final Shot)
        // Pair of <HoleStatWithHole, EffectiveOutcome?, EffectiveLie?, EffectiveDistance?>
        val holeDetails = holes.map { h ->
            val finalShot = h.shots.maxByOrNull { it.shotNumber }
            val outcome = finalShot?.outcome ?: h.holeStat.approachOutcome
            val lie = finalShot?.lie ?: h.holeStat.approachLie
            val distance = finalShot?.distanceToPin ?: h.holeStat.approachShotDistance
            DataPoint(h, outcome, lie, distance)
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
            totalShots = approachShots.size
        )
    }

    private data class DataPoint(
        val h: HoleStatWithHole,
        val outcome: ShotOutcome?,
        val lie: ApproachLie?,
        val distance: Int?
    )


    // ── Chipping ────────────────────────────────────────────────────────

    private fun calculateChippingStats(rounds: List<RoundWithDetails>): ChippingStats {
        val holes = rounds.flatMap { it.holeStats }.filter { it.holeStat.score > 0 }
        val roundCount = rounds.size.coerceAtLeast(1)
        if (holes.isEmpty()) return ChippingStats()

        fun isGir(h: HoleStatWithHole): Boolean {
            return GirCalculator.isGir(h.holeStat.score, h.hole.par, h.holeStat.putts)
        }

        fun isUpAndDown(h: HoleStatWithHole): Boolean {
            // Updated Logic: If missed GIR, and Score <= Par, it counts as Up & Down (Scrambling)
            // This captures "Par Save" essentially, which is the most common definition.
            // If we strictly want "1 putt", we could add `&& h.holeStat.putts <= 1`.
            // But usually "Up & Down" includes chip-ins (0 putts).
            return !isGir(h) && h.holeStat.score <= h.hole.par
        }

        fun isSandSave(h: HoleStatWithHole): Boolean {
            // Sand save: hitting from sand, and getting up-and-down for par or better
            // Ideally we check if they *were* in a bunker.
            val wasInSand = h.holeStat.sandShots > 0 || h.holeStat.approachLie == ApproachLie.SAND || 
                            h.shots.any { it.lie == ApproachLie.SAND }
            return wasInSand && h.holeStat.score <= h.hole.par
        }

        fun isParSave(h: HoleStatWithHole): Boolean {
            // Scrambling: Missed GIR but made Par or better
            return !isGir(h) && h.holeStat.score <= h.hole.par && h.holeStat.score > 0
        }

        val missedGirHoles = holes.filter { !isGir(it) }
        val missedCount = missedGirHoles.size.coerceAtLeast(1)

        // Up & Down: automatic definition
        val upAndDowns = missedGirHoles.count { isUpAndDown(it) }
        val upAndDownPct = if (missedCount > 0) (upAndDowns.toDouble() / missedCount) * 100 else 0.0

        // Par Saves: automatic definition
        val parSaves = missedGirHoles.count { isParSave(it) }
        val parSavePct = if (missedCount > 0) (parSaves.toDouble() / missedCount) * 100 else 0.0

        // Double chips: holes with chips >= 2
        val doubleChipHoles = holes.count { it.holeStat.chips >= 2 }

        // Average chips per hole (across all holes)
        val totalChips = holes.sumOf { it.holeStat.chips }
        val avgChipsPerHole = totalChips.toDouble() / holes.size

        // Sand saves
        val sandHoles = missedGirHoles.filter { h -> 
            h.holeStat.approachLie == ApproachLie.SAND || h.holeStat.sandShots > 0 || h.shots.any { it.lie == ApproachLie.SAND }
        }
        val sandSaves = sandHoles.count { isSandSave(it) }
        val sandSaveTotal = sandHoles.size.coerceAtLeast(1)

        val sandSavePct = if (sandSaveTotal > 0) (sandSaves.toDouble() / sandSaveTotal) * 100 else 0.0

        return ChippingStats(
            upAndDownPct = upAndDownPct,
            upAndDownMoE = calculateProportionMoE(upAndDownPct, missedGirHoles.size),
            parSavePct = parSavePct,
            parSaveMoE = calculateProportionMoE(parSavePct, missedGirHoles.size),
            sandSavePct = sandSavePct,
            sandSaveMoE = calculateProportionMoE(sandSavePct, sandHoles.size),
            avgChipsPerHole = avgChipsPerHole,
            chipsMoE = calculateMeanMoE(holes.map { it.holeStat.chips.toDouble() }),
            upAndDownsPerRound = upAndDowns.toDouble() / roundCount,
            parSavesPerRound = parSaves.toDouble() / roundCount,
            doubleChipsPerRound = doubleChipHoles.toDouble() / roundCount,
            totalMissedGir = missedGirHoles.size,
            totalSandHoles = sandHoles.size
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

        // Per-round putting distributions
        val puttsPerRoundList = rounds.map { r ->
            r.holeStats.filter { it.holeStat.score > 0 }.sumOf { it.holeStat.putts }.toDouble()
        }

        val onePuttPct = if (holes.isNotEmpty()) (onePutts.toDouble() / holes.size) * 100 else 0.0

        return PuttingStats(
            totalPutts = totalPutts,
            avgPuttsPerRound = totalPutts.toDouble() / roundCount,
            puttsMoE = calculateMeanMoE(puttsPerRoundList),
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
            onePuttsPerRound = onePutts.toDouble() / roundCount,
            twoPuttsPerRound = twoPutts.toDouble() / roundCount,
            threePlusPuttsPerRound = threePlusPutts.toDouble() / roundCount,
            puttsPerRoundDistribution = puttsPerRoundList
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
    // ── Strokes Gained ──────────────────────────────────────────────────

    private fun calculateSgStats(rounds: List<RoundWithDetails>): SgStats {
        var totalSgOffTee = 0.0
        var totalSgApproach = 0.0
        var totalSgAroundGreen = 0.0
        var totalSgPutting = 0.0
        
        var countOffTee = 0
        var countApproach = 0
        var countAroundGreen = 0
        
        val sgByLie = mutableMapOf<ApproachLie, MutableList<Double>>()
        val distanceRanges = listOf(0..30, 31..100, 101..150, 151..200, 201..Int.MAX_VALUE)
        val rangeLabels = listOf("<30y", "30-100y", "100-150y", "150-200y", "200y+")
        val sgByDistance = mutableMapOf<String, MutableList<Double>>()
        
        for (round in rounds) {
            for (hole in round.holeStats) {
                val stat = hole.holeStat
                
                stat.sgOffTee?.let { sg ->
                    totalSgOffTee += sg
                    countOffTee++
                    sgByLie.getOrPut(ApproachLie.TEE) { mutableListOf() }.add(sg)
                    val dist = stat.teeShotDistance ?: 250 // Assumption for categorizing tee shots if missing
                    val rangeIdx = distanceRanges.indexOfFirst { dist in it }
                    if (rangeIdx != -1) {
                        sgByDistance.getOrPut(rangeLabels[rangeIdx]) { mutableListOf() }.add(sg)
                    } else {
                        sgByDistance.getOrPut("200y+") { mutableListOf() }.add(sg)
                    }
                }
                
                stat.sgAroundGreen?.let { sg ->
                    totalSgAroundGreen += sg
                    countAroundGreen++
                    val lie = if (stat.sandShots > 0) ApproachLie.SAND else ApproachLie.ROUGH
                    sgByLie.getOrPut(lie) { mutableListOf() }.add(sg)
                    val dist = stat.chipDistance ?: 15
                    val rangeIdx = distanceRanges.indexOfFirst { dist in it }
                    if (rangeIdx != -1) {
                        sgByDistance.getOrPut(rangeLabels[rangeIdx]) { mutableListOf() }.add(sg)
                    }
                }
                
                stat.sgPutting?.let { sg ->
                    totalSgPutting += sg
                }

                val shots = hole.shots.sortedBy { it.shotNumber }
                for (i in shots.indices) {
                    val shot = shots[i]
                    val sg = shot.strokesGained ?: continue
                    val isFirstShotOfPar3 = (hole.hole.par == 3 && i == 0)
                    
                    if (!isFirstShotOfPar3) {
                        totalSgApproach += sg
                        countApproach++
                        
                        // ONLY track these properties down here if it belongs to Approach
                        val lie = shot.lie ?: ApproachLie.FAIRWAY
                        sgByLie.getOrPut(lie) { mutableListOf() }.add(sg)
                        
                        val dist = shot.distanceToPin
                        if (dist != null) {
                            val rangeIdx = distanceRanges.indexOfFirst { dist in it }
                            if (rangeIdx != -1) {
                                sgByDistance.getOrPut(rangeLabels[rangeIdx]) { mutableListOf() }.add(sg)
                            }
                        }
                    } else if (stat.sgOffTee == null) {
                        // Fallback logic for legacy rounds not fully mapped
                        totalSgOffTee += sg
                        countOffTee++
                        sgByLie.getOrPut(ApproachLie.TEE) { mutableListOf() }.add(sg)
                        sgByDistance.getOrPut("150-200y") { mutableListOf() }.add(sg)
                    }
                }
            }
        }
        
        val avgLie = sgByLie.mapValues { if (it.value.isNotEmpty()) it.value.sum() else 0.0 }
        val avgDist = sgByDistance.mapValues { if (it.value.isNotEmpty()) it.value.sum() else 0.0 }
        
        val totalSg = totalSgOffTee + totalSgApproach + totalSgAroundGreen + totalSgPutting
        val roundCount = rounds.size.coerceAtLeast(1)

        return SgStats(
            totalSgPerRound = totalSg / roundCount,
            sgOffTeePerRound = totalSgOffTee / roundCount,
            sgApproachPerRound = totalSgApproach / roundCount,
            sgAroundGreenPerRound = totalSgAroundGreen / roundCount,
            sgPuttingPerRound = totalSgPutting / roundCount,
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
    val trend: List<RoundScoreSummary> = emptyList()
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
    val totalDrivingHoles: Int = 0,
    val perClubStats: Map<Int, ClubStats> = emptyMap(),
    val selectedClubId: Int? = null
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
    val totalShots: Int = 0
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
    val avgChipsPerHole: Double = 0.0,
    val chipsMoE: Double = 0.0,
    val upAndDownsPerRound: Double = 0.0,
    val parSavesPerRound: Double = 0.0,
    val doubleChipsPerRound: Double = 0.0,
    val totalMissedGir: Int = 0,
    val totalSandHoles: Int = 0
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
    val sgByLie: Map<ApproachLie, Double> = emptyMap(),
    val sgByDistance: Map<String, Double> = emptyMap()
)
