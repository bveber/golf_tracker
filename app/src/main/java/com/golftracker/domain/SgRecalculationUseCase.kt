package com.golftracker.domain

import android.content.Context
import android.content.pm.PackageManager
import com.golftracker.data.repository.CourseRepository
import com.golftracker.data.repository.RoundRepository
import com.golftracker.data.model.ShotOutcome
import com.golftracker.data.model.ApproachLie
import com.golftracker.util.StrokesGainedCalculator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

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
        val currentVersion = getVersionCode()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastVersion = prefs.getLong(KEY_LAST_VERSION, -1L)

        if (currentVersion == lastVersion) return@withContext

        recalculateAll()

        prefs.edit().putLong(KEY_LAST_VERSION, currentVersion).apply()
    }

    private suspend fun recalculateAll() {
        val allRoundsWithDetails = roundRepository.finalizedRoundsWithDetails.first()
        val allYardages = courseRepository.allYardages.first()
        val allHoles = courseRepository.allHoles.first()

        for (roundWithDetails in allRoundsWithDetails) {
            val round = roundWithDetails.round
            val teeSet = roundWithDetails.teeSet
            val coursePar = allHoles
                .filter { it.courseId == round.courseId }
                .sumOf { it.par }
                .takeIf { it > 0 } ?: 72

            for (holeStatWithHole in roundWithDetails.holeStats) {
                val hole = holeStatWithHole.hole
                val holeStat = holeStatWithHole.holeStat

                val defaultYardage = allYardages
                    .firstOrNull { it.holeId == hole.id && it.teeSetId == round.teeSetId }
                    ?.yardage ?: continue
                val holeYardage = holeStat.adjustedYardage ?: defaultYardage

                val breakdown = sgCalculator.calculateHoleSg(
                    par = hole.par,
                    holeYardage = holeYardage,
                    shots = holeStatWithHole.shots,
                    putts = holeStatWithHole.putts,
                    penalties = holeStatWithHole.penalties,
                    stat = holeStat
                )

                val hasData = holeStat.teeOutcome != null ||
                        holeStat.teeShotDistance != null ||
                        holeStatWithHole.shots.isNotEmpty() ||
                        holeStatWithHole.putts.isNotEmpty() ||
                        holeStat.chips > 0

                val updatedStat = holeStat.copy(
                    strokesGained = if (hasData) breakdown.total else null,
                    sgOffTee = if (hasData) breakdown.offTee else null,
                    sgApproach = if (hasData) breakdown.approach else null,
                    sgAroundGreen = if (hasData) breakdown.aroundGreen else null,
                    sgPutting = if (hasData) breakdown.putting else null
                )

                if (updatedStat != holeStat) {
                    roundRepository.updateHoleStat(updatedStat)
                }

                // Also update per-shot SG values
                for (shot in holeStatWithHole.shots) {
                    val sg = breakdown.shotSgs.find { it.first == shot.shotNumber }?.second
                    val updated = shot.copy(strokesGained = sg)
                    if (updated != shot) {
                        roundRepository.updateShot(updated)
                    }
                }

                // And per-putt SG values
                for (putt in holeStatWithHole.putts) {
                    val sg = breakdown.puttSgs.find { it.first == putt.puttNumber }?.second
                    val updated = putt.copy(strokesGained = sg)
                    if (updated != putt) {
                        roundRepository.updatePutt(updated)
                    }
                }
            }
        }
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
