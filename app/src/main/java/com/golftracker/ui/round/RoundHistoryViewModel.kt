package com.golftracker.ui.round

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.golftracker.data.entity.HoleStat
import com.golftracker.data.entity.Round
import com.golftracker.data.repository.CourseRepository
import com.golftracker.data.repository.RoundRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

import android.content.Context
import com.golftracker.util.JsonExporter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File

@HiltViewModel
class RoundHistoryViewModel @Inject constructor(
    private val roundRepository: RoundRepository,
    private val courseRepository: CourseRepository,
    private val jsonExporter: JsonExporter,
    private val sgRecalculationUseCase: com.golftracker.domain.SgRecalculationUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // Ideally this would be a joined query returning RoundWithCourse info
    // For MVP, just Rounds. Or we can update DAO to return POJO.
    // Let's assume for now we list rounds and maybe fetch course names if needed or display ID/Date.
    // Plan: "Lists past rounds: date, course, score, vs par"
    
    // Simple cache of course names
    private val _courseNames = kotlinx.coroutines.flow.MutableStateFlow<Map<Int, String>>(emptyMap())
    val courseNames: StateFlow<Map<Int, String>> = _courseNames

    init {
        viewModelScope.launch {
            val courses = courseRepository.allCourses.first()
            _courseNames.value = courses.associate { it.id to it.name }
        }
    }
    val roundsWithDetails: StateFlow<List<RoundHistoryItem>> = combine(
        roundRepository.finalizedRoundsWithDetails,
        courseRepository.allYardages,
        courseRepository.allHoles
    ) { rounds, yardages, holes ->
        val yardageMap = yardages.associateBy { it.teeSetId to it.holeId }
        val parMap = holes.groupBy { it.courseId }
            .mapValues { (_, courseHoles) -> courseHoles.sumOf { it.par } }
        val distSumMap = yardages.groupBy { it.teeSetId }
            .mapValues { (_, holes) -> holes.sumOf { it.yardage } }
            
        rounds.map { round ->
            RoundHistoryItem(
                roundWithDetails = round,
                scoreData = calculateRoundScore(round, yardageMap, parMap, distSumMap)
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    data class RoundHistoryItem(
        val roundWithDetails: com.golftracker.data.model.RoundWithDetails,
        val scoreData: RoundScoreData
    )

    private val _exportFileEvent = MutableSharedFlow<File?>()
    val exportFileEvent: SharedFlow<File?> = _exportFileEvent.asSharedFlow()

    data class RoundScoreData(
        val score: Int, 
        val toPar: Int, 
        val totalSg: Double,
        val teeName: String,
        val rating: Double,
        val slope: Int,
        val totalDistance: Int
    )

    private fun calculateRoundScore(
        roundWithDetails: com.golftracker.data.model.RoundWithDetails,
        yardageMap: Map<Pair<Int, Int>, com.golftracker.data.entity.HoleTeeYardage>,
        parMap: Map<Int, Int>,
        distSumMap: Map<Int, Int>
    ): RoundScoreData {
        val stats = roundWithDetails.holeStats
        var totalScore = 0
        var totalPar = 0
        var totalSg = 0.0
        
        val teeSetId = roundWithDetails.round.teeSetId
            
        stats.forEach { hole ->
            val stat = hole.holeStat
            if (stat.score > 0) {
                totalScore += stat.score
                totalPar += hole.hole.par
            }
            totalSg += stat.strokesGained ?: 0.0
        }
        
        return RoundScoreData(
            score = totalScore,
            toPar = if (totalScore > 0) totalScore - totalPar else 0,
            totalSg = totalSg,
            teeName = roundWithDetails.teeSet.name,
            rating = roundWithDetails.teeSet.rating.toDouble(),
            slope = roundWithDetails.teeSet.slope,
            totalDistance = distSumMap[teeSetId] ?: 0
        )
    }

    fun exportRound(roundId: Int) {
        viewModelScope.launch {
            val roundDetails = roundRepository.getRoundWithDetails(roundId)
            if (roundDetails != null) {
                // Use IO dispatcher for file operations if possible, but cache dir is fast enough for MVP
                val file = jsonExporter.exportRoundToCache(context, roundDetails)
                _exportFileEvent.emit(file)
            }
        }
    }

    fun deleteRound(roundId: Int) {
        viewModelScope.launch {
            val round = roundRepository.getRound(roundId)
            if (round != null) {
                roundRepository.deleteRound(round)
            }
        }
    }

    fun togglePracticeRound(roundId: Int) {
        viewModelScope.launch {
            val round = roundRepository.getRound(roundId)
            if (round != null) {
                roundRepository.updateRound(round.copy(isPractice = !round.isPractice))
            }
        }
    }
}
