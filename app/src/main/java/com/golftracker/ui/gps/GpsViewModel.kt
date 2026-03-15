package com.golftracker.ui.gps

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.golftracker.data.entity.Club
import com.golftracker.data.entity.HoleStat
import com.golftracker.data.entity.Shot
import com.golftracker.data.model.ShotOutcome
import com.golftracker.data.model.ShotType
import com.golftracker.data.repository.ClubRepository
import com.golftracker.data.repository.CourseRepository
import com.golftracker.data.repository.RoundRepository
import com.golftracker.data.repository.StatsFilter
import com.golftracker.data.repository.StatsRepository
import com.golftracker.data.repository.UserPreferencesRepository
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlin.math.sqrt
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
    val clubId: Int? = null,
    val clubName: String? = null,
    val location: LatLng,
    val targetLocation: LatLng? = null,
    val distanceYards: Int? = null,
    val distanceToPin: Int? = null,
    val outcome: ShotOutcome? = null
)

/**
 * Represent a pending update for a hole's location.
 */
data class LocationUpdate(
    val holeId: Int,
    val isTee: Boolean,
    val newLocation: LatLng
)

/**
 * Represents a single dispersion ellipse to be drawn on the map.
 * 
 * @property xOffsetYards Average left/right miss (Right = positive).
 * @property yOffsetYards Average short/long miss (Long = positive).
 * @property radiusXYards The width radius of the ellipse.
 * @property radiusYYards The depth radius of the ellipse.
 * @property correlation The skew of the ellipse (-1.0 to 1.0).
 * @property is1Sigma True if this represents 1 standard deviation (drawn darker), false for 2-sigma.
 */
