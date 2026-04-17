package dev.hardik.aiguardian.data.remote

import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

@Singleton
class FirebaseRepository @Inject constructor() {
    private val database = FirebaseDatabase.getInstance().reference

    suspend fun updateSafetyStatus(userId: String, isProtected: Boolean) {
        database.child("users").child(userId).child("isProtected").setValue(isProtected).await()
    }

    suspend fun logSOS(userId: String, location: String) {
        val sosEvent = mapOf(
            "timestamp" to System.currentTimeMillis(),
            "location" to location,
            "status" to "ACTIVE"
        )
        database.child("users").child(userId).child("sos_events").push().setValue(sosEvent).await()
    }

    suspend fun getFCMToken(): String? {
        return try {
            FirebaseMessaging.getInstance().token.await()
        } catch (e: Exception) {
            null
        }
    }
}
