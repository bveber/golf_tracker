package com.golftracker.ui.bag

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.golftracker.data.entity.Club
import com.golftracker.data.repository.ClubRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BagViewModel @Inject constructor(
    private val clubRepository: ClubRepository
) : ViewModel() {

    val clubs: StateFlow<List<Club>> = clubRepository.activeClubs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            if (clubRepository.activeClubs.first().isEmpty()) {
                prepopulateBag()
            }
        }
    }

    private suspend fun prepopulateBag() {
        val defaultClubs = listOf(
            Club(name = "Driver", type = "DRIVER", sortOrder = 0, stockDistance = 250),
            Club(name = "3 Wood", type = "WOOD", sortOrder = 1, stockDistance = 230),
            Club(name = "5 Wood", type = "WOOD", sortOrder = 2, stockDistance = 210),
            Club(name = "4 Hybrid", type = "HYBRID", sortOrder = 3, stockDistance = 200),
            Club(name = "5 Iron", type = "IRON", sortOrder = 4, stockDistance = 185),
            Club(name = "6 Iron", type = "IRON", sortOrder = 5, stockDistance = 175),
            Club(name = "7 Iron", type = "IRON", sortOrder = 6, stockDistance = 165),
            Club(name = "8 Iron", type = "IRON", sortOrder = 7, stockDistance = 155),
            Club(name = "9 Iron", type = "IRON", sortOrder = 8, stockDistance = 140),
            Club(name = "Pitching Wedge", type = "WEDGE", sortOrder = 9, stockDistance = 130),
            Club(name = "Gap Wedge", type = "WEDGE", sortOrder = 10, stockDistance = 115),
            Club(name = "Sand Wedge", type = "WEDGE", sortOrder = 11, stockDistance = 100),
            Club(name = "Lob Wedge", type = "WEDGE", sortOrder = 12, stockDistance = 80),
            Club(name = "Putter", type = "PUTTER", sortOrder = 13)
        )
        defaultClubs.forEach { clubRepository.insertClub(it) }
    }

    fun addClub(name: String, type: String, stockDistance: Int?) {
        viewModelScope.launch {
            val maxOrder = clubs.value.maxOfOrNull { it.sortOrder } ?: -1
            clubRepository.insertClub(
                Club(name = name, type = type, sortOrder = maxOrder + 1, stockDistance = stockDistance)
            )
        }
    }

    fun updateClub(club: Club) {
        viewModelScope.launch {
            clubRepository.updateClub(club)
        }
    }

    fun deleteClub(club: Club) {
        viewModelScope.launch {
            clubRepository.deleteClub(club)
        }
    }

    fun retireClub(club: Club) {
        viewModelScope.launch {
            clubRepository.updateClub(club.copy(isRetired = true))
        }
    }

    fun updateClubOrder(orderedClubs: List<Club>) {
        viewModelScope.launch {
            orderedClubs.forEachIndexed { index, club ->
                if (club.sortOrder != index) {
                    clubRepository.updateClub(club.copy(sortOrder = index))
                }
            }
        }
    }
}
