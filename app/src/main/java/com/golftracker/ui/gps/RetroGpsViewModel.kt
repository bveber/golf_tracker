package com.golftracker.ui.gps

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.golftracker.data.entity.Club
import com.golftracker.data.entity.HoleStat
import com.golftracker.data.entity.Shot
import com.golftracker.data.model.ApproachLie
import com.golftracker.data.repository.ClubRepository
import com.golftracker.data.repository.CourseRepository
import com.golftracker.data.repository.RoundRepository
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RetroPinType { START, TARGET, OUTCOME }

data class RetroShotItem(
    val label: String,
    val isTeeShotOnHoleStat: Boolean,
    val shotId: Int?,
    val shotNumber: Int?,
    val holeStatId: Int,
    val startPin: LatLng?,
    val targetPin: LatLng?,
    val outcomePin: LatLng?,
    val clubId: Int? = null,
    val lie: ApproachLie? = null,
)

data class RetroGpsUiState(
    val shots: List<RetroShotItem> = emptyList(),
    val selectedIndex: Int = 0,
    val activePin: RetroPinType = RetroPinType.TARGET,
    val holeTeeLatLng: LatLng? = null,
    val holeGreenLatLng: LatLng? = null,
    val fallbackLocation: LatLng? = null,
    val holeNumber: Int = 0,
    val holePar: Int = 4,
    val clubs: List<Club> = emptyList(),
    val isSaving: Boolean = false,
    val savedMessage: String? = null
) {
    val selectedShot: RetroShotItem? get() = shots.getOrNull(selectedIndex)
}

