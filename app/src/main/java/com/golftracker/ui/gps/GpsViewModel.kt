package com.golftracker.ui.gps

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GpsUiState(
    val userLocation: LatLng? = null,
    val flagLocation: LatLng? = null,
    val distanceInYards: Int? = null,
    val isLocationPermissionGranted: Boolean = false
)

@HiltViewModel
class GpsViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(GpsUiState())
    val uiState: StateFlow<GpsUiState> = _uiState.asStateFlow()

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    fun onPermissionResult(isGranted: Boolean) {
        _uiState.update { it.copy(isLocationPermissionGranted = isGranted) }
        if (isGranted) {
            startLocationUpdates()
        }
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        viewModelScope.launch {
            // Get last known location immediately
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    val latLng = LatLng(it.latitude, it.longitude)
                    _uiState.update { state -> 
                        state.copy(
                            userLocation = latLng,
                            // Initialize flag location nearby if not set
                            flagLocation = state.flagLocation ?: LatLng(it.latitude + 0.001, it.longitude + 0.001)
                        )
                    }
                    calculateDistance()
                }
            }
            
            // In a real app, we'd setup a LocationRequest here for continuous updates
            // For now, let's just use the current location periodically or on-demand
        }
    }

    fun onFlagDragged(newLocation: LatLng) {
        _uiState.update { it.copy(flagLocation = newLocation) }
        calculateDistance()
    }

    private fun calculateDistance() {
        val state = _uiState.value
        val user = state.userLocation
        val flag = state.flagLocation

        if (user != null && flag != null) {
            val results = FloatArray(1)
            Location.distanceBetween(
                user.latitude, user.longitude,
                flag.latitude, flag.longitude,
                results
            )
            val distanceInMeters = results[0]
            val distanceInYards = (distanceInMeters * 1.09361).toInt()
            _uiState.update { it.copy(distanceInYards = distanceInYards) }
        }
    }
}
