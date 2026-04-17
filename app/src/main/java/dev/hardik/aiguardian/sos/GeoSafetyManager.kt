package dev.hardik.aiguardian.sos

import android.content.Context
import android.location.Location
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeoSafetyManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationClient: FusedLocationProviderClient
) {
    private var homeLocation: Location? = null
    private val maxDistance = 5000.0 // 5km in meters

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { location ->
                checkDistance(location)
            }
        }
    }

    fun setHomeLocation(latitude: Double, longitude: Double) {
        homeLocation = Location("Home").apply {
            this.latitude = latitude
            this.longitude = longitude
        }
    }

    fun startMonitoring() {
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 300000) // 5 mins
            .build()
        
        try {
            locationClient.requestLocationUpdates(request, locationCallback, context.mainLooper)
        } catch (e: SecurityException) {
            Log.e("GeoSafetyManager", "Permission denied for location updates", e)
        }
    }

    fun stopMonitoring() {
        locationClient.removeLocationUpdates(locationCallback)
    }

    private fun checkDistance(currentLocation: Location) {
        homeLocation?.let { home ->
            val distance = currentLocation.distanceTo(home)
            if (distance > maxDistance) {
                Log.w("GeoSafetyManager", "User is outside safe zone! Distance: $distance")
                // TODO: Trigger FCM or local notification for family
            }
        }
    }
}
