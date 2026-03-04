package com.golftracker.ui.gps

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.golftracker.data.entity.Club
import com.golftracker.data.entity.HoleStat
import com.golftracker.data.entity.Shot
import com.golftracker.data.model.ShotType
import com.golftracker.data.repository.ClubRepository
import com.golftracker.data.repository.RoundRepository
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Represents a single shot tracked on the GPS screen.
 *
 * @property shotType The category of shot (TEE, APPROACH, or CHIP).
 * @property clubId   Database ID of the club used, or `null` if unselected.
 * @property clubName Display name of the club (cached for UI rendering).
 * @property location GPS coordinate where the shot was played from.
 * @property distanceYards Calculated carry distance in yards (set when the *next* shot is tracked).
 * @property distanceToPin Distance to the flag at the time the shot was tracked, in yards.
 */
data class TrackedShot(
    val shotType: ShotType,
    val clubId: Int?,
    val clubName: String?,
    val location: LatLng,
    val distanceYards: Int? = null,
    val distanceToPin: Int? = null
)

/**
 * UI state for the GPS rangefinder / shot-tracking screen.
 *
 * @property liveUserLocation Real-time GPS position used for the "Snap to Me" feature.
 * @property playerLocation   Draggable player-marker position; seeded from the first GPS fix.
 * @property flagLocation     Draggable flag-marker position; initially offset from the player.
 * @property isLocationPermissionGranted Whether ACCESS_FINE_LOCATION has been granted.
 * @property trackedShots     Ordered list of shots recorded on this hole via GPS.
 * @property pendingShotType  Shot type pre-selected for the next tracking action.
 * @property pendingClubId    Club pre-selected for the next tracking action.
 * @property showShotPanel    Whether the shot-tracking bottom panel is visible.
 * @property isSyncing        True while loading existing shots from the database.
 * @property clubs            Active clubs from the user's bag (for the club picker).
 * @property roundId          Current round database ID (null when used as standalone rangefinder).
 * @property holeStatId       Current hole-stat database ID.
 * @property holePar          Par for the current hole (drives auto-advance logic).
 */
data class GpsUiState(
    val liveUserLocation: LatLng? = null,
    val playerLocation: LatLng? = null,
    val flagLocation: LatLng? = null,
    val isLocationPermissionGranted: Boolean = false,

    // Shot Tracking
    val trackedShots: List<TrackedShot> = emptyList(),
    val pendingShotType: ShotType = ShotType.APPROACH,
    val pendingClubId: Int? = null,
    val showShotPanel: Boolean = false,
    val isSyncing: Boolean = false,
    val clubs: List<Club> = emptyList(),
    val roundId: Int? = null,
    val holeStatId: Int? = null,
    val holePar: Int? = null
)

/**
 * ViewModel for the GPS rangefinder and shot-tracking screen.
 *
 * Manages live GPS location updates, draggable map markers,
 * shot recording / deletion, and persistence to the Room database.
 */