data class DispersionEllipse(
    val xOffsetYards: Double,
    val yOffsetYards: Double,
    val radiusXYards: Double,
    val radiusYYards: Double,
    val correlation: Double,
    val is1Sigma: Boolean
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
 * @property dispersionEllipses List of generated dispersion boundaries for the selected club.
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
    val dispersionEllipses: List<DispersionEllipse> = emptyList(),
    val dispersionPoints: List<Pair<Double, Double>> = emptyList(),
    val rawDispersionData: com.golftracker.data.repository.RawDispersionData = com.golftracker.data.repository.RawDispersionData(),
    val showShotPanel: Boolean = false,
    val isSyncing: Boolean = false,
    val clubs: List<Club> = emptyList(),
    val roundId: Int? = null,
    val holeStatId: Int? = null,
    val holePar: Int? = null,
    val currentHole: com.golftracker.data.entity.Hole? = null,
    val pendingLocationUpdate: LocationUpdate? = null,
    val knownHoleFrame: Pair<LatLng, LatLng>? = null
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
    private val roundRepository: RoundRepository,
    private val courseRepository: CourseRepository,
    private val statsRepository: StatsRepository,
    private val userPreferencesRepository: UserPreferencesRepository
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
            updateLocations(LatLng(location.latitude, location.longitude))
        }
    }

    /**
     * Updates the UI state with a new GPS [latLng].
     * Specifically updates liveUserLocation and seeds player/flag markers on the first fix.
     */
    private fun updateLocations(latLng: LatLng) {
        _uiState.update { state ->
            state.copy(
                liveUserLocation = latLng,
                // Set player marker and flag only on the very first GPS fix
                playerLocation = state.playerLocation ?: latLng,
                flagLocation = state.flagLocation ?: resetFlagLocation(latLng)
            )
        }
        checkProximityAndAutoSuggest(latLng)
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
     * Resets the flag location based on the given player location.
     * Positions the flag 200 yards north of the player.
     */
    private fun resetFlagLocation(playerLocation: LatLng): LatLng {
        // Default to a heading of 0 (North). In a more advanced implementation,
        // we could use the next hole's GPS coordinates to determine the heading.
        return GpsUtils.computeOffset(playerLocation, 200.0, 0.0)
    }

    /**
     * Sets the round / hole context for shot tracking.
     *
     * When [roundId] and [holeStatId] are both non-null the shot-tracking
     * panel becomes visible. Changing [holeStatId] triggers a fresh load
     * of existing shots from the database.
     */
    fun setTrackingContext(roundId: Int?, holeStatId: Int?, holePar: Int?) {
        val currentState = _uiState.value
        val currentHoleStatId = currentState.holeStatId

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
            
            // Load the Hole entity to get persistent tee/green locations
            viewModelScope.launch {
                val round = roundId?.let { roundRepository.getRound(it) }
                val holes = round?.courseId?.let { courseRepository.getHoles(it).first() }
                val stat = roundRepository.getHoleStatFlow(holeStatId).first()
                val hole = holes?.find { it.id == stat?.holeId }
                
                _uiState.update { it.copy(currentHole = hole) }

                // Seed markers from persistent hole data if available
                val knownTee = hole?.teeLat?.let { lat -> hole.teeLng?.let { lng -> LatLng(lat, lng) } }
                val knownGreen = hole?.greenLat?.let { lat -> hole.greenLng?.let { lng -> LatLng(lat, lng) } }

                if (knownTee != null && knownGreen != null) {
                    // Authoritatively set markers to known locations and emit frame data
                    _uiState.update { state ->
                        state.copy(
                            playerLocation = knownTee,
                            flagLocation = knownGreen,
                            knownHoleFrame = Pair(knownTee, knownGreen)
                        )
                    }
                } else {
                    // Fallback: seed from partial data or leave for GPS to fill in
                    _uiState.update { state ->
                        state.copy(
                            playerLocation = state.playerLocation ?: knownTee,
                            flagLocation = state.flagLocation ?: knownGreen
                        )
                    }
                    // If flag is still null, reset it relative to player
                    if (_uiState.value.flagLocation == null) {
                        _uiState.value.playerLocation?.let { playerLoc ->
                            _uiState.update { it.copy(flagLocation = resetFlagLocation(playerLoc)) }
                        }
                    }
                }
            }
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
                    targetLocation = if (stat.teeTargetLat != null && stat.teeTargetLng != null) LatLng(stat.teeTargetLat, stat.teeTargetLng) else null,
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
                        targetLocation = if (shot.targetLat != null && shot.targetLng != null) LatLng(shot.targetLat, shot.targetLng) else null,
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
                updateLocations(LatLng(it.latitude, it.longitude))
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
        _uiState.update { state ->
            val updatedState = state.copy(pendingClubId = clubId)
            
            // If club has stock distance, update flag location to that "target" distance
            // EXCEPTION: Don't move the flag automatically for CHIP shots, as the target is usually the pin.
            val club = state.clubs.find { it.id == clubId }
            if (state.pendingShotType != ShotType.CHIP && club?.stockDistance != null && state.playerLocation != null) {
                // Keep the current bearing if flag is already placed, otherwise North (0.0)
                val bearing = if (state.flagLocation != null) {
                    GpsUtils.calculateBearing(state.playerLocation, state.flagLocation)
                } else 0.0
                
                val newFlag = GpsUtils.computeOffset(
                    state.playerLocation, 
                    club.stockDistance.toDouble(), 
                    bearing
                )
                updatedState.copy(flagLocation = newFlag)
            } else {
                updatedState
            }
        }
        
        // Asynchronously calculate dispersion when a club is selected
        viewModelScope.launch {
            calculateDispersion(clubId)
        }
    }

    private suspend fun calculateDispersion(clubId: Int?) = withContext(Dispatchers.Default) {
        if (clubId == null) {
            _uiState.update { it.copy(
                dispersionEllipses = emptyList(), 
                dispersionPoints = emptyList(),
                rawDispersionData = com.golftracker.data.repository.RawDispersionData()
            ) }
            return@withContext
        }
        
        val club = _uiState.value.clubs.find { it.id == clubId } ?: return@withContext
        val stockDistance = club.stockDistance ?: 150 // Fallback if no stock distance
        
        // 1. Fetch club-specific stats
        val clubFilter = StatsFilter(drivingClubId = clubId, approachClubId = clubId)
        val statsData = statsRepository.getFilteredStatsData(clubFilter).first()
        
        // Determine whether this club is primarily used off the tee or for approach
        val isTeeClub = club.type in listOf("DRIVER", "WOOD")
        val rawDispersion = if (isTeeClub) {
            statsData.driving.rawDispersion
        } else {
            statsData.approach.rawDispersion
        }
        
        // Always convert raw points for dot rendering, regardless of count
        val points = rawDispersion.points as List<com.golftracker.data.repository.DispersionPoint>
        val rawPoints = points.map { pt ->
            Pair(((pt.right ?: 0) - (pt.left ?: 0)).toDouble(), ((pt.long ?: 0) - (pt.short ?: 0)).toDouble())
        }

        // 2. Perform statistical analysis if enough data points exist
        if (rawPoints.size >= 5) {
            val filteredPoints = filterOutliers(rawPoints)
            if (filteredPoints.isNotEmpty()) {
                val ellipses = calculateEllipsesFromPoints(filteredPoints)
                _uiState.update { it.copy(
                    dispersionEllipses = ellipses, 
                    dispersionPoints = rawPoints,
                    rawDispersionData = rawDispersion
                ) }
                return@withContext
            }
        }
        
        // 3. Sparse data fallback: use handicap-based formula, but still show dots
        val estimatedHandicap = userPreferencesRepository.estimatedHandicapFlow.first()
        val actualHandicap = statsData.scoring.handicapIndex
        val activeHandicap = actualHandicap ?: estimatedHandicap ?: 15.0
        
        val ellipses = calculateFallbackEllipses(stockDistance.toDouble(), activeHandicap)
        _uiState.update { it.copy(
            dispersionEllipses = ellipses, 
            dispersionPoints = rawPoints,
            rawDispersionData = rawDispersion
        ) }
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

        // Calculate distance and dispersion for the previous shot if exists
        if (updatedShots.isNotEmpty()) {
            val lastIdx = updatedShots.size - 1
            val lastShot = updatedShots[lastIdx]
            val dist = GpsUtils.calculateDistanceYards(lastShot.location, currentLocation)
            
            // If previous shot has a target, estimate dispersion
            var dispersionOffsets: GpsUtils.DispersionOffsets? = null
            if (lastShot.targetLocation != null) {
                dispersionOffsets = GpsUtils.calculateDispersionOffsets(
                    start = lastShot.location,
                    target = lastShot.targetLocation,
                    actual = currentLocation
                )
            }

            updatedShots[lastIdx] = lastShot.copy(
                distanceYards = dist
            )

            // Persist the distance update for the previous shot
            persistShotUpdate(updatedShots[lastIdx], lastIdx, dispersionOffsets)
        }

        val finalTrackedShot = newTrackedShot.copy(targetLocation = state.flagLocation)
        updatedShots.add(finalTrackedShot)
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
        persistShotUpdate(finalTrackedShot, updatedShots.size - 1)

        // Persistent Tee Location logic
        if (newTrackedShot.shotType == ShotType.TEE) {
            val hole = state.currentHole
            if (hole != null) {
                val newTee = newTrackedShot.location
                if (hole.teeLat == null || hole.teeLng == null) {
                    // Save automatically the first time
                    viewModelScope.launch {
                        courseRepository.updateHole(hole.copy(teeLat = newTee.latitude, teeLng = newTee.longitude))
                        _uiState.update { it.copy(currentHole = it.currentHole?.copy(teeLat = newTee.latitude, teeLng = newTee.longitude)) }
                    }
                } else {
                    // Check for > 10 yard discrepancy
                    val oldTee = LatLng(hole.teeLat, hole.teeLng)
                    val dist = GpsUtils.calculateDistanceYards(oldTee, newTee)
                    if (dist > 10) {
                        _uiState.update { 
                            it.copy(pendingLocationUpdate = LocationUpdate(hole.id, true, newTee))
                        }
                    }
                }
            }
        }
    }

    /**
     * Finalizes the last tracked shot by calculating its distance and dispersion 
     * based on the player's current position (presumably on the green).
     */
    fun onGreenReached() {
        val state = _uiState.value
        val currentLocation = state.playerLocation ?: return
        val updatedShots = state.trackedShots.toMutableList()

        if (updatedShots.isNotEmpty()) {
            val lastIdx = updatedShots.size - 1
            val lastShot = updatedShots[lastIdx]
            
            // Calculate distance and dispersion from last shot to green position
            val dist = GpsUtils.calculateDistanceYards(lastShot.location, currentLocation)
            
            var dispersionOffsets: GpsUtils.DispersionOffsets? = null
            if (lastShot.targetLocation != null) {
                dispersionOffsets = GpsUtils.calculateDispersionOffsets(
                    start = lastShot.location,
                    target = lastShot.targetLocation,
                    actual = currentLocation
                )
            }

            updatedShots[lastIdx] = lastShot.copy(
                distanceYards = dist
            )
            
            _uiState.update { it.copy(trackedShots = updatedShots) }

            // Persist the final distance and dispersion update
            persistShotUpdate(updatedShots[lastIdx], lastIdx, dispersionOffsets)
        }

        // Persistent Green Location logic
        val hole = state.currentHole
        if (hole != null) {
            val newGreen = currentLocation
            if (hole.greenLat == null || hole.greenLng == null) {
                // Save automatically the first time
                viewModelScope.launch {
                    courseRepository.updateHole(hole.copy(greenLat = newGreen.latitude, greenLng = newGreen.longitude))
                    _uiState.update { it.copy(currentHole = it.currentHole?.copy(greenLat = newGreen.latitude, greenLng = newGreen.longitude)) }
                }
            } else {
                // Check for > 10 yard discrepancy
                val oldGreen = LatLng(hole.greenLat, hole.greenLng)
                val dist = GpsUtils.calculateDistanceYards(oldGreen, newGreen)
                if (dist > 10) {
                    _uiState.update { 
                        it.copy(pendingLocationUpdate = LocationUpdate(hole.id, false, newGreen))
                    }
                }
            }
        }
    }

    /**
     * Confirms the pending location update for a hole.
     */
    fun confirmLocationUpdate() {
        val update = _uiState.value.pendingLocationUpdate ?: return
        val hole = _uiState.value.currentHole ?: return
        
        viewModelScope.launch {
            val updatedHole = if (update.isTee) {
                hole.copy(teeLat = update.newLocation.latitude, teeLng = update.newLocation.longitude)
            } else {
                hole.copy(greenLat = update.newLocation.latitude, greenLng = update.newLocation.longitude)
            }
            courseRepository.updateHole(updatedHole)
            _uiState.update { it.copy(currentHole = updatedHole, pendingLocationUpdate = null) }
        }
    }

    fun dismissLocationUpdate() {
        _uiState.update { it.copy(pendingLocationUpdate = null) }
    }

    /** Clears the known hole frame after the camera has animated to it. */
    fun onHoleFrameConsumed() {
        _uiState.update { it.copy(knownHoleFrame = null) }
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

    private fun persistShotUpdate(
        trackedShot: TrackedShot,
        index: Int,
        dispersionOffsets: GpsUtils.DispersionOffsets? = null
    ) {
        val holeStatId = _uiState.value.holeStatId ?: return
        val state = _uiState.value

        viewModelScope.launch {
            // Auto-estimate outcome if null and we have dispersion
            val updatedShot = if (trackedShot.outcome == null && dispersionOffsets != null) {
                trackedShot.copy(outcome = GpsUtils.estimateOutcome(dispersionOffsets))
            } else {
                trackedShot
            }

            // Update UI list
            _uiState.update { s ->
                val updatedList = s.trackedShots.toMutableList()
                if (index in updatedList.indices) {
                    updatedList[index] = updatedShot
                }
                s.copy(trackedShots = updatedList)
            }

            when (updatedShot.shotType) {
                ShotType.TEE -> {
                    val currentHoleStat = roundRepository.getHoleStatsForRound(state.roundId!!).first().find { it.id == holeStatId }
                    currentHoleStat?.let {
                        roundRepository.updateHoleStat(it.copy(
                            teeOutcome = updatedShot.outcome,
                            teeClubId = updatedShot.clubId,
                            teeShotDistance = updatedShot.distanceYards,
                            teeLat = updatedShot.location.latitude,
                            teeLng = updatedShot.location.longitude,
                            teeTargetLat = updatedShot.targetLocation?.latitude,
                            teeTargetLng = updatedShot.targetLocation?.longitude,
                            teeDispersionLeft = dispersionOffsets?.left,
                            teeDispersionRight = dispersionOffsets?.right,
                            teeDispersionShort = dispersionOffsets?.short,
                            teeDispersionLong = dispersionOffsets?.long
                        ))
                    }
                }
                ShotType.APPROACH -> {
                    val actualShots = roundRepository.getShotsForHoleStat(holeStatId).first().sortedBy { it.shotNumber }
                    val approachIndex = state.trackedShots.filterIndexed { i, s -> i < index && s.shotType == ShotType.APPROACH }.size

                    if (approachIndex in actualShots.indices) {
                        val existing = actualShots[approachIndex]
                        roundRepository.updateShot(existing.copy(
                            outcome = updatedShot.outcome,
                            clubId = updatedShot.clubId,
                            distanceToPin = updatedShot.distanceToPin,
                            distanceTraveled = updatedShot.distanceYards,
                            startLat = updatedShot.location.latitude,
                            startLng = updatedShot.location.longitude,
                            targetLat = updatedShot.targetLocation?.latitude,
                            targetLng = updatedShot.targetLocation?.longitude,
                            dispersionLeft = dispersionOffsets?.left,
                            dispersionRight = dispersionOffsets?.right,
                            dispersionShort = dispersionOffsets?.short,
                            dispersionLong = dispersionOffsets?.long
                        ))
                    } else {
                        roundRepository.insertShot(com.golftracker.data.entity.Shot(
                            holeStatId = holeStatId,
                            shotNumber = approachIndex + 1,
                            outcome = updatedShot.outcome,
                            clubId = updatedShot.clubId,
                            distanceToPin = updatedShot.distanceToPin,
                            distanceTraveled = updatedShot.distanceYards,
                            startLat = updatedShot.location.latitude,
                            startLng = updatedShot.location.longitude,
                            targetLat = updatedShot.targetLocation?.latitude,
                            targetLng = updatedShot.targetLocation?.longitude,
                            dispersionLeft = dispersionOffsets?.left,
                            dispersionRight = dispersionOffsets?.right,
                            dispersionShort = dispersionOffsets?.short,
                            dispersionLong = dispersionOffsets?.long
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

    /**
     * Filters out points that are more than 3 standard deviations from the mean.
     */
    private fun filterOutliers(points: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        if (points.isEmpty()) return emptyList()
        
        val meanX = points.map { it.first }.average()
        val meanY = points.map { it.second }.average()
        
        val varianceX = points.map { (it.first - meanX) * (it.first - meanX) }.average()
        val varianceY = points.map { (it.second - meanY) * (it.second - meanY) }.average()
        
        val stdDevX = sqrt(varianceX.coerceAtLeast(1.0))
        val stdDevY = sqrt(varianceY.coerceAtLeast(1.0))
        
        return points.filter { (x, y) ->
            Math.abs(x - meanX) <= 3 * stdDevX && Math.abs(y - meanY) <= 3 * stdDevY
        }
    }

    /**
     * Calculates 1-sigma and 2-sigma dispersion ellipses from a list of points.
     * Includes mean offset and Pearson correlation (tilt).
     */
    private fun calculateEllipsesFromPoints(points: List<Pair<Double, Double>>): List<DispersionEllipse> {
        val size = points.size.toDouble()
        val meanX = points.map { it.first }.average()
        val meanY = points.map { it.second }.average()
        
        var varX = 0.0
        var varY = 0.0
        var cov = 0.0
        
        for (pt in points) {
            val dx = pt.first - meanX
            val dy = pt.second - meanY
            varX += dx * dx
            varY += dy * dy
            cov += dx * dy
        }
        
        val finalVarX = varX / size
        val finalVarY = varY / size
        val finalCov = cov / size
        
        val stdDevX = sqrt(finalVarX.coerceAtLeast(1.0))
        val stdDevY = sqrt(finalVarY.coerceAtLeast(1.0))
        
        val correlation = if (stdDevX > 0.0 && stdDevY > 0.0) {
            (finalCov / (stdDevX * stdDevY)).coerceIn(-1.0, 1.0)
        } else 0.0
        
        return listOf(
            DispersionEllipse(meanX, meanY, stdDevX, stdDevY, correlation, true),
            DispersionEllipse(meanX, meanY, stdDevX * 2.0, stdDevY * 2.0, correlation, false)
        )
    }

    /**
     * Generates fallback ellipses based on distance and handicap.
     * Models a slightly elongated left/right dispersion with a negative correlation (Right-Short/Left-Long).
     */
    private fun calculateFallbackEllipses(distance: Double, handicap: Double): List<DispersionEllipse> {
        // Handicap formula: Scratch (4% width) to Bogey (10% width)
        val dispersionPct = 0.04 + (handicap.coerceIn(0.0, 36.0) / 20.0) * (0.10 - 0.04)
        
        val sigmaX = distance * dispersionPct
        val sigmaY = sigmaX * 0.6 // Depth is ~60% of width
        val defaultCorrelation = -0.3 // Standard "Right-Short" miss pattern
        
        return listOf(
            DispersionEllipse(0.0, 0.0, sigmaX, sigmaY, defaultCorrelation, true),
            DispersionEllipse(0.0, 0.0, sigmaX * 2.0, sigmaY * 2.0, defaultCorrelation, false)
        )
    }
}
