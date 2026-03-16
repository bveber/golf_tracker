/**
 * GPS Rangefinder and Shot Tracking screen.
 *
 * Displays a satellite Google Map with a draggable player marker (green)
 * and flag marker (red). A cyan polyline and yardage label show the
 * distance between them. When launched from the hole-tracking screen
 * with a round context, a bottom panel allows users to record tee,
 * approach, and chip shots tied to GPS coordinates.
 */
package com.golftracker.ui.gps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.SportsGolf
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.golftracker.data.entity.Club
import com.golftracker.data.model.ShotType
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.DragState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLngBounds

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GpsScreen(
    roundId: Int? = null,
    holeStatId: Int? = null,
    holePar: Int? = null,
    onClose: () -> Unit = {},
    viewModel: GpsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(roundId, holeStatId, holePar) {
        viewModel.setTrackingContext(roundId, holeStatId, holePar)
    }

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
                ) { Text("Grant Permission") }
            }
        }
        return
    }

    if (uiState.playerLocation == null || uiState.isSyncing) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator()
        }
        return
    }

    val initialPlayer = uiState.playerLocation!!
    val initialFlag   = uiState.flagLocation ?: LatLng(0.0, 0.0)

    val playerMarkerState = rememberMarkerState(position = initialPlayer)
    val flagMarkerState   = rememberMarkerState(position = initialFlag)

    // Sync state from ViewModel back to markers (e.g. for Snap to Me or Club Stock Distance)
    LaunchedEffect(uiState.playerLocation) {
        uiState.playerLocation?.let {
            if (playerMarkerState.position != it) {
                playerMarkerState.position = it
            }
        }
    }
    LaunchedEffect(uiState.flagLocation) {
        uiState.flagLocation?.let {
            if (flagMarkerState.position != it) {
                flagMarkerState.position = it
            }
        }
    }

    // Sync back to ViewModel only when dragging ends to prevent jitter
    LaunchedEffect(playerMarkerState.dragState) {
        if (playerMarkerState.dragState == DragState.END) {
            viewModel.onPlayerDragged(playerMarkerState.position)
        }
    }
    LaunchedEffect(flagMarkerState.dragState) {
        if (flagMarkerState.dragState == DragState.END) {
            viewModel.onFlagDragged(flagMarkerState.position)
        }
    }

    val distanceYards = remember(playerMarkerState.position, flagMarkerState.position) {
        GpsUtils.calculateDistanceYards(playerMarkerState.position, flagMarkerState.position)
    }

    val currentPolylinePoints = remember(playerMarkerState.position, flagMarkerState.position) {
        listOf(playerMarkerState.position, flagMarkerState.position)
    }
    val currentMidPoint = remember(playerMarkerState.position, flagMarkerState.position) {
        GpsUtils.midpoint(playerMarkerState.position, flagMarkerState.position)
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialPlayer, 17f)
    }
    var hasCentered by remember { mutableStateOf(false) }

    // Frame camera to known tee/green bounds when available
    LaunchedEffect(uiState.knownHoleFrame) {
        val frame = uiState.knownHoleFrame
        if (frame != null) {
            val bounds = LatLngBounds.builder()
                .include(frame.first)   // tee
                .include(frame.second)  // green
                .build()
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngBounds(bounds, 120)
            )
            viewModel.onHoleFrameConsumed()
            hasCentered = true
        }
    }

    // Fallback: center on GPS if no known hole frame was used
    LaunchedEffect(uiState.playerLocation) {
        if (!hasCentered) {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(initialPlayer, 17f)
            hasCentered = true
        }
    }

    // Midpoint yardage label update
    val midpointState = rememberMarkerState(position = currentMidPoint)
    LaunchedEffect(currentMidPoint) {
        midpointState.position = currentMidPoint
    }
    Box(modifier = Modifier.fillMaxSize()) {
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
            MapOverlays(
                uiState = uiState,
                playerMarkerState = playerMarkerState,
                flagMarkerState = flagMarkerState,
                midpointState = midpointState,
                distanceYards = distanceYards,
                currentPolylinePoints = currentPolylinePoints
            )
        }

        // Shot Tracking Panel
        if (uiState.showShotPanel) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 0.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf(ShotType.TEE, ShotType.APPROACH, ShotType.CHIP).forEach { type ->
                            FilterChip(
                                selected = uiState.pendingShotType == type,
                                onClick = { viewModel.onShotTypeSelected(type) },
                                label = { Text(type.name, color = Color.White) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color.White.copy(alpha = 0.3f)
                                )
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val filteredClubs = when(uiState.pendingShotType) {
                        ShotType.TEE -> uiState.clubs.filter { it.type in listOf("DRIVER", "WOOD", "HYBRID", "IRON") }
                        ShotType.APPROACH -> uiState.clubs.filter { it.type != "DRIVER" && it.type != "PUTTER" }
                        ShotType.CHIP -> uiState.clubs.filter { it.type == "WEDGE" }
                        else -> emptyList()
                    }
                    
                    ClubDropdown(
                        label = "Select Club",
                        clubs = filteredClubs,
                        selectedClubId = uiState.pendingClubId,
                        onClubSelected = { viewModel.onClubSelected(it) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    androidx.compose.material3.Button(
                        onClick = { viewModel.onTrackShot() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(Icons.Filled.SportsGolf, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Track ${uiState.pendingShotType.name} Shot")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Green transition button
                    val isNearGreen = distanceYards < 20
                    androidx.compose.material3.OutlinedButton(
                        onClick = {
                            viewModel.onGreenReached()
                            onClose()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        border = androidx.compose.foundation.BorderStroke(
                            2.dp, 
                            if (isNearGreen) Color.Green else Color.White.copy(alpha = 0.5f)
                        )
                    ) {
                        Text(
                            "⛳ I'm on the Green", 
                            color = if (isNearGreen) Color.Green else Color.White,
                            fontWeight = if (isNearGreen) FontWeight.Black else FontWeight.Normal
                        )
                    }
                    
                    // Shot History Ribbon
                    if (uiState.trackedShots.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(uiState.trackedShots.size) { index ->
                                val shot = uiState.trackedShots[index]
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White.copy(alpha = 0.1f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "${index + 1}: ${shot.clubName ?: shot.shotType.name} ${shot.distanceYards?.let { "${it}y" } ?: "..."}",
                                            color = Color.White,
                                            fontSize = 12.sp
                                        )
                                        IconButton(
                                            onClick = { viewModel.onDeleteShot(index) },
                                            modifier = Modifier.height(24.dp).width(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.Close,
                                                contentDescription = "Delete",
                                                tint = Color.White.copy(alpha = 0.5f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Snap to real GPS location button
        ExtendedFloatingActionButton(
            onClick = {
                uiState.liveUserLocation?.let { realLocation ->
                    playerMarkerState.position = realLocation
                    viewModel.onPlayerDragged(realLocation)
                }
            },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .padding(bottom = if (uiState.showShotPanel) 240.dp else 16.dp),
            containerColor = Color.White,
            contentColor = Color.Black
        ) {
            Icon(Icons.Default.MyLocation, contentDescription = "Snap to Me")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Snap to Me")
        }

        uiState.pendingLocationUpdate?.let { update ->
            LocationUpdateDialog(
                isTee = update.isTee,
                onConfirm = { viewModel.confirmLocationUpdate() },
                onDismiss = { viewModel.dismissLocationUpdate() }
            )
        }
    }
}

@Composable
fun LocationUpdateDialog(
    isTee: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update Hole Location?") },
        text = {
            Text("The ${if (isTee) "tee" else "green"} location you just used is more than 10 yards from the stored location. Would you like to update the permanent position for this hole?")
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onConfirm) {
                Text("Update")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Keep Old")
            }
        }
    )
}

@Composable
private fun MapOverlays(
    uiState: GpsUiState,
    playerMarkerState: com.google.maps.android.compose.MarkerState,
    flagMarkerState: com.google.maps.android.compose.MarkerState,
    midpointState: com.google.maps.android.compose.MarkerState,
    distanceYards: Int,
    currentPolylinePoints: List<LatLng>
) {
    Marker(
        state = playerMarkerState,
        title = "Player",
        draggable = true,
        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
    )
    Marker(
        state = flagMarkerState,
        title = "Flag",
        draggable = true,
        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
    )

    // Dispersion Overlay
    val targetLoc = flagMarkerState.position
    if (uiState.dispersionEllipses.isNotEmpty()) {
        val bearing = GpsUtils.calculateBearing(playerMarkerState.position, targetLoc)
        for (ellipse in uiState.dispersionEllipses) {
            key(ellipse) {
                val polygonPoints = remember(targetLoc, ellipse, bearing) {
                    GpsUtils.createDispersionPolygon(
                        targetCenter = targetLoc,
                        xOffsetYards = ellipse.xOffsetYards,
                        yOffsetYards = ellipse.yOffsetYards,
                        radiusXYards = ellipse.radiusXYards,
                        radiusYYards = ellipse.radiusYYards,
                        bearingDegrees = bearing,
                        correlation = ellipse.correlation
                    )
                }

                val fillColor = if (ellipse.is1Sigma) {
                    Color.Yellow.copy(alpha = 0.4f)
                } else {
                    Color.Yellow.copy(alpha = 0.15f)
                }
                val strokeColor = if (ellipse.is1Sigma) {
                    Color.Yellow.copy(alpha = 0.8f)
                } else {
                    Color.Yellow.copy(alpha = 0.4f)
                }

                com.google.maps.android.compose.Polygon(
                    points = polygonPoints,
                    fillColor = fillColor,
                    strokeColor = strokeColor,
                    strokeWidth = 3f,
                    zIndex = if (ellipse.is1Sigma) 2f else 1f
                )
            }
        }
    }

    // Raw dispersion shot outcome dots
    if (uiState.dispersionPoints.isNotEmpty()) {
        val bearing = GpsUtils.calculateBearing(playerMarkerState.position, targetLoc)
        for ((index, point) in uiState.dispersionPoints.withIndex()) {
            val (xYards, yYards) = point
            // Convert (x=right/left, y=long/short) yard offsets to a LatLng relative to flag
            // Y offset goes along the bearing, X offset goes perpendicular (bearing + 90)
            val depthOffset = GpsUtils.computeOffset(targetLoc, yYards, bearing)
            val dotLocation = GpsUtils.computeOffset(depthOffset, xYards, (bearing + 90.0) % 360.0)

            key("dispersion_dot_$index") {
                com.google.maps.android.compose.Circle(
                    center = dotLocation,
                    radius = 0.75, // meters — renders as a small dot
                    fillColor = Color.White.copy(alpha = 0.85f),
                    strokeColor = Color.White,
                    strokeWidth = 1f,
                    zIndex = 3f
                )
            }
        }
    }

    // Previous shots markers and polyline
    if (uiState.trackedShots.isNotEmpty()) {
        val shotPoints = uiState.trackedShots.map { it.location }
        
        com.google.maps.android.compose.Polyline(
            points = shotPoints,
            color = Color.White.copy(alpha = 0.5f),
            width = 3f,
            pattern = listOf(com.google.android.gms.maps.model.Dash(20f), com.google.android.gms.maps.model.Gap(10f))
        )

        for (index in uiState.trackedShots.indices) {
            val shot = uiState.trackedShots[index]
            key("shot_$index") {
                val shotMarkerState = rememberMarkerState(position = shot.location)
                Marker(
                    state = shotMarkerState,
                    title = "Shot ${index + 1}: ${shot.clubName ?: shot.shotType.name}",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE),
                    alpha = 0.7f
                )
            }
        }
    }
    
    com.google.maps.android.compose.Polyline(
        points = currentPolylinePoints,
        color = Color.Cyan,
        width = 5f
    )
    
    com.google.maps.android.compose.MarkerComposable(
        keys = arrayOf(distanceYards),
        state = midpointState,
        anchor = androidx.compose.ui.geometry.Offset(0.0f, 0.5f),
        onClick = { false }
    ) {
        Box(
            modifier = Modifier
                .padding(start = 24.dp)
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                "$distanceYards yds",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClubDropdown(
    label: String,
    clubs: List<Club>,
    selectedClubId: Int?,
    onClubSelected: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedClub = clubs.firstOrNull { it.id == selectedClubId }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedClub?.let {
                "${it.name}${it.stockDistance?.let { d -> " ($d yds)" } ?: ""}"
            } ?: "Select club",
            onValueChange = {},
            readOnly = true,
            label = { Text(label, color = Color.White) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedLabelColor = Color.White,
                unfocusedLabelColor = Color.White,
                focusedTrailingIconColor = Color.White,
                unfocusedTrailingIconColor = Color.White,
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.White.copy(alpha = 0.5f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color.DarkGray)
        ) {
            DropdownMenuItem(
                text = { Text("None", color = Color.White) },
                onClick = {
                    onClubSelected(null)
                    expanded = false
                }
            )
            clubs.forEach { club ->
                DropdownMenuItem(
                    text = {
                        Text("${club.name}${club.stockDistance?.let { " ($it yds)" } ?: ""}", color = Color.White)
                    },
                    onClick = {
                        onClubSelected(club.id)
                        expanded = false
                    }
                )
            }
        }
    }
}