@HiltViewModel
class GpsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clubRepository: ClubRepository,
    private val roundRepository: RoundRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GpsUiState())
    val uiState: StateFlow<GpsUiState> = _uiState.asStateFlow()

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    init {
        viewModelScope.launch {
            clubRepository.activeClubs.collectLatest { clubs ->
                _uiState.update { it.copy(clubs = clubs) }
            }
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            val latLng = LatLng(location.latitude, location.longitude)
            _uiState.update { state ->
                state.copy(
                    liveUserLocation = latLng,
                    // Set player marker and flag only on the very first GPS fix
                    playerLocation = state.playerLocation ?: latLng,
                    flagLocation = state.flagLocation
                        ?: LatLng(location.latitude + 0.00101, location.longitude)
                )
            }

            checkProximityAndAutoSuggest(latLng)
        }
    }

    /**
     * Checks how close [currentLocation] is to the flag and automatically
     * switches the pending shot type to CHIP when within 40 yards.
     */
    private fun checkProximityAndAutoSuggest(currentLocation: LatLng) {
        val state = _uiState.value
        val flag = state.flagLocation ?: return

        val dist = GpsUtils.calculateDistanceYards(currentLocation, flag)

        // Auto-select CHIP if < 40 yards and currently on APPROACH
        if (dist < 40 && state.pendingShotType == ShotType.APPROACH) {
            _uiState.update { it.copy(pendingShotType = ShotType.CHIP) }
        }
    }

    /**
     * Sets the round / hole context for shot tracking.
     *
     * When [roundId] and [holeStatId] are both non-null the shot-tracking
     * panel becomes visible. Changing [holeStatId] triggers a fresh load
     * of existing shots from the database.
     */
    fun setTrackingContext(roundId: Int?, holeStatId: Int?, holePar: Int?) {
        val currentHoleStatId = _uiState.value.holeStatId

        _uiState.update {
            it.copy(
                roundId = roundId,
                holeStatId = holeStatId,
                holePar = holePar,
                showShotPanel = roundId != null && holeStatId != null
            )
        }

        // If we switched holes or just opened, load from DB
        if (holeStatId != null && holeStatId != currentHoleStatId) {
            loadExistingShots(holeStatId)
        }
    }

    private fun loadExistingShots(holeStatId: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            
            val roundId = _uiState.value.roundId ?: return@launch
            val holeStats = roundRepository.getHoleStatsForRound(roundId).first()
            val stat = holeStats.find { it.id == holeStatId } ?: return@launch
            val shots = roundRepository.getShotsForHoleStat(holeStatId).first().sortedBy { it.shotNumber }
            
            val clubs = _uiState.value.clubs
            val reconstructed = mutableListOf<TrackedShot>()
            
            // 1. Reconstruct Tee Shot
            if (stat.teeLat != null && stat.teeLng != null) {
                reconstructed.add(TrackedShot(
                    shotType = ShotType.TEE,
                    clubId = stat.teeClubId,
                    clubName = clubs.find { it.id == stat.teeClubId }?.name,
                    location = LatLng(stat.teeLat, stat.teeLng),
                    distanceYards = stat.teeShotDistance
                ))
            }
            
            // 2. Reconstruct Approach Shots
            shots.forEach { shot ->
                if (shot.startLat != null && shot.startLng != null) {
                    reconstructed.add(TrackedShot(
                        shotType = ShotType.APPROACH,
                        clubId = shot.clubId,
                        clubName = clubs.find { it.id == shot.clubId }?.name,
                        location = LatLng(shot.startLat, shot.startLng),
                        distanceYards = shot.distanceTraveled,
                        distanceToPin = shot.distanceToPin
                    ))
                }
            }

            _uiState.update { state ->
                state.copy(
                    trackedShots = reconstructed,
                    isSyncing = false,
                    // Smart auto-advance
                    pendingShotType = when {
                        reconstructed.isEmpty() && (state.holePar ?: 0) > 3 -> ShotType.TEE
                        reconstructed.any { it.shotType == ShotType.TEE } -> ShotType.APPROACH
                        else -> ShotType.APPROACH
                    }
                )
            }
        }
    }

    fun onPermissionResult(isGranted: Boolean) {
        _uiState.update { it.copy(isLocationPermissionGranted = isGranted) }
        if (isGranted) {
            startLocationUpdates()
        }
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        // Seed immediately from last known location
        fusedLocationClient.lastLocation.addOnSuccessListener { loc: android.location.Location? ->
            loc?.let {
                val latLng = LatLng(it.latitude, it.longitude)
                _uiState.update { state ->
                    state.copy(
                        liveUserLocation = latLng,
                        playerLocation = state.playerLocation ?: latLng,
                        flagLocation = state.flagLocation
                            ?: LatLng(it.latitude + 0.00101, it.longitude)
                    )
                }
            }
        }

        // Background updates keep the live-location dot current (every 5 seconds)
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5000L
        ).setMinUpdateIntervalMillis(3000L).build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            android.os.Looper.getMainLooper()
        )
    }

    override fun onCleared() {
        super.onCleared()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    /** Called when the user drags or taps to move the player marker. */
    fun onPlayerDragged(newLocation: LatLng) {
        _uiState.update { it.copy(playerLocation = newLocation) }
    }

    /** Called when the user drags or taps to move the flag marker. */
    fun onFlagDragged(newLocation: LatLng) {
        _uiState.update { it.copy(flagLocation = newLocation) }
    }

    /** Updates the pending shot type selection in the tracking panel. */
    fun onShotTypeSelected(type: ShotType) {
        _uiState.update { it.copy(pendingShotType = type) }
    }

    /** Updates the pending club selection in the tracking panel. */
    fun onClubSelected(clubId: Int?) {
        _uiState.update { it.copy(pendingClubId = clubId) }
    }

    /**
     * Records the current player-marker position as a new shot.
     *
     * When a previous shot exists, this also back-fills its carry distance
     * (the distance from the previous shot to the current position).
     * After recording, the pending shot type auto-advances (TEE → APPROACH,
     * APPROACH → CHIP if near the green).
     */
    fun onTrackShot() {
        val state = _uiState.value
        val currentLocation = state.playerLocation ?: return
        val currentClub = state.clubs.find { it.id == state.pendingClubId }
        val distToFlag = state.flagLocation?.let {
            GpsUtils.calculateDistanceYards(currentLocation, it)
        }

        val newTrackedShot = TrackedShot(
            shotType = state.pendingShotType,
            clubId = state.pendingClubId,
            clubName = currentClub?.name,
            location = currentLocation,
            distanceToPin = distToFlag
        )

        val updatedShots = state.trackedShots.toMutableList()

        // Calculate distance for the previous shot if exists
        if (updatedShots.isNotEmpty()) {
            val lastIdx = updatedShots.size - 1
            val lastShot = updatedShots[lastIdx]
            val dist = GpsUtils.calculateDistanceYards(lastShot.location, currentLocation)
            updatedShots[lastIdx] = lastShot.copy(distanceYards = dist)

            // Persist the distance update for the previous shot
            persistShotUpdate(updatedShots[lastIdx], lastIdx)
        }

        updatedShots.add(newTrackedShot)
        _uiState.update {
            it.copy(
                trackedShots = updatedShots,
                // Advance shot type
                pendingShotType = when (state.pendingShotType) {
                    ShotType.TEE -> ShotType.APPROACH
                    ShotType.APPROACH -> {
                        val flag = state.flagLocation
                        if (flag != null && GpsUtils.calculateDistanceYards(currentLocation, flag) < 30) {
                            ShotType.CHIP
                        } else {
                            ShotType.APPROACH
                        }
                    }
                    else -> state.pendingShotType
                }
            )
        }

        // Persist the new shot entry
        persistShotUpdate(newTrackedShot, updatedShots.size - 1)
    }

    /** Removes the tracked shot at [index] from the UI list and the database. */
    fun onDeleteShot(index: Int) {
        _uiState.update { state ->
            val updated = state.trackedShots.toMutableList()
            if (index in updated.indices) {
                val shotToRemove = updated[index]
                updated.removeAt(index)
                deleteFromDb(shotToRemove, index)
            }
            state.copy(trackedShots = updated)
        }
    }

    private fun deleteFromDb(shot: TrackedShot, index: Int) {
        val holeStatId = _uiState.value.holeStatId ?: return
        viewModelScope.launch {
            when (shot.shotType) {
                ShotType.TEE -> {
                    val stat = roundRepository.getHoleStatsForRound(_uiState.value.roundId!!).first().find { it.id == holeStatId }
                    stat?.let {
                        roundRepository.updateHoleStat(it.copy(
                            teeClubId = null,
                            teeShotDistance = null,
                            teeLat = null,
                            teeLng = null
                        ))
                    }
                }
                ShotType.APPROACH -> {
                    val actualShots = roundRepository.getShotsForHoleStat(holeStatId).first()
                    // Re-calculate approach index
                    val approachIndex = _uiState.value.trackedShots.filterIndexed { i, s -> i < index && s.shotType == ShotType.APPROACH }.size
                    if (approachIndex in actualShots.indices) {
                        roundRepository.deleteShot(actualShots[approachIndex])
                    }
                }
                else -> {}
            }
        }
    }

    private fun persistShotUpdate(trackedShot: TrackedShot, index: Int) {
        val holeStatId = _uiState.value.holeStatId ?: return
        
        viewModelScope.launch {
            when (trackedShot.shotType) {
                ShotType.TEE -> {
                    // Update HoleStat tee info
                    val currentHoleStat = roundRepository.getHoleStatsForRound(_uiState.value.roundId!!).first().find { it.id == holeStatId }
                    currentHoleStat?.let {
                        roundRepository.updateHoleStat(it.copy(
                            teeClubId = trackedShot.clubId,
                            teeShotDistance = trackedShot.distanceYards,
                            teeLat = trackedShot.location.latitude,
                            teeLng = trackedShot.location.longitude
                        ))
                    }
                }
                ShotType.APPROACH -> {
                    // Find or create Shot entity
                    val actualShots = roundRepository.getShotsForHoleStat(holeStatId).first()
                    // Map GPS shot index to relevant approach shot. 
                    val approachIndex = _uiState.value.trackedShots.filterIndexed { i, s -> i < index && s.shotType == ShotType.APPROACH }.size
                    
                    if (approachIndex < actualShots.size) {
                        val existing = actualShots[approachIndex]
                        roundRepository.updateShot(existing.copy(
                            clubId = trackedShot.clubId,
                            distanceToPin = trackedShot.distanceToPin, 
                            distanceTraveled = trackedShot.distanceYards,
                            startLat = trackedShot.location.latitude,
                            startLng = trackedShot.location.longitude
                        ))
                    } else {
                        roundRepository.insertShot(Shot(
                            holeStatId = holeStatId,
                            shotNumber = actualShots.size + 1,
                            clubId = trackedShot.clubId,
                            startLat = trackedShot.location.latitude,
                            startLng = trackedShot.location.longitude,
                            distanceToPin = trackedShot.distanceToPin,
                            distanceTraveled = trackedShot.distanceYards
                        ))
                    }
                }
                ShotType.CHIP -> {
                    // Update HoleStat chip info
                    val currentHoleStat = roundRepository.getHoleStatsForRound(_uiState.value.roundId!!).first().find { it.id == holeStatId }
                    currentHoleStat?.let {
                        roundRepository.updateHoleStat(it.copy(
                            chips = it.chips.coerceAtLeast(1),
                            chipDistance = trackedShot.distanceYards ?: it.chipDistance
                        ))
                    }
                }
                else -> { /* PUTT is not tracked via GPS */ }
            }
        }
    }
}
