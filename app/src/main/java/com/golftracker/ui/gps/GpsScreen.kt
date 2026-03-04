package com.golftracker.ui.gps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun GpsScreen(
    viewModel: GpsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    val locationPermissionsState = rememberMultiplePermissionsState(
        listOf(
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
        )
    )

    LaunchedEffect(locationPermissionsState.allPermissionsGranted) {
        viewModel.onPermissionResult(locationPermissionsState.allPermissionsGranted)
    }

    if (!locationPermissionsState.allPermissionsGranted) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Location permission is required for GPS rangefinder.")
                androidx.compose.material3.Button(
                    onClick = { locationPermissionsState.launchMultiplePermissionRequest() },
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text("Grant Permission")
                }
            }
        }
        return
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(uiState.userLocation ?: LatLng(0.0, 0.0), 17f)
    }

    // Update camera when location first becomes available
    LaunchedEffect(uiState.userLocation) {
        uiState.userLocation?.let {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(it, 17f)
        }
    }

    if (uiState.userLocation == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator()
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val markerState = com.google.maps.android.compose.rememberMarkerState(
            position = uiState.flagLocation ?: LatLng(0.0, 0.0)
        )

        // Sync marker position to ViewModel
        LaunchedEffect(markerState.position) {
            viewModel.onFlagDragged(markerState.position)
        }

        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = true,
                mapType = com.google.maps.android.compose.MapType.SATELLITE
            ),
            uiSettings = MapUiSettings(
                myLocationButtonEnabled = true,
                zoomControlsEnabled = false
            )
        ) {
            Marker(
                state = markerState,
                title = "Flag",
                draggable = true
            )
        }

        // Distance Overlay
        uiState.distanceInYards?.let { yards ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$yards",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "YARDS TO FLAG",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Instructions
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                "Drag the marker to the flag",
                color = Color.White,
                fontSize = 12.sp
            )
        }
    }
}
