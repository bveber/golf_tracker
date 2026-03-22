package com.golftracker.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.golftracker.data.entity.Club
import com.golftracker.data.entity.Course
import com.golftracker.data.entity.TeeSet
import com.golftracker.data.repository.ClubRepository
import com.golftracker.data.repository.StatsData
import com.golftracker.data.repository.StatsFilter
import com.golftracker.data.repository.StatsRepository
import com.golftracker.data.repository.CourseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.util.Calendar
import javax.inject.Inject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val statsRepository: StatsRepository,
    courseRepository: CourseRepository,
    clubRepository: ClubRepository
) : ViewModel() {

    private val _filter = MutableStateFlow(StatsFilter())
    val filter: StateFlow<StatsFilter> = _filter

    val courses: StateFlow<List<Course>> = courseRepository.allCourses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val clubs: StateFlow<List<Club>> = clubRepository.activeClubs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Tee sets for the selected course
    private val _teeSetsForCourse = MutableStateFlow<List<TeeSet>>(emptyList())
    val teeSetsForCourse: StateFlow<List<TeeSet>> = _teeSetsForCourse

    // Available years extracted from rounds
    val availableYears: StateFlow<List<Int>> = statsRepository.getStatsData()
        .map { data ->
            val cal = Calendar.getInstance()
            data.rounds.map {
                cal.time = it.round.date
                cal.get(Calendar.YEAR)
            }.distinct().sorted().reversed()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val statsUiState: StateFlow<StatsUiState> = _filter
        .flatMapLatest { filter ->
            statsRepository.getFilteredStatsData(filter)
        }
        .map { data -> StatsUiState.Success(data) as StatsUiState }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = StatsUiState.Loading
        )

    fun updateCourseFilter(courseId: Int?) {
        _filter.update { it.copy(courseId = courseId, teeSetId = null) }
        // Load tee sets for the selected course
        if (courseId != null) {
            val repo = statsRepository // unused, but we need CourseRepository
            // We'll collect tee sets via a launched coroutine
            // For simplicity, extract from courses flow
        }
    }

    fun updateTeeSetFilter(teeSetId: Int?) {
        _filter.update { it.copy(teeSetId = teeSetId) }
    }

    fun updateLastNRounds(n: Int) {
        _filter.update { it.copy(lastNRounds = n) }
    }

    fun updateYearFilter(year: Int?) {
        _filter.update { it.copy(year = year) }
    }

    fun updateStartDate(date: java.util.Date?) {
        _filter.update { it.copy(startDate = date, year = null) }
    }

    fun updateEndDate(date: java.util.Date?) {
        _filter.update { it.copy(endDate = date, year = null) }
    }

    fun toggleRoundExclusion(roundId: Int) {
        _filter.update { 
            val newExcluded = it.excludedRoundIds.toMutableSet()
            if (newExcluded.contains(roundId)) {
                newExcluded.remove(roundId)
            } else {
                newExcluded.add(roundId)
            }
            it.copy(excludedRoundIds = newExcluded)
        }
    }

    fun clearDateFilters() {
        _filter.update { it.copy(startDate = null, endDate = null, year = null) }
    }

    fun clearFilters() {
        _filter.update { StatsFilter() }
    }

    fun updateDrivingClubFilter(clubId: Int?) {
        _filter.update { it.copy(drivingClubId = clubId) }
    }

    fun updateApproachClubFilter(clubId: Int?) {
        _filter.update { it.copy(approachClubId = clubId) }
    }

    fun updateMishitFilter(include: Boolean) {
        _filter.update { it.copy(includeMishits = include) }
    }
}

sealed interface StatsUiState {
    data object Loading : StatsUiState
    data class Success(val data: StatsData) : StatsUiState
    data class Error(val message: String) : StatsUiState
}
