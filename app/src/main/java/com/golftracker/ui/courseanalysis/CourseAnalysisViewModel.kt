package com.golftracker.ui.courseanalysis

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.golftracker.data.repository.ClubRepository
import com.golftracker.data.repository.StatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.util.Date
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CourseAnalysisViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val statsRepository: StatsRepository,
    clubRepository: ClubRepository
) : ViewModel() {

    val courseId: Int = checkNotNull(savedStateHandle["courseId"])

    private val _filter = MutableStateFlow(CourseAnalysisFilter())
    val filter: StateFlow<CourseAnalysisFilter> = _filter

    val uiState: StateFlow<CourseAnalysisUiState> = combine(_filter, clubRepository.activeClubs) { f, clubs -> f to clubs }
        .flatMapLatest { (f, clubs) -> statsRepository.getCourseAnalysisData(courseId, f, clubs) }
        .map { data -> CourseAnalysisUiState.Success(data) as CourseAnalysisUiState }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CourseAnalysisUiState.Loading
        )

    fun updateTeeSetFilter(teeSetId: Int?) {
        _filter.update { it.copy(teeSetId = teeSetId) }
    }

    fun updateYearFilter(year: Int?) {
        _filter.update { it.copy(year = year, startDate = null, endDate = null) }
    }

    fun updateStartDate(date: Date?) {
        _filter.update { it.copy(startDate = date, year = null) }
    }

    fun updateEndDate(date: Date?) {
        _filter.update { it.copy(endDate = date, year = null) }
    }

    fun clearFilters() {
        _filter.update { CourseAnalysisFilter() }
    }
}
