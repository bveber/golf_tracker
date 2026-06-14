package com.golftracker.ui.courseanalysis

import com.golftracker.data.entity.Course
import com.golftracker.data.entity.PuttBreak
import com.golftracker.data.entity.PuttSlopeDirection
import com.golftracker.data.entity.TeeSet
import com.golftracker.data.repository.DispersionPoint
import com.golftracker.data.repository.DistanceBucket
import com.golftracker.data.repository.RoundScoreSummary
import java.util.Date

data class CourseAnalysisFilter(
    val teeSetId: Int? = null,
    val year: Int? = null,
    val startDate: Date? = null,
    val endDate: Date? = null
)

data class OverallScoringStats(
    val roundsPlayed: Int = 0,
    val avgScore: Double = 0.0,
    val avgToPar: Double = 0.0,
    val bestRoundToPar: Int = 0,
    val worstRoundToPar: Int = 0,
    val eagles: Int = 0,
    val birdies: Int = 0,
    val pars: Int = 0,
    val bogeys: Int = 0,
    val doubles: Int = 0,
    val worseCount: Int = 0,
    val trend: List<RoundScoreSummary> = emptyList()
)

data class HoleAnalysis(
    val holeNumber: Int,
    val par: Int,
    val roundsPlayed: Int,
    val avgScore: Double,
    val medianScore: Double,
    val eagles: Int,
    val birdies: Int,
    val pars: Int,
    val bogeys: Int,
    val doubles: Int,
    val worseCount: Int
)

data class HoleSgBreakdown(
    val holeNumber: Int,
    val par: Int,
    val roundsPlayed: Int,
    val avgSgTotal: Double,
    val avgSgOffTee: Double?,
    val avgSgApproach: Double,
    val avgSgAroundGreen: Double,
    val avgSgPutting: Double,
    val avgExpectedStrokes: Double
)

data class MissBreakdown(
    val totalShots: Int = 0,
    val leftPct: Float = 0f,
    val rightPct: Float = 0f,
    val shortPct: Float = 0f,
    val longPct: Float = 0f,
    val onTargetPct: Float = 0f,
    val avgLateral: Double = 0.0,
    val avgDistance: Double = 0.0,
    val dispersionPoints: List<DispersionPoint> = emptyList()
)

data class CourseMissStats(
    val tee: MissBreakdown = MissBreakdown(),
    val approach: MissBreakdown = MissBreakdown(),
    val chip: MissBreakdown = MissBreakdown()
)

data class PuttBreakStats(
    val breakDir: PuttBreak,
    val count: Int,
    val makePct: Float
)

data class PuttSlopeStats(
    val slope: PuttSlopeDirection,
    val count: Int,
    val makePct: Float
)

data class ClubTeeStats(
    val clubId: Int,
    val clubName: String,
    val clubType: String,
    val holesPlayed: Int,
    val avgSgOffTee: Double?,
    val avgSgTotal: Double,
    val avgToPar: Double,
    val onTargetPct: Float,
    val mishitPct: Float,
    val missLeftPct: Float,
    val missRightPct: Float,
    val missShortPct: Float,
    val missLongPct: Float
)

data class HoleClubBreakdown(
    val holeNumber: Int,
    val par: Int,
    val byClub: List<ClubTeeStats>
)

data class CoursePuttingStats(
    val avgPuttsPerRound: Double = 0.0,
    val onePuttPct: Float = 0f,
    val twoPuttPct: Float = 0f,
    val threePlusPct: Float = 0f,
    val makePctByDistance: List<DistanceBucket> = emptyList(),
    val avgFirstPuttDistance: Double = 0.0,
    val missShortPct: Float = 0f,
    val missLongPct: Float = 0f,
    val missLeftPct: Float = 0f,
    val missRightPct: Float = 0f,
    val totalMissedPutts: Int = 0,
    val breakStats: List<PuttBreakStats> = emptyList(),
    val slopeStats: List<PuttSlopeStats> = emptyList()
)

data class CourseAnalysisData(
    val course: Course,
    val teeSets: List<TeeSet> = emptyList(),
    val availableYears: List<Int> = emptyList(),
    val overall: OverallScoringStats = OverallScoringStats(),
    val byHole: List<HoleAnalysis> = emptyList(),
    val holeSgBreakdowns: List<HoleSgBreakdown> = emptyList(),
    val missStats: CourseMissStats = CourseMissStats(),
    val putting: CoursePuttingStats = CoursePuttingStats(),
    val clubTeeStats: List<ClubTeeStats> = emptyList(),
    val holeClubBreakdowns: List<HoleClubBreakdown> = emptyList()
)

data class CourseWithRoundCount(
    val course: Course,
    val roundCount: Int,
    val lastPlayed: Date
)

sealed interface CourseAnalysisUiState {
    data object Loading : CourseAnalysisUiState
    data class Success(val data: CourseAnalysisData) : CourseAnalysisUiState
    data class Error(val message: String) : CourseAnalysisUiState
}
