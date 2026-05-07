package com.ochotka.app.common.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class LocationHelper(private val context: Context) {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    fun hasFinePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    fun hasPermission(): Boolean =
        hasFinePermission() ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

    fun isLocationEnabled(): Boolean {
        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return runCatching {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }.getOrDefault(false)
    }

    /**
     * Pobiera aktualną lokalizację użytkownika.
     * Wymaga uprzedniego przyznania uprawnień.
     */
    suspend fun getCurrentLocation(): Location? {
        if (!hasPermission() || !isLocationEnabled()) return null
        return getCurrentLocationForPriority(Priority.PRIORITY_HIGH_ACCURACY)
            ?: getCurrentLocationForPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
    }

    private suspend fun getCurrentLocationForPriority(priority: Int): Location? {
        return suspendCancellableCoroutine { cont ->
            val cts = CancellationTokenSource()
            cont.invokeOnCancellation { cts.cancel() }
            try {
                fusedClient.getCurrentLocation(priority, cts.token)
                    .addOnSuccessListener { location -> cont.resume(location) }
                    .addOnFailureListener { e -> cont.resumeWithException(e) }
            } catch (e: SecurityException) {
                cont.resume(null)
            }
        }
    }

    /**
     * Pobiera ostatnio znana lokalizację (szybsza, może być null).
     */
    suspend fun getLastLocation(): Location? {
        if (!hasPermission() || !isLocationEnabled()) return null
        return suspendCancellableCoroutine { cont ->
            try {
                fusedClient.lastLocation
                    .addOnSuccessListener { location -> cont.resume(location) }
                    .addOnFailureListener { cont.resume(null) }
            } catch (e: SecurityException) {
                cont.resume(null)
            }
        }
    }

    companion object {
        // Centrum Poznania – fallback gdy brak lokalizacji
        const val POZNAN_LAT = 52.4064
        const val POZNAN_LNG = 16.9252
    }
}
