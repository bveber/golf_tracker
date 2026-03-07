package com.golftracker.ui.round

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.golftracker.data.entity.Course
import com.golftracker.data.entity.Round
import com.golftracker.data.entity.TeeSet
import com.golftracker.data.repository.CourseRepository
import com.golftracker.data.repository.RoundRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

data class RoundSetupUiState(
    val courses: List<Course> = emptyList(),
    val selectedCourse: Course? = null,
    val teeSets: List<TeeSet> = emptyList(),
    val selectedTeeSet: TeeSet? = null,
    val date: Date = Date(),
    val notes: String = "",
    val teeYardages: Map<Int, Int> = emptyMap(), // teeSetId to totalYardage
    val isLoading: Boolean = false,
    val createdRoundId: Int? = null
)

@HiltViewModel
class RoundSetupViewModel @Inject constructor(
    private val courseRepository: CourseRepository,
    private val roundRepository: RoundRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RoundSetupUiState())
    val uiState: StateFlow<RoundSetupUiState> = _uiState.asStateFlow()

    init {
        loadCourses()
    }

    private fun loadCourses() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val courses = courseRepository.allCourses.first()
            _uiState.update { it.copy(courses = courses, isLoading = false) }
        }
    }

    fun selectCourse(course: Course) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, selectedCourse = course, teeSets = emptyList(), selectedTeeSet = null, teeYardages = emptyMap()) }
            val teeSets = courseRepository.getTeeSets(course.id).first()
            
            val yardagesMap = mutableMapOf<Int, Int>()
            for (teeSet in teeSets) {
                val yardages = courseRepository.getYardagesForTeeSet(teeSet.id).first()
                yardagesMap[teeSet.id] = yardages.sumOf { it.yardage }
            }

            _uiState.update { 
                it.copy(
                    teeSets = teeSets, 
                    selectedTeeSet = teeSets.firstOrNull(), // Default to first
                    teeYardages = yardagesMap,
                    isLoading = false
                ) 
            }
        }
    }

    fun selectTeeSet(teeSet: TeeSet) {
        _uiState.update { it.copy(selectedTeeSet = teeSet) }
    }

    fun updateDate(date: Date) {
        _uiState.update { it.copy(date = date) }
    }
    
    fun updateNotes(notes: String) {
        _uiState.update { it.copy(notes = notes) }
    }

    fun startRound() {
        val state = uiState.value
        if (state.selectedCourse != null && state.selectedTeeSet != null) {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }
                val newRound = Round(
                    courseId = state.selectedCourse.id,
                    teeSetId = state.selectedTeeSet.id,
                    date = state.date,
                    notes = state.notes
                )
                val roundId = roundRepository.insertRound(newRound).toInt()
                
                // Initialize hole stats
                val holes = courseRepository.getHoles(state.selectedCourse.id).first()
                holes.forEach { hole ->
                    // Correctly initialize with roundId
                    roundRepository.insertHoleStat(
                        com.golftracker.data.entity.HoleStat(
                            roundId = roundId,
                            holeId = hole.id
                        )
                    )
                }

                _uiState.update { it.copy(createdRoundId = roundId, isLoading = false) }
            }
        }
    }
    
    fun resetCreatedRoundId() {
        _uiState.update { it.copy(createdRoundId = null) }
    }
}
