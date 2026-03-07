package com.golftracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.golftracker.data.entity.Round
import com.golftracker.data.repository.RoundRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.content.Context
import com.golftracker.util.JsonExporter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject

import com.golftracker.data.repository.CourseRepository
import com.golftracker.util.ShotDistanceCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val roundRepository: RoundRepository,
    private val courseRepository: CourseRepository,
    private val jsonExporter: JsonExporter,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val activeRound: StateFlow<Round?> = roundRepository.activeRound
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            calculateMissingDistances()
        }
    }

    private suspend fun calculateMissingDistances() = withContext(Dispatchers.IO) {
        val allRounds = roundRepository.finalizedRoundsWithDetails.first()
        for (roundDetails in allRounds) {
            val yardages = courseRepository.getYardagesForTeeSet(roundDetails.teeSet.id).first()
            
            for (holeStatWithHole in roundDetails.holeStats) {
                val shots = holeStatWithHole.shots.sortedBy { it.shotNumber }
                if (shots.isEmpty()) continue
                
                val holeYardage = yardages.find { it.holeId == holeStatWithHole.hole.id }?.yardage ?: continue
                
                for (i in shots.indices) {
                    val shot = shots[i]
                    if (shot.distanceTraveled == null && shot.distanceToPin != null) {
                        // Calculate missing distance for shot[i]
                        // startDist is WHERE THIS SHOT STARTED (shot.distanceToPin)
                        // endDist is WHERE THIS SHOT ENDED (the next shot's start dist, or chip/putt dist)
                        val startDist = shot.distanceToPin ?: continue
                        
                        val endDist = if (i + 1 < shots.size) {
                            shots[i + 1].distanceToPin ?: 0
                        } else if (holeStatWithHole.holeStat.chips > 0 || holeStatWithHole.holeStat.sandShots > 0) {
                            holeStatWithHole.holeStat.chipDistance ?: 15
                        } else if (holeStatWithHole.putts.isNotEmpty()) {
                            (holeStatWithHole.putts.first().distance?.toInt() ?: 0) / 3 // feet to yards
                        } else {
                            0
                        }
                        
                        val newDistanceTraveled = ShotDistanceCalculator.estimateShotDistance(
                            startDist = startDist, 
                            endDist = endDist, 
                            outcome = shot.outcome
                        )
                        roundRepository.updateShot(shot.copy(distanceTraveled = newDistanceTraveled))
                    }
                }
            }
        }
    }

    fun deleteRound(round: Round) {
        viewModelScope.launch {
            roundRepository.deleteRound(round)
        }
    }

    private val _exportFileEvent = MutableSharedFlow<File?>()
    val exportFileEvent: SharedFlow<File?> = _exportFileEvent.asSharedFlow()

    fun exportAllData() {
        viewModelScope.launch {
            val allRounds = roundRepository.finalizedRoundsWithDetails.first()
            val file = jsonExporter.exportAllDataToCache(context, allRounds)
            _exportFileEvent.emit(file)
        }
    }
}
