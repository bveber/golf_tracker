package com.golftracker.ui.round

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.golftracker.data.entity.Round
import com.golftracker.data.repository.CourseRepository
import com.golftracker.data.repository.RoundRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    }// To get course name, we need a relation.
    // I'll stick to simple Round list for now, maybe add Course name fetching later if complex.
    
    val roundsWithDetails: StateFlow<List<com.golftracker.data.model.RoundWithDetails>> = roundRepository.finalizedRoundsWithDetails
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _exportFileEvent = MutableSharedFlow<File?>()
    val exportFileEvent: SharedFlow<File?> = _exportFileEvent.asSharedFlow()

    data class RoundScoreData(val score: Int, val toPar: Int, val totalSg: Double)

    fun getRoundScore(roundWithDetails: com.golftracker.data.model.RoundWithDetails): RoundScoreData {
        val stats = roundWithDetails.holeStats
        var totalScore = 0
        var totalPar = 0
        var totalSg = 0.0
        stats.forEach { stat ->
             if (stat.holeStat.score > 0) {
                 totalScore += stat.holeStat.score
                 totalPar += stat.hole.par
             }
             if (stat.holeStat.strokesGained != null) {
                 totalSg += stat.holeStat.strokesGained
             }
        }
        return RoundScoreData(totalScore, totalScore - totalPar, totalSg)
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
}