@HiltViewModel
class RetroGpsViewModel @Inject constructor(
    private val roundRepository: RoundRepository,
    private val courseRepository: CourseRepository,
    private val clubRepository: ClubRepository,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val holeStatId: Int = savedStateHandle.get<Int>("holeStatId") ?: 0
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    private val _uiState = MutableStateFlow(RetroGpsUiState())
    val uiState: StateFlow<RetroGpsUiState> = _uiState.asStateFlow()

    init {
        loadData()
        viewModelScope.launch {
            clubRepository.activeClubs.collect { clubs ->
                _uiState.update { it.copy(clubs = clubs.filter { c -> c.type != "PUTTER" }) }
            }
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            val holeStat = roundRepository.getHoleStatFlow(holeStatId).first() ?: return@launch
            val shots = roundRepository.getShotsForHoleStat(holeStatId).first()
            val hole = courseRepository.getHoleById(holeStat.holeId)

            val teeLatLng = hole?.teeLat?.let { lat -> hole.teeLng?.let { lng -> LatLng(lat, lng) } }
            val greenLatLng = hole?.greenLat?.let { lat -> hole.greenLng?.let { lng -> LatLng(lat, lng) } }

            _uiState.update { state ->
                state.copy(
                    shots = buildRetroShots(holeStat, shots, teeLatLng),
                    holeTeeLatLng = teeLatLng,
                    holeGreenLatLng = greenLatLng,
                    holeNumber = hole?.holeNumber ?: 0,
                    holePar = hole?.par ?: 4
                )
            }
        }
    }

    private fun buildRetroShots(
        holeStat: HoleStat,
        shots: List<Shot>,
        teeLatLng: LatLng?
    ): List<RetroShotItem> {
        val items = mutableListOf<RetroShotItem>()

        val teeStart = holeStat.teeLat?.let { lat ->
            holeStat.teeLng?.let { lng -> LatLng(lat, lng) }
        } ?: teeLatLng

        val teeTarget = holeStat.teeTargetLat?.let { lat ->
            holeStat.teeTargetLng?.let { lng -> LatLng(lat, lng) }
        }

        // Tee shot landing: prefer the stored outcome lat/lng, fall back to first approach start
        val teeOutcome = holeStat.teeOutcomeLat?.let { lat ->
            holeStat.teeOutcomeLng?.let { lng -> LatLng(lat, lng) }
        } ?: shots.firstOrNull()?.startLat?.let { lat ->
            shots.firstOrNull()?.startLng?.let { lng -> LatLng(lat, lng) }
        }

        items.add(
            RetroShotItem(
                label = "Tee Shot",
                isTeeShotOnHoleStat = true,
                shotId = null,
                shotNumber = null,
                holeStatId = holeStat.id,
                startPin = teeStart,
                targetPin = teeTarget,
                outcomePin = teeOutcome,
                clubId = holeStat.teeClubId,
                lie = ApproachLie.TEE,
            )
        )

        shots.forEachIndexed { index, shot ->
            val label = when {
                shots.size == 1 -> "Approach"
                index < shots.size - 1 -> "Approach ${index + 1}"
                else -> "Chip"
            }
            items.add(
                RetroShotItem(
                    label = label,
                    isTeeShotOnHoleStat = false,
                    shotId = shot.id,
                    shotNumber = shot.shotNumber,
                    holeStatId = holeStat.id,
                    startPin = shot.startLat?.let { lat -> shot.startLng?.let { lng -> LatLng(lat, lng) } },
                    targetPin = shot.targetLat?.let { lat -> shot.targetLng?.let { lng -> LatLng(lat, lng) } },
                    outcomePin = shot.endLat?.let { lat -> shot.endLng?.let { lng -> LatLng(lat, lng) } },
                    clubId = shot.clubId,
                    lie = shot.lie,
                )
            )
        }

        return items
    }

    fun selectShot(index: Int) {
        _uiState.update { it.copy(selectedIndex = index.coerceIn(0, it.shots.size - 1)) }
    }

    fun setActivePin(pinType: RetroPinType) {
        _uiState.update { it.copy(activePin = pinType) }
    }

    fun addNextShot() {
        val state = _uiState.value
        val currentOutcome = state.selectedShot?.outcomePin
        val existingApproach = state.shots.filter { !it.isTeeShotOnHoleStat }
        val maxShotNumber = existingApproach.mapNotNull { it.shotNumber }.maxOrNull() ?: 0
        val teeShotPadding = if (existingApproach.isEmpty() && state.holePar > 3) 1 else 0
        val nextShotNumber = maxShotNumber + 1 + teeShotPadding

        viewModelScope.launch {
            val newShotId = roundRepository.insertShot(
                com.golftracker.data.entity.Shot(
                    holeStatId = holeStatId,
                    shotNumber = nextShotNumber,
                    startLat = currentOutcome?.latitude,
                    startLng = currentOutcome?.longitude,
                )
            ).toInt()

            val newIndex = state.shots.size
            val totalApproach = existingApproach.size + 1
            val newLabel = if (totalApproach == 1) "Approach" else "Approach $totalApproach"

            val newItem = RetroShotItem(
                label = newLabel,
                isTeeShotOnHoleStat = false,
                shotId = newShotId,
                shotNumber = nextShotNumber,
                holeStatId = holeStatId,
                startPin = currentOutcome,
                targetPin = null,
                outcomePin = null,
            )

            _uiState.update { s ->
                s.copy(shots = s.shots + newItem, selectedIndex = newIndex)
            }
        }
    }

    fun updateClub(clubId: Int?) {
        _uiState.update { state ->
            val shots = state.shots.toMutableList()
            val current = shots.getOrNull(state.selectedIndex) ?: return@update state
            shots[state.selectedIndex] = current.copy(clubId = clubId)
            state.copy(shots = shots)
        }
    }

    fun updateLie(lie: ApproachLie?) {
        _uiState.update { state ->
            val shots = state.shots.toMutableList()
            val current = shots.getOrNull(state.selectedIndex) ?: return@update state
            shots[state.selectedIndex] = current.copy(lie = lie)
            state.copy(shots = shots)
        }
    }

    fun tapMap(latLng: LatLng) {
        updatePin(_uiState.value.activePin, latLng)
    }

    fun updatePin(pinType: RetroPinType, latLng: LatLng) {
        _uiState.update { state ->
            val shots = state.shots.toMutableList()
            val current = shots.getOrNull(state.selectedIndex) ?: return@update state
            val updated = when (pinType) {
                RetroPinType.START -> current.copy(startPin = latLng)
                RetroPinType.TARGET -> current.copy(targetPin = latLng)
                RetroPinType.OUTCOME -> {
                    // Auto-link: pre-fill next shot's start if not yet placed
                    val nextIndex = state.selectedIndex + 1
                    if (nextIndex < shots.size && shots[nextIndex].startPin == null) {
                        shots[nextIndex] = shots[nextIndex].copy(startPin = latLng)
                    }
                    current.copy(outcomePin = latLng)
                }
            }
            shots[state.selectedIndex] = updated
            state.copy(shots = shots)
        }
    }

    fun saveCurrentShot() {
        val state = _uiState.value
        val shot = state.selectedShot ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                if (shot.isTeeShotOnHoleStat) {
                    saveTeeShot(shot)
                } else {
                    saveApproachShot(shot)
                }
                syncHoleStatFromGpsShots()
                _uiState.update { it.copy(isSaving = false, savedMessage = "Saved") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, savedMessage = "Save failed") }
            }
        }
    }

    /**
     * After saving GPS shots, back-fill HoleStat aggregate fields that the manual tracking page
     * reads: chip count + distance (from GPS approach shots within 30 yds of pin).
     * Only writes if not already manually set, so manual entries take precedence.
     */
    private suspend fun syncHoleStatFromGpsShots() {
        val holeStat = roundRepository.getHoleStatFlow(holeStatId).first() ?: return
        val shots = roundRepository.getShotsForHoleStat(holeStatId).first()
            .filter { it.lie != ApproachLie.TEE }
            .sortedBy { it.shotNumber }

        if (shots.isEmpty()) return

        val chipThreshold = 30
        val chipShots = shots.filter {
            (it.distanceToPin ?: Int.MAX_VALUE) < chipThreshold && it.lie != ApproachLie.SAND
        }

        if (chipShots.isNotEmpty() && holeStat.chips == 0) {
            val firstChip = chipShots.first()
            roundRepository.updateHoleStat(
                holeStat.copy(
                    chips = chipShots.size,
                    chipDistance = firstChip.distanceToPin ?: holeStat.chipDistance,
                    chipLie = firstChip.lie ?: holeStat.chipLie,
                    chipOutcome = firstChip.outcome ?: holeStat.chipOutcome
                )
            )
        }
    }

    private suspend fun saveTeeShot(item: RetroShotItem) {
        val holeStat = roundRepository.getHoleStatFlow(item.holeStatId).first() ?: return
        val start = item.startPin
        val target = item.targetPin
        val outcome = item.outcomePin

        val dispersion = if (start != null && target != null && outcome != null) {
            GpsUtils.calculateDispersionOffsets(start, target, outcome)
        } else null

        val teeOutcomeEnum = if (dispersion != null) GpsUtils.estimateOutcome(dispersion) else holeStat.teeOutcome
        val teeShotDist = if (start != null && outcome != null) GpsUtils.calculateDistanceYards(start, outcome) else null

        roundRepository.updateHoleStat(
            holeStat.copy(
                teeLat = start?.latitude ?: holeStat.teeLat,
                teeLng = start?.longitude ?: holeStat.teeLng,
                teeTargetLat = target?.latitude ?: holeStat.teeTargetLat,
                teeTargetLng = target?.longitude ?: holeStat.teeTargetLng,
                teeOutcomeLat = outcome?.latitude ?: holeStat.teeOutcomeLat,
                teeOutcomeLng = outcome?.longitude ?: holeStat.teeOutcomeLng,
                teeDispersionLeft = dispersion?.left ?: holeStat.teeDispersionLeft,
                teeDispersionRight = dispersion?.right ?: holeStat.teeDispersionRight,
                teeDispersionShort = dispersion?.short ?: holeStat.teeDispersionShort,
                teeDispersionLong = dispersion?.long ?: holeStat.teeDispersionLong,
                teeOutcome = teeOutcomeEnum,
                teeClubId = item.clubId ?: holeStat.teeClubId,
                teeShotDistance = teeShotDist ?: holeStat.teeShotDistance,
            )
        )

        // Auto-create the first approach Shot entity if none exists yet (par 4/5 only).
        // This lets the manual tracking page show the approach shot without requiring the user
        // to separately tap "Add Next Shot" in the GPS screen.
        if (_uiState.value.holePar > 3 && outcome != null) {
            val existingApproach = roundRepository.getShotsForHoleStat(item.holeStatId).first()
                .filter { it.lie != ApproachLie.TEE }
            if (existingApproach.isEmpty()) {
                val greenLatLng = _uiState.value.holeGreenLatLng
                val approachDistanceToPin = greenLatLng?.let {
                    GpsUtils.calculateDistanceYards(outcome, it)
                }
                val newShotId = roundRepository.insertShot(
                    Shot(
                        holeStatId = item.holeStatId,
                        shotNumber = 2,
                        startLat = outcome.latitude,
                        startLng = outcome.longitude,
                        distanceToPin = approachDistanceToPin,
                        targetLat = greenLatLng?.latitude,
                        targetLng = greenLatLng?.longitude,
                    )
                ).toInt()
                val newItem = RetroShotItem(
                    label = "Approach",
                    isTeeShotOnHoleStat = false,
                    shotId = newShotId,
                    shotNumber = 2,
                    holeStatId = item.holeStatId,
                    startPin = outcome,
                    targetPin = null,
                    outcomePin = null
                )
                _uiState.update { s ->
                    s.copy(shots = s.shots + newItem, selectedIndex = s.shots.size)
                }
            }
        }
    }

    private suspend fun saveApproachShot(item: RetroShotItem) {
        val shotId = item.shotId ?: return
        val shots = roundRepository.getShotsForHoleStat(item.holeStatId).first()
        val shot = shots.find { it.id == shotId } ?: return

        val start = item.startPin
        val target = item.targetPin
        val outcome = item.outcomePin

        val dispersion = if (start != null && target != null && outcome != null) {
            GpsUtils.calculateDispersionOffsets(start, target, outcome)
        } else null

        val shotOutcome = if (dispersion != null) GpsUtils.estimateOutcome(dispersion) else shot.outcome
        val distanceTraveled = if (start != null && outcome != null) {
            GpsUtils.calculateDistanceYards(start, outcome)
        } else shot.distanceTraveled
        val distanceToPin = if (start != null && target != null) {
            GpsUtils.calculateDistanceYards(start, target)
        } else shot.distanceToPin

        roundRepository.updateShot(
            shot.copy(
                startLat = start?.latitude ?: shot.startLat,
                startLng = start?.longitude ?: shot.startLng,
                targetLat = target?.latitude ?: shot.targetLat,
                targetLng = target?.longitude ?: shot.targetLng,
                endLat = outcome?.latitude ?: shot.endLat,
                endLng = outcome?.longitude ?: shot.endLng,
                dispersionLeft = dispersion?.left ?: shot.dispersionLeft,
                dispersionRight = dispersion?.right ?: shot.dispersionRight,
                dispersionShort = dispersion?.short ?: shot.dispersionShort,
                dispersionLong = dispersion?.long ?: shot.dispersionLong,
                outcome = shotOutcome,
                distanceTraveled = distanceTraveled,
                distanceToPin = distanceToPin,
                clubId = item.clubId ?: shot.clubId,
                lie = item.lie ?: shot.lie,
            )
        )
    }

    @SuppressLint("MissingPermission")
    fun fetchFallbackLocation() {
        val state = _uiState.value
        if (state.holeTeeLatLng != null || state.holeGreenLatLng != null) return
        fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
            loc?.let {
                _uiState.update { s -> s.copy(fallbackLocation = LatLng(it.latitude, it.longitude)) }
            }
        }
    }

    fun clearSavedMessage() {
        _uiState.update { it.copy(savedMessage = null) }
    }
}
