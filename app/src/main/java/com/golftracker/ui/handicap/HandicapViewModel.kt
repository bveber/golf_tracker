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
import javax.inject.Inject

data class HandicapUiState(
    val handicapIndex: Double? = null,
    val estimatedHandicap: Double? = null,
    val differentials: List<HandicapCalculator.Differential> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class HandicapViewModel @Inject constructor(
    roundRepository: RoundRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val uiState: StateFlow<HandicapUiState> = kotlinx.coroutines.flow.combine(
        roundRepository.finalizedRoundsWithDetails,
        userPreferencesRepository.estimatedHandicapFlow
    ) { rounds, estimatedHandicap ->
        val index = HandicapCalculator.calculateHandicapIndex(rounds)
        val diffs = HandicapCalculator.calculateDifferentials(rounds)
        
        // Clear the estimated handicap if 3 or more rounds have been tracked
        if (index != null && estimatedHandicap != null) {
            viewModelScope.launch {
                userPreferencesRepository.setEstimatedHandicap(null)
            }
        }

        HandicapUiState(
            handicapIndex = index,
            estimatedHandicap = estimatedHandicap,
            differentials = diffs,
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
