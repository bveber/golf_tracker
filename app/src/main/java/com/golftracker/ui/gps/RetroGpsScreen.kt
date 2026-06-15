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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import com.golftracker.data.model.ApproachLie
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.DragState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState

private val StartColor = Color(0xFF29B6F6)   // light blue
private val TargetColor = Color(0xFFFFD600)  // yellow
private val OutcomeColor = Color(0xFFFF7043) // orange-red

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RetroGpsScreen(
    onNavigateBack: () -> Unit,
    viewModel: RetroGpsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val cameraPositionState = rememberCameraPositionState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Request location permission on entry and pass result to ViewModel for fallback camera position
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted -> if (isGranted) viewModel.fetchFallbackLocation() }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.fetchFallbackLocation()
        else permissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
    }

    // Center camera: prefer known hole tee/green bounds, fall back to current device location
    LaunchedEffect(uiState.holeTeeLatLng, uiState.holeGreenLatLng, uiState.fallbackLocation) {
        val holePoints = listOfNotNull(uiState.holeTeeLatLng, uiState.holeGreenLatLng)
        when {
            holePoints.size >= 2 -> {
                val bounds = LatLngBounds.builder().apply { holePoints.forEach { include(it) } }.build()
                cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 120))
            }
            holePoints.size == 1 -> {
                cameraPositionState.animate(
                    CameraUpdateFactory.newCameraPosition(CameraPosition.fromLatLngZoom(holePoints[0], 17f))
                )
            }
            uiState.fallbackLocation != null -> {
                cameraPositionState.animate(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.fromLatLngZoom(uiState.fallbackLocation!!, 17f)
                    )
                )
            }
        }
    }

    LaunchedEffect(uiState.savedMessage) {
        uiState.savedMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSavedMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(if (uiState.holeNumber > 0) "Pin Shots: Hole ${uiState.holeNumber}" else "Pin Shots")
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(mapType = MapType.SATELLITE),
                uiSettings = MapUiSettings(zoomControlsEnabled = false),
                onMapClick = { latLng -> viewModel.tapMap(latLng) }
            ) {
                // Permanent course markers
                uiState.holeTeeLatLng?.let {
                    Marker(
                        state = rememberMarkerState(position = it),
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN),
                        title = "Tee Box"
                    )
                }
                uiState.holeGreenLatLng?.let {
                    Marker(
                        state = rememberMarkerState(position = it),
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN),
                        title = "Green"
                    )
                }

                // Ghost outcomes for non-selected shots
                uiState.shots.forEachIndexed { index, shot ->
                    if (index != uiState.selectedIndex) {
                        shot.outcomePin?.let { pos ->
                            Marker(
                                state = rememberMarkerState(position = pos),
                                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET),
                                alpha = 0.5f,
                                title = shot.label
                            )
                        }
                    }
                }

                // Active shot pins - keyed so marker states reset on shot change
                val selectedShot = uiState.selectedShot
                key(uiState.selectedIndex) {
                    if (selectedShot != null) {
                        selectedShot.startPin?.let { pos ->
                            DraggableRetroMarker(
                                position = pos,
                                hue = BitmapDescriptorFactory.HUE_AZURE,
                                title = "Start (${selectedShot.label})",
                                onDragEnd = { viewModel.updatePin(RetroPinType.START, it) }
                            )
                        }
                        selectedShot.targetPin?.let { pos ->
                            DraggableRetroMarker(
                                position = pos,
                                hue = BitmapDescriptorFactory.HUE_YELLOW,
                                title = "Target (${selectedShot.label})",
                                onDragEnd = { viewModel.updatePin(RetroPinType.TARGET, it) }
                            )
                        }
                        selectedShot.outcomePin?.let { pos ->
                            DraggableRetroMarker(
                                position = pos,
                                hue = BitmapDescriptorFactory.HUE_ORANGE,
                                title = "Outcome (${selectedShot.label})",
                                onDragEnd = { viewModel.updatePin(RetroPinType.OUTCOME, it) }
                            )
                        }

                        // Line from start to outcome
                        val linePoints = listOfNotNull(selectedShot.startPin, selectedShot.outcomePin)
                        if (linePoints.size == 2) {
                            Polyline(points = linePoints, color = Color.White, width = 3f)
                        }
                        // Line from start to target
                        val targetLine = listOfNotNull(selectedShot.startPin, selectedShot.targetPin)
                        if (targetLine.size == 2) {
                            Polyline(points = targetLine, color = Color(0xFFFFD600), width = 2f)
                        }
                    }
                }
            }

            // Bottom control panel
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Shot selector chips (only shown when more than one shot)
                if (uiState.shots.size > 1) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(uiState.shots) { index, shot ->
                            FilterChip(
                                selected = index == uiState.selectedIndex,
                                onClick = { viewModel.selectShot(index) },
                                label = { Text(shot.label) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Pin type selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RetroPinType.entries.forEach { pinType ->
                        val color = when (pinType) {
                            RetroPinType.START -> StartColor
                            RetroPinType.TARGET -> TargetColor
                            RetroPinType.OUTCOME -> OutcomeColor
                        }
                        FilterChip(
                            selected = uiState.activePin == pinType,
                            onClick = { viewModel.setActivePin(pinType) },
                            label = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                    )
                                    Text(pinType.name.lowercase().replaceFirstChar { it.uppercase() })
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Club selector
                if (uiState.clubs.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            FilterChip(
                                selected = uiState.selectedShot?.clubId == null,
                                onClick = { viewModel.updateClub(null) },
                                label = { Text("No Club") }
                            )
                        }
                        items(uiState.clubs) { club ->
                            FilterChip(
                                selected = uiState.selectedShot?.clubId == club.id,
                                onClick = { viewModel.updateClub(club.id) },
                                label = { Text(club.name) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Lie / shot type selector (not shown for tee shot — it's always TEE)
                val shot = uiState.selectedShot
                if (shot != null && !shot.isTeeShotOnHoleStat) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ApproachLie.entries.forEach { lie ->
                            val lieLabel = when (lie) {
                                ApproachLie.TEE -> "Tee"
                                ApproachLie.FAIRWAY -> "Fairway"
                                ApproachLie.ROUGH -> "Rough"
                                ApproachLie.SAND -> "Sand"
                                ApproachLie.OTHER -> "Other"
                            }
                            FilterChip(
                                selected = shot.lie == lie,
                                onClick = { viewModel.updateLie(lie) },
                                label = { Text(lieLabel) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Distance + dispersion readout
                val infoShot = uiState.selectedShot
                val hasAllPins = infoShot?.startPin != null && infoShot.targetPin != null && infoShot.outcomePin != null
                val hasDistancePins = infoShot?.startPin != null && infoShot.outcomePin != null

                if (hasAllPins) {
                    val d = GpsUtils.calculateDispersionOffsets(infoShot!!.startPin!!, infoShot.targetPin!!, infoShot.outcomePin!!)
                    val dist = GpsUtils.calculateDistanceYards(infoShot.startPin, infoShot.outcomePin)
                    val dispParts = buildList {
                        if ((d.left ?: 0) > 0) add("${d.left}yd L")
                        if ((d.right ?: 0) > 0) add("${d.right}yd R")
                        if ((d.short ?: 0) > 0) add("${d.short}yd Short")
                        if ((d.long ?: 0) > 0) add("${d.long}yd Long")
                    }.ifEmpty { listOf("On Target") }
                    Text(
                        text = "$dist yds  •  ${dispParts.joinToString("  ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else if (hasDistancePins) {
                    val dist = GpsUtils.calculateDistanceYards(infoShot!!.startPin!!, infoShot.outcomePin!!)
                    Text("$dist yds", style = MaterialTheme.typography.bodySmall)
                } else {
                    val hint = when (uiState.activePin) {
                        RetroPinType.START -> "Tap map to place start pin"
                        RetroPinType.TARGET -> "Tap map to place target pin"
                        RetroPinType.OUTCOME -> "Tap map to place outcome pin"
                    }
                    Text(hint, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Save + Add Next Shot buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { viewModel.saveCurrentShot() },
                        enabled = !uiState.isSaving,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (uiState.isSaving) "Saving…" else "Save Shot")
                    }
                    Button(
                        onClick = { viewModel.addNextShot() },
                        enabled = !uiState.isSaving,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Add Next Shot")
                    }
                }
            }
        }
    }
}

@Composable
private fun DraggableRetroMarker(
    position: LatLng,
    hue: Float,
    title: String,
    onDragEnd: (LatLng) -> Unit
) {
    val markerState = remember { MarkerState(position = position) }
    LaunchedEffect(position) { markerState.position = position }
    LaunchedEffect(markerState.dragState) {
        if (markerState.dragState == DragState.END) {
            onDragEnd(markerState.position)
        }
    }
    Marker(
        state = markerState,
        icon = BitmapDescriptorFactory.defaultMarker(hue),
        draggable = true,
        title = title
    )
}
