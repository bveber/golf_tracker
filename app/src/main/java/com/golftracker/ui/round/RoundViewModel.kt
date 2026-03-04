package com.golftracker.ui.round

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.golftracker.util.StrokesGainedCalculator
import androidx.lifecycle.viewModelScope
import com.golftracker.data.entity.Club
import com.golftracker.data.entity.Hole
import com.golftracker.data.entity.HoleStat
import com.golftracker.data.entity.HoleTeeYardage
import com.golftracker.data.entity.Penalty
import com.golftracker.data.entity.Putt
import com.golftracker.data.entity.Round
import com.golftracker.data.entity.TeeSet
import com.golftracker.data.model.ApproachLie
import com.golftracker.data.model.PenaltyType
import com.golftracker.data.model.ShotOutcome
import com.golftracker.data.repository.ClubRepository
import com.golftracker.data.repository.CourseRepository
import com.golftracker.data.repository.RoundRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

data class RoundUiState(
    val activeRound: Round? = null,
    val currentHoleStat: HoleStat? = null,
    val currentHole: Hole? = null,
    val currentHoleIndex: Int = 0,
    val holeStats: List<HoleStat> = emptyList(),
    val holes: List<Hole> = emptyList(),
    val shots: List<com.golftracker.data.entity.Shot> = emptyList(),
    val putts: List<Putt> = emptyList(),
    val penalties: List<Penalty> = emptyList(),
    val teeSets: List<TeeSet> = emptyList(),
    val yardages: Map<Int, Int> = emptyMap(), // holeId -> yardage for the selected tee set
    val isLoading: Boolean = false,
    val isRoundFinalized: Boolean = false
) {
    /** Cumulative over-par through all holes that have been scored (score > 0) */
    val cumulativeOverPar: Int
        get() {
            var total = 0
            holeStats.forEachIndexed { idx, stat ->
                if (stat.score > 0 && idx in holes.indices) {
                    total += stat.score - holes[idx].par
                }
            }
            return total
        }
    
    /** Yardage for the current hole from the selected tee set */
    val currentHoleYardage: Int?
        get() = currentHole?.let { yardages[it.id] }
}

