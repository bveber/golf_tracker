package com.golftracker.domain

import android.content.Context
import android.content.pm.PackageManager
import com.golftracker.data.repository.CourseRepository
import com.golftracker.data.repository.RoundRepository
import com.golftracker.data.entity.Hole
import com.golftracker.data.entity.HoleStat
import com.golftracker.data.entity.Shot
import com.golftracker.data.entity.Putt
import com.golftracker.data.entity.Penalty
import com.golftracker.data.entity.TeeSet
import com.golftracker.data.entity.Club
import com.golftracker.data.model.RoundWithDetails
import com.golftracker.data.model.HoleStatWithHole
import com.golftracker.data.model.ShotOutcome
import com.golftracker.data.model.ApproachLie
import com.golftracker.util.StrokesGainedCalculator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class HoleCalculationResult(
    val updatedStat: HoleStat,
    val correctedShots: List<Shot>,
    val correctedPutts: List<Putt>,
    val totalSg: Double,
    val difficultyAdjustment: Double
)

data class RoundAdjustments(
    val totalAdjustment: Double,
    val courseDiff: Double
)

/**
 * Recalculates and persists Strokes Gained for all finalized rounds whenever the
 * app's version code changes. This ensures that improvements to the
 * [StrokesGainedCalculator] are automatically applied to historical data.
 *
 * Call [runIfNeeded] once during application startup (e.g. from
 * [com.golftracker.GolfTrackerApp.onCreate]).
 */
