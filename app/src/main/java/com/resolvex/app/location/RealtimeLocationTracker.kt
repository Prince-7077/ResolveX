package com.resolvex.app.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat

data class LiveLocationState(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
    val lastUpdatedTimeMs: Long? = null,
    val provider: String? = null,
    val isTracking: Boolean = false,
    val errorMessage: String? = null,
    val isGpsEnabled: Boolean = true
)

class RealtimeLocationTracker(private val context: Context) {
    private val locationManager: LocationManager? =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private var gpsListener: LocationListener? = null
    private var networkListener: LocationListener? = null

    var currentState: LiveLocationState = LiveLocationState()
        private set

    fun hasLocationPermission(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fineGranted || coarseGranted
    }

    fun isLocationServiceEnabled(): Boolean {
        if (locationManager == null) return false
        val gps = try { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) } catch (e: Exception) { false }
        val net = try { locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) } catch (e: Exception) { false }
        return gps || net
    }

    /**
     * Starts continuous real-time location monitoring using device GPS hardware and network provider.
     * Updates automatically as the citizen moves.
     */
    fun startContinuousTracking(
        onUpdate: (LiveLocationState) -> Unit
    ) {
        if (!hasLocationPermission()) {
            currentState = currentState.copy(
                isTracking = false,
                errorMessage = "Location permission denied. Please grant location access to show live position."
            )
            onUpdate(currentState)
            return
        }

        if (locationManager == null) {
            currentState = currentState.copy(
                isTracking = false,
                errorMessage = "Location services unavailable on this device."
            )
            onUpdate(currentState)
            return
        }

        val gpsEnabled = try { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) } catch (e: Exception) { false }
        val netEnabled = try { locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) } catch (e: Exception) { false }

        if (!gpsEnabled && !netEnabled) {
            currentState = currentState.copy(
                isTracking = false,
                isGpsEnabled = false,
                errorMessage = "GPS / Location Services disabled. Please enable device location settings."
            )
            onUpdate(currentState)
            return
        }

        // Stop any previous listener
        stopContinuousTracking()

        val listener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                val updated = LiveLocationState(
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    accuracyMeters = if (loc.hasAccuracy()) loc.accuracy else null,
                    lastUpdatedTimeMs = loc.time,
                    provider = loc.provider ?: "GPS",
                    isTracking = true,
                    errorMessage = null,
                    isGpsEnabled = true
                )
                currentState = updated
                onUpdate(updated)
            }

            override fun onProviderEnabled(provider: String) {
                currentState = currentState.copy(isGpsEnabled = true, errorMessage = null)
                onUpdate(currentState)
            }

            override fun onProviderDisabled(provider: String) {
                if (provider == LocationManager.GPS_PROVIDER && !netEnabled) {
                    currentState = currentState.copy(
                        isGpsEnabled = false,
                        errorMessage = "GPS was turned off. Live tracking paused."
                    )
                    onUpdate(currentState)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }

        gpsListener = listener
        networkListener = listener

        try {
            // First check last known location for instant display while GPS locks
            var initialLoc: Location? = null
            if (gpsEnabled) {
                initialLoc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            }
            if (initialLoc == null && netEnabled) {
                initialLoc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            }

            if (initialLoc != null) {
                currentState = LiveLocationState(
                    latitude = initialLoc.latitude,
                    longitude = initialLoc.longitude,
                    accuracyMeters = if (initialLoc.hasAccuracy()) initialLoc.accuracy else null,
                    lastUpdatedTimeMs = initialLoc.time,
                    provider = initialLoc.provider ?: "Cached",
                    isTracking = true,
                    errorMessage = null,
                    isGpsEnabled = true
                )
                onUpdate(currentState)
            }

            // Register continuous updates: 2 seconds, 1 meter minimum change
            if (gpsEnabled) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    2000L,
                    1.0f,
                    listener
                )
            }
            if (netEnabled) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    3000L,
                    2.0f,
                    listener
                )
            }

            currentState = currentState.copy(
                isTracking = true,
                errorMessage = if (initialLoc == null) "Acquiring live satellite fix..." else null
            )
            onUpdate(currentState)
        } catch (e: SecurityException) {
            currentState = currentState.copy(
                isTracking = false,
                errorMessage = "Location permission revoked: ${e.message}"
            )
            onUpdate(currentState)
        } catch (e: Exception) {
            currentState = currentState.copy(
                isTracking = false,
                errorMessage = "Unable to start location tracking: ${e.message}"
            )
            onUpdate(currentState)
        }
    }

    /**
     * Immediately stops location updates to preserve battery life when the citizen navigates away.
     */
    fun stopContinuousTracking() {
        if (locationManager != null) {
            try {
                gpsListener?.let { locationManager.removeUpdates(it) }
                networkListener?.let { locationManager.removeUpdates(it) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        gpsListener = null
        networkListener = null
        currentState = currentState.copy(isTracking = false)
    }
}