@HiltViewModel
class RoundViewModel @Inject constructor(
    private val roundRepository: RoundRepository,
    private val courseRepository: CourseRepository,
    private val clubRepository: ClubRepository,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val roundIdArg: Int? = savedStateHandle.get<String>("roundId")?.toIntOrNull()
    private val initialHoleArg: Int? = savedStateHandle.get<Int>("initialHole")

    private val sgCalculator = StrokesGainedCalculator(context)

    private val _uiState = MutableStateFlow(RoundUiState())
    val uiState: StateFlow<RoundUiState> = _uiState.asStateFlow()

    val clubs: StateFlow<List<Club>> = clubRepository.activeClubs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        if (roundIdArg != null && roundIdArg != -1) {
            loadRound(roundIdArg)
        }
    }

    private fun loadRound(roundId: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val round = roundRepository.getRound(roundId)
            if (round != null) {
                val holes = courseRepository.getHoles(round.courseId).first()
                val stats = roundRepository.getHoleStatsForRound(roundId).first()
                
                // Load yardages for the selected tee set
                val yardageList = courseRepository.getYardagesForTeeSet(round.teeSetId).first()
                val yardageMap = yardageList.associate { it.holeId to it.yardage }
                
                _uiState.update { 
                    it.copy(
                        activeRound = round,
                        holes = holes,
                        holeStats = stats,
                        yardages = yardageMap,
                        isLoading = false
                    ) 
                }
                
                // Determine initial hole index
                val initialIndex = if (initialHoleArg != null && initialHoleArg != -1 && initialHoleArg in holes.indices) {
                    initialHoleArg
                } else {
                    // Jump to first incomplete hole if not specified
                    val firstIncompleteIndex = stats.indexOfFirst { it.score == 0 }
                    if (firstIncompleteIndex != -1) firstIncompleteIndex else 0
                }
                loadHoleDetails(initialIndex)
            }
        }
    }
    
    fun startRound(courseId: Int, teeSetId: Int) {
         viewModelScope.launch {
             _uiState.update { it.copy(isLoading = true) }
             val newRound = Round(courseId = courseId, teeSetId = teeSetId, date = Date())
             val roundId = roundRepository.insertRound(newRound).toInt()
             
             val holes = courseRepository.getHoles(courseId).first()
             
             holes.forEach { hole ->
                 roundRepository.insertHoleStat(HoleStat(roundId = roundId, holeId = hole.id))
             }
             
             loadRound(roundId)
         }
    }

    private fun loadHoleDetails(index: Int) {
        val state = uiState.value
        if (index in state.holes.indices && index in state.holeStats.indices) {
            val holeStat = state.holeStats[index]
            val hole = state.holes[index]
            viewModelScope.launch {
                val putts = roundRepository.getPuttsForHoleStat(holeStat.id).first()
                val penalties = roundRepository.getPenaltiesForHoleStat(holeStat.id).first()
                val shots = roundRepository.getShotsForHoleStat(holeStat.id).first()
                _uiState.update { 
                    it.copy(
                        currentHoleIndex = index,
                        currentHoleStat = holeStat,
                        currentHole = hole,
                        putts = putts,
                        penalties = penalties,
                        shots = shots
                    )
                }
            }
        }
    }

    fun nextHole() {
        val nextIndex = uiState.value.currentHoleIndex + 1
        if (nextIndex < uiState.value.holes.size) {
            loadHoleDetails(nextIndex)
        }
    }

    fun prevHole() {
        val prevIndex = uiState.value.currentHoleIndex - 1
        if (prevIndex >= 0) {
            loadHoleDetails(prevIndex)
        }
    }
    
    fun updateScore(score: Int) {
        val currentStat = uiState.value.currentHoleStat ?: return
        val updatedStat = currentStat.copy(score = score)
        updateStat(updatedStat)
    }
    
    fun updateTeeShot(outcome: ShotOutcome?, inTrouble: Boolean, clubId: Int?, distance: Int?) {
        val currentStat = uiState.value.currentHoleStat ?: return
        val updatedStat = currentStat.copy(
            teeOutcome = outcome,
            teeInTrouble = inTrouble,
            teeClubId = clubId,
            teeShotDistance = distance
        )
        updateStat(updatedStat)
    }
    
    // Old single-approach method - deprecated or reused if needed, but we typically use the list now. 
    // Keeping for compatibility if UI still references it, but we are moving to list.
    fun updateApproach(outcome: ShotOutcome?, lie: ApproachLie?, clubId: Int?, distance: Int?) {
        val currentStat = uiState.value.currentHoleStat ?: return
        val updatedStat = currentStat.copy(
            approachOutcome = outcome,
            approachLie = lie,
            approachClubId = clubId,
            approachShotDistance = distance
        )
        updateStat(updatedStat)
    }

    fun addApproachShot() {
        val currentStat = uiState.value.currentHoleStat ?: return
        val currentHole = uiState.value.currentHole ?: return
        
        viewModelScope.launch {
            val nextShotNumber = (uiState.value.shots.maxOfOrNull { it.shotNumber } ?: 0) + 1
            
            // Default distance logic for Par 3 first shot
            val defaultDistance = if (currentHole.par == 3 && nextShotNumber == 1) {
                uiState.value.currentHoleYardage
            } else {
                null
            }

            roundRepository.insertShot(
                com.golftracker.data.entity.Shot(
                    holeStatId = currentStat.id,
                    shotNumber = nextShotNumber,
                    distanceToPin = defaultDistance
                )
            )
            refreshShots(currentStat.id)
        }
    }


    
    // Correction: Shot entity does not have approachShotDistance field. It has distanceToPin.
    // I need to be careful with what fields I'm updating.
    fun updateShotDetails(shot: com.golftracker.data.entity.Shot, outcome: ShotOutcome?, lie: ApproachLie?, clubId: Int?, distanceToPin: Int?, isRecovery: Boolean) {
        viewModelScope.launch {
            roundRepository.updateShot(
                shot.copy(
                    outcome = outcome,
                    lie = lie,
                    clubId = clubId,
                    distanceToPin = distanceToPin,
                    isRecovery = isRecovery
                )
            )
            
            // Auto-populate Tee Shot Distance if this is the first approach shot on Par 4/5
            val currentHole = uiState.value.currentHole
            val currentStat = uiState.value.currentHoleStat
            val yardage = uiState.value.currentHoleYardage
            
            if (shot.shotNumber == 1 && currentHole != null && currentHole.par > 3 && currentStat != null) {
                val oldCalcTeeDist = if (shot.distanceToPin != null && yardage != null) yardage - shot.distanceToPin else null
                val newCalcTeeDist = if (distanceToPin != null && yardage != null) yardage - distanceToPin else null
                
                if (currentStat.teeShotDistance == null || currentStat.teeShotDistance == oldCalcTeeDist) {
                    if (newCalcTeeDist != null && newCalcTeeDist > 0) {
                        updateTeeShot(currentStat.teeOutcome, currentStat.teeInTrouble, currentStat.teeClubId, newCalcTeeDist)
                    } else if (currentStat.teeShotDistance == oldCalcTeeDist) {
                        updateTeeShot(currentStat.teeOutcome, currentStat.teeInTrouble, currentStat.teeClubId, null)
                    }
                }
            }
            
            refreshShots(shot.holeStatId)
        }
    }

    fun deleteApproachShot(shot: com.golftracker.data.entity.Shot) {
        viewModelScope.launch {
            roundRepository.deleteShot(shot)
            refreshShots(shot.holeStatId)
        }
    }
    
    private suspend fun refreshShots(holeStatId: Int) {
         val shots = roundRepository.getShotsForHoleStat(holeStatId).first()
         _uiState.update { it.copy(shots = shots) }
         recalculateSgForCurrentHole()
    }

    /** Find the club with the closest stock distance to a given yardage */
    fun suggestApproachClub(yardage: Int): Club? {
        val allClubs = clubs.value
        if (allClubs.isEmpty() || yardage <= 0) return null
        return allClubs
            .filter { it.stockDistance != null && it.type != "DRIVER" && it.type != "PUTTER" }
            .minByOrNull { kotlin.math.abs(it.stockDistance!! - yardage) }
    }

    /** Get the default tee club (first DRIVER in the bag) */
    fun defaultTeeClub(): Club? {
        return clubs.value.firstOrNull { it.type == "DRIVER" }
    }
    
    fun updateGreen(chips: Int, sandShots: Int, chipDistance: Int?, chipLie: com.golftracker.data.model.ApproachLie? = null, recoveryChip: Boolean = false) {
        val stat = uiState.value.currentHoleStat ?: return
        updateStat(stat.copy(chips = chips, sandShots = sandShots, chipDistance = chipDistance, chipLie = chipLie, recoveryChip = recoveryChip))
    }

    private fun updateStat(stat: HoleStat) {
        viewModelScope.launch {
            roundRepository.updateHoleStat(stat)
             _uiState.update { 
                 val newStats = it.holeStats.toMutableList()
                 newStats[it.currentHoleIndex] = stat
                 it.copy(currentHoleStat = stat, holeStats = newStats)
             }
             recalculateSgForCurrentHole()
        }
    }
    
    fun updatePutts(count: Int) {
        val currentStat = uiState.value.currentHoleStat ?: return
        updateStat(currentStat.copy(putts = count))
        
        viewModelScope.launch {
            val currentPutts = uiState.value.putts
            if (count > currentPutts.size) {
                for (i in currentPutts.size + 1..count) {
                    roundRepository.insertPutt(Putt(holeStatId = currentStat.id, puttNumber = i, distance = null))
                }
            } else if (count < currentPutts.size) {
                 val toRemove = currentPutts.takeLast(currentPutts.size - count)
                 toRemove.forEach { roundRepository.deletePutt(it) }
            }
            val newPutts = roundRepository.getPuttsForHoleStat(currentStat.id).first()
            _uiState.update { it.copy(putts = newPutts) }
            recalculateSgForCurrentHole()
        }
    }
    
    fun updatePuttDistance(putt: Putt, distance: Float?) {
        viewModelScope.launch {
            roundRepository.updatePutt(putt.copy(distance = distance))
            val newPutts = roundRepository.getPuttsForHoleStat(putt.holeStatId).first()
            _uiState.update { it.copy(putts = newPutts) }
            recalculateSgForCurrentHole()
        }
    }

    fun incrementPuttDistance(putt: Putt, delta: Float) {
        val currentDistance = putt.distance ?: 0f
        updatePuttDistance(putt, kotlin.math.max(0f, currentDistance + delta))
    }
    
    fun addPenalty(type: PenaltyType, strokes: Int) {
        val currentStat = uiState.value.currentHoleStat ?: return
        viewModelScope.launch {
            roundRepository.insertPenalty(Penalty(holeStatId = currentStat.id, type = type, strokes = strokes))
            val newPenalties = roundRepository.getPenaltiesForHoleStat(currentStat.id).first()
            _uiState.update { it.copy(penalties = newPenalties) }
            recalculateSgForCurrentHole()
        }
    }
    
    fun removePenalty(penalty: Penalty) {
        viewModelScope.launch {
            roundRepository.deletePenalty(penalty)
            val currentStat = uiState.value.currentHoleStat ?: return@launch
            val newPenalties = roundRepository.getPenaltiesForHoleStat(currentStat.id).first()
            _uiState.update { it.copy(penalties = newPenalties) }
            recalculateSgForCurrentHole()
        }
    }

    fun finalizeRound() {
        val round = uiState.value.activeRound ?: return
        viewModelScope.launch {
            roundRepository.updateRound(round.copy(isFinalized = true))
             _uiState.update { it.copy(isRoundFinalized = true) }
        }
    }

    private suspend fun recalculateSgForCurrentHole() {
        val state = uiState.value
        val stat = state.currentHoleStat ?: return
        val hole = state.currentHole ?: return
        val round = state.activeRound ?: return
        
        val teeSet = courseRepository.getTeeSets(round.courseId).first().find { it.id == round.teeSetId } ?: return
        val coursePar = courseRepository.getHoles(round.courseId).first().sumOf { it.par }
        
        val yardages = courseRepository.getYardagesForTeeSet(round.teeSetId).first()
        val holeYardage = yardages.find { it.holeId == hole.id }?.yardage ?: return
        
        val shots = roundRepository.getShotsForHoleStat(stat.id).first().sortedBy { it.shotNumber }
        val putts = roundRepository.getPuttsForHoleStat(stat.id).first().sortedBy { it.puttNumber }
        val penalties = roundRepository.getPenaltiesForHoleStat(stat.id).first()
        
        var totalSg = 0.0
        var sgOffTee = 0.0
        var sgApproach = 0.0
        var sgAroundGreen = 0.0
        var sgPutting = 0.0
        
        // 1. TEE SHOT (Par 4/5)
        if (hole.par > 3 && (stat.score > 0 || stat.teeOutcome != null)) {
            val startDistance = holeYardage
            val startLie = ApproachLie.TEE
            
            var endDist = 0
            var endLie: ApproachLie? = null
            var greenFeet: Float? = null
            
            if (shots.isNotEmpty()) {
                endDist = shots.first().distanceToPin ?: 0
                endLie = shots.first().lie
            } else if (stat.chips > 0 || stat.sandShots > 0) {
                endDist = stat.chipDistance ?: 15
                endLie = if (stat.sandShots > 0) ApproachLie.SAND else ApproachLie.ROUGH
            } else if (putts.isNotEmpty()) {
                greenFeet = putts.first().distance
            }
            
            val teeSg = sgCalculator.calculateShotSG(startDistance, startLie, true, endDist, endLie, greenFeet, 0, teeSet.rating.toDouble(), teeSet.slope, coursePar, hole.handicapIndex)
            sgOffTee += teeSg
        }
        
        // 2. APPROACH SHOTS
        for (i in shots.indices) {
            val shot = shots[i]
            val isFirstShotOfPar3 = (hole.par == 3 && i == 0) // UI asks for approach on par 3 tee
            
            val startDistance = shot.distanceToPin ?: if (isFirstShotOfPar3) holeYardage else continue
            val startLie = if (isFirstShotOfPar3) ApproachLie.TEE else shot.lie ?: ApproachLie.FAIRWAY
            
            var endDist = 0
            var endLie: ApproachLie? = null
            var greenFeet: Float? = null
            
            if (i + 1 < shots.size) {
                endDist = shots[i+1].distanceToPin ?: 0
                endLie = shots[i+1].lie 
            } else if (stat.chips > 0 || stat.sandShots > 0) {
                endDist = stat.chipDistance ?: 15
                endLie = if (stat.sandShots > 0) ApproachLie.SAND else ApproachLie.ROUGH
            } else if (putts.isNotEmpty()) {
                greenFeet = putts.first().distance
            }
            
            val sg = sgCalculator.calculateShotSG(startDistance, startLie, isFirstShotOfPar3, endDist, endLie, greenFeet, 0, teeSet.rating.toDouble(), teeSet.slope, coursePar, hole.handicapIndex)
            roundRepository.updateShot(shot.copy(strokesGained = sg))
            
            if (isFirstShotOfPar3) {
                sgOffTee += sg 
            } else {
                sgApproach += sg
            }
        }

        // 3. SHORT GAME (Chips / Sand Shots)
        val shortGameStrokes = stat.chips + stat.sandShots
        if (shortGameStrokes > 0) {
            val startDistance = stat.chipDistance ?: 15
            val startLie = stat.chipLie ?: if (stat.sandShots > 0) ApproachLie.SAND else ApproachLie.ROUGH
            val expectedStart = sgCalculator.getExpectedStrokes(startDistance, startLie, false)
            
            val greenFeet = putts.firstOrNull()?.distance
            val expectedEnd = if (greenFeet != null) sgCalculator.getExpectedPutts(greenFeet) else 0.0
            
            val shortGameSg = expectedStart - expectedEnd - shortGameStrokes
            sgAroundGreen += shortGameSg
        }

        // 4. PUTTS
        for (i in putts.indices) {
            val putt = putts[i]
            val startFeet = putt.distance ?: continue
            val nextFeet = if (i + 1 < putts.size) putts[i+1].distance else null
            val made = (i == putts.size - 1) && (stat.score > 0 || putt.made)
            
            if (!made && nextFeet == null) continue
            
            val sg = sgCalculator.calculatePuttSG(startFeet, made, nextFeet)
            roundRepository.updatePutt(putt.copy(strokesGained = sg))
            sgPutting += sg
        }
        
        totalSg = sgOffTee + sgApproach + sgAroundGreen + sgPutting
        totalSg -= penalties.sumOf { it.strokes }
        
        val calculatedScore = (if (hole.par > 3 && stat.teeOutcome != null) 1 else 0) + 
            shots.size + 
            shortGameStrokes + 
            stat.putts + 
            penalties.sumOf { it.strokes }
            
        val newScore = if (calculatedScore > 0) calculatedScore else stat.score
        
        val hasData = newScore > 0 || stat.teeOutcome != null || shots.isNotEmpty() || putts.isNotEmpty() || shortGameStrokes > 0
        
        val updatedStat = stat.copy(
            score = newScore,
            strokesGained = if (hasData) totalSg else null,
            sgOffTee = if (hasData) sgOffTee else null,
            sgApproach = if (hasData) sgApproach else null,
            sgAroundGreen = if (hasData) sgAroundGreen else null,
            sgPutting = if (hasData) sgPutting else null
        )
        
        roundRepository.updateHoleStat(updatedStat)
        
        _uiState.update { 
            val newStats = it.holeStats.toMutableList()
            newStats[it.currentHoleIndex] = updatedStat
            it.copy(currentHoleStat = updatedStat, holeStats = newStats)
        }
    }
}
