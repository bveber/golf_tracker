package com.golftracker.ui.handicap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.golftracker.data.repository.RoundRepository
import com.golftracker.data.repository.UserPreferencesRepository
import com.golftracker.util.HandicapCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class HandicapUiState(
    val handicapIndex: Double? = null,
    val estimatedHandicap: Double? = null,
    val differentials: List<HandicapCalculator.Differential> = emptyList(),
    val timeSeries: List<HandicapCalculator.HandicapPoint> = emptyList(),
    val availableYears: List<Int> = emptyList(),
    val selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val isLoading: Boolean = true
)

@HiltViewModel
class HandicapViewModel @Inject constructor(
    roundRepository: RoundRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val uiState: StateFlow<HandicapUiState> = combine(
        roundRepository.finalizedRoundsWithDetails,
        userPreferencesRepository.estimatedHandicapFlow
    ) { rounds, estimatedHandicap ->
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val competitiveRounds = rounds.filter { !it.round.isPractice }
        val index = HandicapCalculator.calculateHandicapIndex(competitiveRounds)
        val diffs = HandicapCalculator.calculateDifferentials(competitiveRounds, currentYear)
        val series = HandicapCalculator.buildHandicapTimeSeries(competitiveRounds)
        val years = competitiveRounds
            .map { Calendar.getInstance().also { c -> c.time = it.round.date }.get(Calendar.YEAR) }
            .distinct()
            .sortedDescending()

        // Clear the estimated handicap if an official index is now available
        if (index != null && estimatedHandicap != null) {
            viewModelScope.launch {
                userPreferencesRepository.setEstimatedHandicap(null)
            }
        }

        HandicapUiState(
            handicapIndex = index,
            estimatedHandicap = estimatedHandicap,
            differentials = diffs,
            timeSeries = series,
            availableYears = years,
            selectedYear = currentYear,
            isLoading = false
        )
    }
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HandicapUiState()
    )

    fun saveEstimatedHandicap(value: Double?) {
        viewModelScope.launch {
            userPreferencesRepository.setEstimatedHandicap(value)
        }
    }
}
