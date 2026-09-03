package com.golftracker.ui.gps

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

interface LocationSource {
    fun startUpdates(): Flow<LatLng>
}

class FusedLocationSource @Inject constructor(
    @ApplicationContext private val context: Context
) : LocationSource {

    @SuppressLint("MissingPermission")
    override fun startUpdates(): Flow<LatLng> = callbackFlow {
        val client = LocationServices.getFusedLocationProviderClient(context)

        // Seed immediately from last known location
        client.lastLocation.addOnSuccessListener { loc ->
            loc?.let { trySend(LatLng(it.latitude, it.longitude)) }
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMinUpdateIntervalMillis(3000L)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(LatLng(it.latitude, it.longitude)) }
            }
        }

        client.requestLocationUpdates(request, callback, android.os.Looper.getMainLooper())

        awaitClose { client.removeLocationUpdates(callback) }
    }
}
