package dev.hardik.aiguardian.sos

import android.annotation.SuppressLint
import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SOSManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationClient: FusedLocationProviderClient,
    private val firebaseRepository: dev.hardik.aiguardian.data.remote.FirebaseRepository
) {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)

    private val smsManager: SmsManager by lazy {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    }

    private var emergencyContacts = listOf<String>() // To be loaded from DB/Prefs

    fun setEmergencyContacts(contacts: List<String>) {
        emergencyContacts = contacts
    }

    @SuppressLint("MissingPermission")
    fun triggerSOS() {
        Log.d("SOSManager", "SOS Triggered")
        
        locationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                val locationUrl = if (location != null) {
                    "https://www.google.com/maps/search/?api=1&query=${location.latitude},${location.longitude}"
                } else {
                    "Location unavailable"
                }

                Log.d("TAG", "triggerSOS: $locationUrl")
                
                sendEmergencySMS(locationUrl)
                
                // Log to Firebase for family monitoring
                scope.launch {
                    try {
                        firebaseRepository.logSOS("current_user", locationUrl)
                    } catch (e: Exception) {
                        Log.e("SOSManager", "Firebase log failed", e)
                    }
                }
            }

            .addOnFailureListener {
                Log.e("SOSManager", "Failed to get location", it)
                sendEmergencySMS("Location unavailable due to error")
            }
    }

    private fun sendEmergencySMS(locationInfo: String) {
        val message = "🚨 EMERGENCY SOS! I need help. My current location: $locationInfo"
        
        emergencyContacts.forEach { contact ->
            try {
                smsManager.sendTextMessage(contact, null, message, null, null)
                Log.d("SOSManager", "SMS sent to $contact")
            } catch (e: Exception) {
                Log.e("SOSManager", "Failed to send SMS to $contact", e)
            }
        }
    }
}