@Singleton
class SgRecalculationUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val roundRepository: RoundRepository,
    private val courseRepository: CourseRepository,
    private val sgCalculator: StrokesGainedCalculator
) {
    companion object {
        private const val PREFS_NAME = "sg_recalc_prefs"
        private const val KEY_LAST_VERSION = "last_recalc_version"
    }

    /**
     * Runs the full SG recalculation for every hole in every finalized round, but only
     * when the current app [versionCode] differs from the last time recalculation ran.
     */
    suspend fun runIfNeeded() = withContext(Dispatchers.IO) {
        // Force recalculation on every startup for now to ensure consistency
        recalculateAll()
    }

    /**
     * Recalculates and persists Strokes Gained for all holes and shots in a specific round.
     */
    suspend fun recalculateRound(roundId: Int) = withContext(Dispatchers.IO) {
        val roundWithDetails = roundRepository.getRoundWithDetails(roundId) ?: return@withContext
        val allYardages = courseRepository.allYardages.first()
        val allHoles = courseRepository.allHoles.first()
        
        val adjustments = calculateTotalRoundAdjustment(roundWithDetails.round)
        recalculateSingleRound(roundWithDetails, allYardages, allHoles, adjustments)
    }

    /**
     * Performs a full recalculation of a single hole's stats and individual shot/putt SG.
     */
    suspend fun recalculateHole(
        roundId: Int,
        holeId: Int,
        providedStat: HoleStat? = null,
        providedShots: List<Shot>? = null,
        providedPutts: List<Putt>? = null,
        providedPenalties: List<Penalty>? = null,
        providedAdjustments: RoundAdjustments? = null
    ): HoleStat? = withContext(Dispatchers.IO) {
        val round = roundRepository.getRound(roundId) ?: return@withContext null
        val hole = courseRepository.getHole(holeId).first() ?: return@withContext null
        
        val record = providedStat ?: roundRepository.getHoleStat(roundId, holeId) ?: return@withContext null
        val shots = providedShots ?: roundRepository.getShotsForHoleStat(record.id).first()
        val putts = providedPutts ?: roundRepository.getPuttsForHoleStat(record.id).first()
        val penalties = providedPenalties ?: roundRepository.getPenaltiesForHoleStat(record.id).first()
        
        val yardageList = courseRepository.getYardagesForTeeSet(round.teeSetId).first()
        val defaultYardage = yardageList.find { it.holeId == hole.id }?.yardage ?: return@withContext null
        val holeYardage = record.adjustedYardage ?: defaultYardage

        val adjustments = providedAdjustments ?: calculateTotalRoundAdjustment(round)

        // Unified Calculation Logic
        val result = calculateHoleData(
            par = hole.par,
            holeYardage = holeYardage,
            stat = record,
            shots = shots,
            putts = putts,
            penalties = penalties,
            totalRoundAdjustment = adjustments.totalAdjustment,
            numHoles = if (round.totalHoles == 9) 9 else 18,
            courseDiff = adjustments.courseDiff
        )

        // Persistence
        for (corrected in result.correctedShots) {
            val original = shots.find { it.id == corrected.id }
            if (original != null && (original.shotNumber != corrected.shotNumber || original.strokesGained != corrected.strokesGained)) {
                roundRepository.updateShot(corrected)
            }
        }

        for (corrected in result.correctedPutts) {
            val original = putts.find { it.id == corrected.id }
            if (original != null && (original.puttNumber != corrected.puttNumber || original.strokesGained != corrected.strokesGained)) {
                roundRepository.updatePutt(corrected)
            }
        }

        if (result.updatedStat != record) {
            roundRepository.updateHoleStat(result.updatedStat)
        }
        
        result.updatedStat
    }

    /**
     * Pure calculation logic for a hole, including shot number correction, SG, score, and GIR.
     * Does NOT touch the database.
     */
    fun calculateHoleData(
        par: Int,
        holeYardage: Int,
        stat: HoleStat,
        shots: List<Shot>,
        putts: List<Putt>,
        penalties: List<Penalty>,
        totalRoundAdjustment: Double = 0.0,
        numHoles: Int = 18,
        courseDiff: Double? = null
    ): HoleCalculationResult {
        // 1. Shot Number Correction (De-duplication & Offset)
        val anyTeeTracked = shots.any { it.lie == ApproachLie.TEE }
        val teeShotInStat = par > 3 && (stat.teeOutcome != null || stat.teeShotDistance != null || stat.teeClubId != null || stat.teeLat != null)

        val sortedShots = shots.sortedBy { it.shotNumber }
        // When any tee shot is tracked (drive or re-tee), shot numbers are explicitly
        // assigned and encode stroke position — preserve them as-is.
        // Only apply sequential renumbering for approach-only sequences where
        // numbers may be ambiguous or off-by-one from legacy data.
        val correctedShots = if (anyTeeTracked) {
            sortedShots
        } else {
            val shotNumberOffset = if (teeShotInStat) 2 else 1
            sortedShots.mapIndexed { index, shot ->
                shot.copy(shotNumber = index + shotNumberOffset)
            }
        }

        // 2. Strokes Gained Calculation
        val breakdown = sgCalculator.calculateHoleSg(
            par = par,
            holeYardage = holeYardage,
            shots = correctedShots,
            putts = putts.sortedBy { it.puttNumber },
            penalties = penalties,
            stat = stat,
            totalRoundAdjustment = totalRoundAdjustment,
            numHoles = numHoles,
            courseDiff = courseDiff
        )

        // 3. Mark SG on shots/putts
        val finalizedShots = correctedShots.map { shot ->
            val sg = breakdown.shotSgs.find { it.first == shot.shotNumber }?.second
            shot.copy(strokesGained = sg)
        }

        val finalizedPutts = putts.sortedBy { it.puttNumber }.map { putt ->
            val sg = breakdown.puttSgs.find { it.first == putt.puttNumber }?.second
            putt.copy(strokesGained = sg)
        }

        // 4. Score & GIR Calculation
        val totalSg = breakdown.total
        val shortGameStrokes = stat.chips + stat.sandShots
        val teeShotTaken = par > 3 && (stat.teeOutcome != null || stat.teeShotDistance != null || stat.teeClubId != null || stat.teeLat != null)
        val holedOutFromOffGreen = (stat.teeOutcome == ShotOutcome.HOLED_OUT) || finalizedShots.any { it.outcome == ShotOutcome.HOLED_OUT }
        
        val calculatedScore = (if (teeShotTaken && finalizedShots.none { it.shotNumber == 1 }) 1 else 0) + 
            finalizedShots.size + 
            shortGameStrokes + 
            stat.putts + 
            penalties.sumOf { it.strokes }
            
        val finishedHole = holedOutFromOffGreen || (stat.putts > 0 && finalizedPutts.any { it.made })
        val newScore = calculatedScore
        val hasData = teeShotTaken || finalizedShots.isNotEmpty() || finalizedPutts.isNotEmpty() || shortGameStrokes > 0 || penalties.isNotEmpty()
        val isGir = finishedHole && (newScore - stat.putts <= par - 2)

        val updatedStat = stat.copy(
            score = if (stat.scoreManual) stat.score else newScore,
            strokesGained = if (hasData) totalSg else null,
            sgOffTee = if (hasData) breakdown.offTee else null,
            sgApproach = if (hasData) breakdown.approach else null,
            sgAroundGreen = if (hasData) breakdown.aroundGreen else null,
            sgPutting = if (hasData) breakdown.putting else null,
            gir = isGir,
            sgOffTeeExpected = if (hasData) breakdown.offTeeExpected else null,
            difficultyAdjustment = if (hasData) breakdown.courseRatingAdjustment else 0.0
        )

        return HoleCalculationResult(
            updatedStat = updatedStat,
            correctedShots = finalizedShots,
            correctedPutts = finalizedPutts,
            totalSg = totalSg,
            difficultyAdjustment = breakdown.courseRatingAdjustment
        )
    }


    private suspend fun recalculateAll() {
        val allRoundsWithDetails = roundRepository.finalizedRoundsWithDetails.first()
        val allYardages = courseRepository.allYardages.first()
        val allHoles = courseRepository.allHoles.first()

        for (roundWithDetails in allRoundsWithDetails) {
            val adjustments = calculateTotalRoundAdjustment(roundWithDetails.round)
            recalculateSingleRound(roundWithDetails, allYardages, allHoles, adjustments)
        }
    }

    private suspend fun recalculateSingleRound(
        roundWithDetails: RoundWithDetails,
        allYardages: List<com.golftracker.data.entity.HoleTeeYardage>,
        allHoles: List<com.golftracker.data.entity.Hole>,
        adjustments: RoundAdjustments
    ) {
        for (holeStatWithHole in roundWithDetails.holeStats) {
            recalculateHole(
                roundId = roundWithDetails.round.id,
                holeId = holeStatWithHole.hole.id,
                providedStat = holeStatWithHole.holeStat,
                providedShots = holeStatWithHole.shots,
                providedPutts = holeStatWithHole.putts,
                providedPenalties = holeStatWithHole.penalties,
                providedAdjustments = adjustments
            )
        }
    }

    internal suspend fun calculateTotalRoundAdjustment(round: com.golftracker.data.entity.Round): RoundAdjustments {
        val teeSet = courseRepository.getTeeSet(round.teeSetId) ?: return RoundAdjustments(0.0, 0.0)
        if (teeSet.rating == 0.0) return RoundAdjustments(0.0, 0.0)

        val is9Holes = round.totalHoles == 9
        val courseRating = if (is9Holes) teeSet.rating / 2.0 else teeSet.rating

        // 1. Fetch yardages and holes for the round
        val allYardages = courseRepository.getYardagesForTeeSet(round.teeSetId).first()
        val allHoles = courseRepository.getHoles(round.courseId).first()
        val playedHoles = if (is9Holes) {
            if (round.startHole == 1) allHoles.filter { it.holeNumber in 1..9 }
            else allHoles.filter { it.holeNumber in 10..18 }
        } else {
            allHoles
        }

        if (playedHoles.isEmpty()) return RoundAdjustments(0.0, 0.0)

        // 2. Calculate the Sum of PGA Expected Strokes and course par for the played holes
        var totalPgaExpected = 0.0
        var coursePar = 0
        playedHoles.forEach { hole ->
            val yardage = allYardages.find { it.holeId == hole.id }?.yardage ?: hole.par * 40
            totalPgaExpected += sgCalculator.getExpectedStrokes(
                distance = yardage,
                lie = ApproachLie.TEE,
                isTeeShot = true
            )
            coursePar += hole.par
        }

        return RoundAdjustments(
            totalAdjustment = courseRating - totalPgaExpected,
            courseDiff = courseRating - coursePar
        )
    }

    private fun getVersionCode(): Long {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        } catch (e: PackageManager.NameNotFoundException) {
            -1L
        }
    }
}
