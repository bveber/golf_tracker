package com.golftracker.ui.round

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.golftracker.util.ShotDistanceCalculator
import com.golftracker.util.StrokesGainedCalculator
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.ViewModel
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.Flow
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
    /** Cumulative over-par through all holes that have been committed (isScored == true) */
    val cumulativeOverPar: Int
        get() {
            var total = 0
            val holeMap = holes.associateBy { it.id }
            
            holeStats.forEach { stat ->
                val hole = holeMap[stat.holeId]
                // Include any committed hole in the "Current Score"
                if (stat.isScored && stat.score > 0 && hole != null) {
                    total += stat.score - hole.par
                }
            }
            return total
        }
    
    /** Yardage for the current hole from the selected tee set, or the user override. */
    val currentHoleYardage: Int?
        get() = currentHoleStat?.adjustedYardage ?: currentHole?.let { yardages[it.id] }
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

    private var holeDetailJob: Job? = null
    private var maintenanceJob: Job? = null

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
                // One-time load of holes and yardages
                val allHoles = courseRepository.getHoles(round.courseId).first()
                val yardageList = courseRepository.getYardagesForTeeSet(round.teeSetId).first()
                val yardageMap = yardageList.associate { it.holeId to it.yardage }

                // Reactive collection of hole stats for the entire round
                roundRepository.getHoleStatsForRound(roundId).collect { stats ->
                    val sortedHoles = allHoles.filter { h -> stats.any { it.holeId == h.id } }.sortedBy { it.holeNumber }
                    val sortedStats = stats.sortedBy { s -> sortedHoles.find { h -> h.id == s.holeId }?.holeNumber ?: 0 }

                    _uiState.update { state ->
                        state.copy(
                            activeRound = round,
                            holes = sortedHoles,
                            holeStats = sortedStats,
                            yardages = yardageMap,
                            isLoading = false
                        )
                    }

                    // On the very first load, determine initial hole index
                    if (_uiState.value.currentHole == null) {
                        val initialIndex = if (initialHoleArg != null && initialHoleArg != -1 && initialHoleArg in sortedHoles.indices) {
                            initialHoleArg
                        } else {
                            val firstIncompleteIndex = sortedStats.indexOfFirst { it.score == 0 }
                            if (firstIncompleteIndex != -1) firstIncompleteIndex else 0
                        }
                        loadHoleDetails(initialIndex)
                    }
                }
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
            
            holeDetailJob?.cancel()
            holeDetailJob = viewModelScope.launch {
                // Update basic hole info first
                _uiState.update { 
                    it.copy(
                        currentHoleIndex = index,
                        currentHoleStat = holeStat,
                        currentHole = hole
                    )
                }

                // Collect collections reactively
                val rawFlow: Flow<HoleStat> = combine(
                    roundRepository.getHoleStatFlow(holeStat.id),
                    roundRepository.getPuttsForHoleStat(holeStat.id),
                    roundRepository.getPenaltiesForHoleStat(holeStat.id),
                    roundRepository.getShotsForHoleStat(holeStat.id)
                ) { stat, _, _, _ -> stat ?: holeStat }
                
                rawFlow.debounce(500).collect { updatedStat: HoleStat ->
                    viewModelScope.launch {
                        val statId = holeStat.id
                        val currentShots = roundRepository.getShotsForHoleStat(statId).first()
                        val currentPutts = roundRepository.getPuttsForHoleStat(statId).first()
                        val currentPenalties = roundRepository.getPenaltiesForHoleStat(statId).first()
                        
                        _uiState.update { currentState ->
                            currentState.copy(
                                currentHoleStat = updatedStat,
                                putts = currentPutts,
                                penalties = currentPenalties,
                                shots = currentShots
                            )
                        }
                        recalculateSgForCurrentHole(
                            stat = updatedStat,
                            shots = currentShots,
                            putts = currentPutts,
                            penalties = currentPenalties
                        )
                    }
                }
            }
        }
    }

    fun nextHole() {
        val currentStat = uiState.value.currentHoleStat ?: return
        viewModelScope.launch {
            if (!currentStat.isScored) {
                updateStat(currentStat.copy(isScored = true))
            }
            val nextIndex = uiState.value.currentHoleIndex + 1
            if (nextIndex < uiState.value.holes.size) {
                loadHoleDetails(nextIndex)
            }
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
    
    fun updateTeeShot(
        outcome: ShotOutcome?, 
        inTrouble: Boolean, 
        clubId: Int?, 
        distance: Int?, 
        mishit: Boolean = false,
        slope: com.golftracker.data.model.LieSlope? = null,
        stance: com.golftracker.data.model.LieStance? = null,
        dispersionLeft: Int? = null,
        dispersionRight: Int? = null,
        dispersionShort: Int? = null,
        dispersionLong: Int? = null
    ) {
        val currentStat = uiState.value.currentHoleStat ?: return
        val updatedStat = currentStat.copy(
            teeOutcome = outcome,
            teeInTrouble = inTrouble,
            teeMishit = mishit,
            teeClubId = clubId,
            teeShotDistance = distance,
            teeSlope = slope,
            teeStance = stance,
            teeDispersionLeft = dispersionLeft ?: currentStat.teeDispersionLeft,
            teeDispersionRight = dispersionRight ?: currentStat.teeDispersionRight,
            teeDispersionShort = dispersionShort ?: currentStat.teeDispersionShort,
            teeDispersionLong = dispersionLong ?: currentStat.teeDispersionLong
        )
        updateStat(updatedStat)
    }
    


    fun addApproachShot() {
        val currentStat = uiState.value.currentHoleStat ?: return
        val currentHole = uiState.value.currentHole ?: return
        
        viewModelScope.launch {
            val existingShots = roundRepository.getShotsForHoleStat(currentStat.id).first()
            // CHANGE: On par 4/5, the drive recorded in HoleStat is always Shot 1. 
            // The first added approach shot should therefore be Shot 2.
            val teeShotPadding = if (existingShots.isEmpty() && currentHole.par > 3) 1 else 0
            val nextShotNumber = (existingShots.maxOfOrNull { it.shotNumber } ?: 0) + 1 + teeShotPadding
            
            // Enhanced distance estimation logic
            val shots = existingShots
            val defaultDistance = if (nextShotNumber == 1) {
                if (currentHole.par == 3) {
                    uiState.value.currentHoleYardage
                } else {
                    // Par 4/5: Hole distance - tee shot distance
                    uiState.value.currentHoleYardage?.let { yardage ->
                        currentStat.teeShotDistance?.let { teeDist ->
                            (yardage - teeDist).coerceAtLeast(0)
                        }
                    }
                }
            } else {
                // Subsequent shots: Previous shot's end distance
                val previousShot = shots.find { it.shotNumber == nextShotNumber - 1 }
                if (previousShot?.distanceToPin != null && previousShot.distanceTraveled != null) {
                    (previousShot.distanceToPin!! - previousShot.distanceTraveled!!).coerceAtLeast(0)
                } else {
                    previousShot?.distanceToPin
                }
            }

            // Smart lie defaulting logic
            val defaultLie = if (nextShotNumber == 1) {
                if (currentHole.par == 3) {
                    com.golftracker.data.model.ApproachLie.TEE
                } else if (currentStat.teeInTrouble) {
                    com.golftracker.data.model.ApproachLie.OTHER
                } else {
                    when (currentStat.teeOutcome) {
                        com.golftracker.data.model.ShotOutcome.ON_TARGET -> com.golftracker.data.model.ApproachLie.FAIRWAY
                        com.golftracker.data.model.ShotOutcome.MISS_LEFT, 
                        com.golftracker.data.model.ShotOutcome.MISS_RIGHT, 
                        com.golftracker.data.model.ShotOutcome.LONG -> com.golftracker.data.model.ApproachLie.ROUGH
                        else -> com.golftracker.data.model.ApproachLie.FAIRWAY
                    }
                }
            } else {
                val previousShot = shots.find { it.shotNumber == nextShotNumber - 1 }
                if (previousShot?.isRecovery == true) {
                    com.golftracker.data.model.ApproachLie.OTHER
                } else {
                    when (previousShot?.outcome) {
                        com.golftracker.data.model.ShotOutcome.ON_TARGET -> com.golftracker.data.model.ApproachLie.FAIRWAY
                        com.golftracker.data.model.ShotOutcome.MISS_LEFT, 
                        com.golftracker.data.model.ShotOutcome.MISS_RIGHT, 
                        com.golftracker.data.model.ShotOutcome.LONG -> com.golftracker.data.model.ApproachLie.ROUGH
                        else -> null
                    }
                }
            }

            roundRepository.insertShot(
                com.golftracker.data.entity.Shot(
                    holeStatId = currentStat.id,
                    shotNumber = nextShotNumber,
                    distanceToPin = defaultDistance,
                    lie = defaultLie
                )
            )
            recalculateSgForCurrentHole()
        }
    }

    fun updateShotPenaltyAttribution(shot: com.golftracker.data.entity.Shot, attribution: Double) {
        viewModelScope.launch {
            roundRepository.updateShot(shot.copy(penaltyAttribution = attribution))
            refreshShots(shot.holeStatId)
            recalculateSgForCurrentHole()
        }
    }

    fun updateShotDetails(
        shot: com.golftracker.data.entity.Shot, 
        outcome: ShotOutcome?, 
        lie: ApproachLie?, 
        clubId: Int?, 
        distanceToPin: Int?, 
        isRecovery: Boolean, 
        providedDistanceTraveled: Int?,
        slope: com.golftracker.data.model.LieSlope? = null,
        stance: com.golftracker.data.model.LieStance? = null,
        dispersionLeft: Int? = null,
        dispersionRight: Int? = null,
        dispersionShort: Int? = null,
        dispersionLong: Int? = null
    ) {
        viewModelScope.launch {
            if (outcome == null && lie == null && distanceToPin == null && providedDistanceTraveled == null) {
                roundRepository.deleteShot(shot)
            } else {
                roundRepository.updateShot(
                    shot.copy(
                        outcome = outcome,
                        lie = lie,
                        clubId = clubId,
                        distanceToPin = distanceToPin,
                        isRecovery = isRecovery,
                        distanceTraveled = providedDistanceTraveled,
                        strokesGained = null,
                        slope = slope,
                        stance = stance,
                        dispersionLeft = dispersionLeft ?: shot.dispersionLeft,
                        dispersionRight = dispersionRight ?: shot.dispersionRight,
                        dispersionShort = dispersionShort ?: shot.dispersionShort,
                        dispersionLong = dispersionLong ?: shot.dispersionLong
                    )
                )
            }
            refreshShots(shot.holeStatId)
            recalculateSgForCurrentHole()
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
         triggerMaintenance()
         recalculateSgForCurrentHole()
    }

    private fun triggerMaintenance() {
        maintenanceJob?.cancel()
        maintenanceJob = viewModelScope.launch {
            kotlinx.coroutines.delay(1500) // 1.5s debounce to allow for typing
            recalculateApproachDistances()
        }
    }

    /** Suggests a club ensuring we recommend the next club up if the yardage exceeds a club's stock yardage */
    fun suggestApproachClub(yardage: Int): Club? {
        val allClubs = clubs.value
        if (allClubs.isEmpty() || yardage <= 0) return null
        
        val validClubs = allClubs.filter { it.stockDistance != null && it.type != "DRIVER" && it.type != "PUTTER" }
        if (validClubs.isEmpty()) return null

        // Find the shortest club that hits at least the required yardage
        val clubUp = validClubs.filter { it.stockDistance!! >= yardage }
            .minByOrNull { it.stockDistance!! }
            
        // If no club goes that far, suggest the longest club we have
        return clubUp ?: validClubs.maxByOrNull { it.stockDistance!! }
    }

    /** Get the default tee club (first DRIVER in the bag) */
    fun defaultTeeClub(): Club? {
        return clubs.value.firstOrNull { it.type == "DRIVER" }
    }
    
    fun updateGreen(
        chips: Int,
        sandShots: Int,
        chipDistance: Int?,
        sandShotDistance: Int? = null,
        chipLie: com.golftracker.data.model.ApproachLie? = null,
        recoveryChip: Boolean = false
    ) {
        val stat = uiState.value.currentHoleStat ?: return
        updateStat(stat.copy(
            chips = chips,
            sandShots = sandShots,
            chipDistance = chipDistance,
            sandShotDistance = sandShotDistance,
            chipLie = chipLie,
            recoveryChip = recoveryChip
        ))
        viewModelScope.launch { recalculateApproachDistances() }
    }

    fun updateChipLieExtras(slope: com.golftracker.data.model.LieSlope?, stance: com.golftracker.data.model.LieStance?) {
        val stat = uiState.value.currentHoleStat ?: return
        updateStat(stat.copy(chipSlope = slope, chipStance = stance))
    }

    fun updateSandShotLieExtras(slope: com.golftracker.data.model.LieSlope?, stance: com.golftracker.data.model.LieStance?) {
        val stat = uiState.value.currentHoleStat ?: return
        updateStat(stat.copy(sandShotSlope = slope, sandShotStance = stance))
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
    
    /**
     * Recalculates estimated distanceTraveled for ALL approach shots on the current hole
     * where distanceTraveled is null.
     */
    private suspend fun recalculateApproachDistances() {
        val stat = uiState.value.currentHoleStat ?: return
        val hole = uiState.value.currentHole ?: return
        val yardage = uiState.value.currentHoleYardage ?: return
        val shots = roundRepository.getShotsForHoleStat(stat.id).first().sortedBy { it.shotNumber }
        val putts = roundRepository.getPuttsForHoleStat(stat.id).first().sortedBy { it.puttNumber }
        
        // 1. Auto-populate Tee Shot Distance if it matches the first approach shot (Par 4/5)
        if (hole.par > 3 && shots.isNotEmpty()) {
            val firstShot = shots.first()
            val oldCalcTeeDist = if (firstShot.distanceToPin != null) yardage - firstShot.distanceToPin!! else null
            if (stat.teeShotDistance == null && oldCalcTeeDist != null && oldCalcTeeDist > 0) {
                 updateTeeShot(stat.teeOutcome, stat.teeInTrouble, stat.teeClubId, oldCalcTeeDist)
            }
        }

        // 2. Auto-estimate Approach distances
        if (shots.isEmpty()) return
        
        var anyUpdated = false
        for (i in shots.indices) {
            val shot = shots[i]
            if (shot.distanceTraveled != null) continue  // Already has a value, skip
            val startDist = shot.distanceToPin ?: continue  // This shot's starting distance
            
            // Determine ending distance (where the ball ended up)
            val endDist = if (i + 1 < shots.size) {
                shots[i + 1].distanceToPin ?: continue
            } else if (stat.chips > 0 || stat.sandShots > 0) {
                stat.chipDistance ?: 15
            } else if (putts.isNotEmpty()) {
                (putts.first().distance?.toInt() ?: 0) / 3  // feet to yards
            } else {
                0
            }
            
            val isLastShot = (i == shots.size - 1)
            val estimated = ShotDistanceCalculator.estimateShotDistance(startDist, endDist, shot.outcome, isLastShot)
            roundRepository.updateShot(shot.copy(distanceTraveled = estimated))
            anyUpdated = true
        }
        
        if (anyUpdated) {
            val updatedShots = roundRepository.getShotsForHoleStat(stat.id).first()
            _uiState.update { it.copy(shots = updatedShots) }
            recalculateSgForCurrentHole()
        }
    }
    
    fun updateHoleYardage(yardage: Int?) {
        val stat = uiState.value.currentHoleStat ?: return
        updateStat(stat.copy(adjustedYardage = yardage))
        viewModelScope.launch {
            recalculateApproachDistances()
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
                    val defaultDistance = if (i == 1) 20f else 1f
                    roundRepository.insertPutt(Putt(holeStatId = currentStat.id, puttNumber = i, distance = defaultDistance))
                }
            } else if (count < currentPutts.size) {
                 val toRemove = currentPutts.takeLast(currentPutts.size - count)
                 toRemove.forEach { roundRepository.deletePutt(it) }
            }
            val newPutts = roundRepository.getPuttsForHoleStat(currentStat.id).first()
            _uiState.update { it.copy(putts = newPutts) }
            recalculateSgForCurrentHole()
            recalculateApproachDistances()
        }
    }
    
    fun updatePuttDistance(putt: Putt, distance: Float?) {
        viewModelScope.launch {
            if (distance == null) {
                roundRepository.deletePutt(putt)
            } else {
                roundRepository.updatePutt(putt.copy(distance = distance, strokesGained = null))
            }
            val newPutts = roundRepository.getPuttsForHoleStat(putt.holeStatId).first()
            _uiState.update { it.copy(putts = newPutts) }
            recalculateSgForCurrentHole()
            recalculateApproachDistances()
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
        val currentStat = uiState.value.currentHoleStat
        viewModelScope.launch {
            if (currentStat != null && !currentStat.isScored) {
                updateStat(currentStat.copy(isScored = true))
            }
            roundRepository.updateRound(round.copy(isFinalized = true))
             _uiState.update { it.copy(isRoundFinalized = true) }
        }
    }

    private suspend fun recalculateSgForCurrentHole(
        stat: HoleStat? = null,
        shots: List<com.golftracker.data.entity.Shot>? = null,
        putts: List<Putt>? = null,
        penalties: List<Penalty>? = null
    ) {
        val state = uiState.value
        val currentStat = stat ?: state.currentHoleStat ?: return
        val hole = state.currentHole ?: return
        val round = state.activeRound ?: return
        
        val teeSet = courseRepository.getTeeSets(round.courseId).first().find { it.id == round.teeSetId } ?: return
        val allHoles = courseRepository.getHoles(round.courseId).first()
        val allYardages = courseRepository.getYardagesForTeeSet(round.teeSetId).first()
        
        val coursePar = allHoles.sumOf { it.par }
        
        val totalAdj = sgCalculator.calculateCourseAdjustment(teeSet.rating.toDouble(), coursePar)
        val holeAdj = sgCalculator.getHoleAdjustment(totalAdj, hole.handicapIndex, allHoles.size)
        val holeYardage = allYardages.find { it.holeId == hole.id }?.yardage ?: return
        
        val rawShots = shots ?: roundRepository.getShotsForHoleStat(currentStat.id).first().sortedBy { it.shotNumber }
        
        // DE-DUPLICATION: Ensure shot numbers are unique and sequential (1, 2, 3...)
        // CHANGE: Account for the par 4/5 drive (HoleStat) being Shot 1.
        val teeShotInStat = hole.par > 3 && (currentStat.teeOutcome != null || currentStat.teeShotDistance != null || currentStat.teeClubId != null)
        val shotNumberOffset = if (teeShotInStat) 2 else 1
        
        val currentShots = rawShots.mapIndexed { index, shot ->
            val expectedNumber = index + shotNumberOffset
            if (shot.shotNumber != expectedNumber) {
                val corrected = shot.copy(shotNumber = expectedNumber)
                viewModelScope.launch { roundRepository.updateShot(corrected) }
                corrected
            } else {
                shot
            }
        }
        
        val currentPutts = putts ?: roundRepository.getPuttsForHoleStat(currentStat.id).first().sortedBy { it.puttNumber }
        val currentPenalties = penalties ?: roundRepository.getPenaltiesForHoleStat(currentStat.id).first()
        
        // 1. Unified Calculation
        val breakdown = sgCalculator.calculateHoleSg(
            par = hole.par,
            holeYardage = holeYardage,
            holeAdjustment = holeAdj,
            shots = currentShots,
            putts = currentPutts,
            penalties = currentPenalties.sumOf { it.strokes },
            stat = currentStat
        )

        // 2. Update individual Shots in DB
        val updatedShots = currentShots.map { shot ->
            val sg = breakdown.shotSgs.find { it.first == shot.shotNumber }?.second
            val updatedShot = shot.copy(strokesGained = sg)
            if (updatedShot != shot) {
                roundRepository.updateShot(updatedShot)
            }
            updatedShot
        }

        // 3. Update individual Putts in DB
        val updatedPutts = currentPutts.map { putt ->
            val sg = breakdown.puttSgs.find { it.first == putt.puttNumber }?.second
            val updatedPutt = putt.copy(strokesGained = sg)
            if (updatedPutt != putt) {
                roundRepository.updatePutt(updatedPutt)
            }
            updatedPutt
        }

        // 4. Update HoleStat
        val penaltyTotal = currentPenalties.sumOf { it.strokes }.toDouble()
        val totalSg = breakdown.total
        
        val shortGameStrokes = currentStat.chips + currentStat.sandShots
        val teeShotTaken = hole.par > 3 && (currentStat.teeOutcome != null || currentStat.teeShotDistance != null || currentStat.teeClubId != null || currentStat.teeLat != null)
        val holedOutFromOffGreen = (currentStat.teeOutcome == ShotOutcome.HOLED_OUT) || updatedShots.any { it.outcome == ShotOutcome.HOLED_OUT }
        
        val calculatedScore = (if (teeShotTaken && updatedShots.none { it.shotNumber == 1 }) 1 else 0) + 
            updatedShots.size + 
            shortGameStrokes + 
            currentStat.putts + 
            currentPenalties.sumOf { it.strokes }
            
        val finishedHole = holedOutFromOffGreen || (currentStat.putts > 0 && updatedPutts.any { it.made })
        val newScore = maxOf(currentStat.score, calculatedScore)
        val hasData = teeShotTaken || updatedShots.isNotEmpty() || updatedPutts.isNotEmpty() || shortGameStrokes > 0 || currentPenalties.isNotEmpty()
        val isGir = finishedHole && (newScore - currentStat.putts <= hole.par - 2)
        
        val updatedStat = currentStat.copy(
            score = newScore,
            strokesGained = if (hasData) totalSg else null,
            sgOffTee = if (hasData) breakdown.offTee else null,
            sgApproach = if (hasData) breakdown.approach else null,
            sgAroundGreen = if (hasData) breakdown.aroundGreen else null,
            sgPutting = if (hasData) breakdown.putting else null,
            gir = isGir,
            difficultyAdjustment = holeAdj,
            sgOffTeeExpected = if (hasData) breakdown.offTeeExpected else null
        )
        
        if (updatedStat != currentStat) {
            roundRepository.updateHoleStat(updatedStat)
        }

        // Refresh UI state
        _uiState.update { 
            val newStats = it.holeStats.toMutableList()
            if (it.currentHoleIndex in newStats.indices) {
                newStats[it.currentHoleIndex] = updatedStat
            }
            it.copy(
                currentHoleStat = updatedStat, 
                holeStats = newStats,
                shots = updatedShots,
                putts = updatedPutts
            )
        }
    }
}
