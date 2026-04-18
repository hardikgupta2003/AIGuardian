package dev.hardik.aiguardian.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

@Singleton
class FirebaseRepository @Inject constructor() {
    private val database = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()

    suspend fun signInAnonymously() {
        if (auth.currentUser == null) {
            try {
                auth.signInAnonymously().await()
                android.util.Log.d("FirebaseRepository", "Anonymous sign-in success: ${auth.currentUser?.uid}")
            } catch (e: Exception) {
                android.util.Log.e("FirebaseRepository", "Anonymous sign-in failed. Ensure 'Anonymous' is enabled in Firebase Console: ${e.message}")
            }
        }
    }

    private fun getCurrentUserId(): String? = auth.currentUser?.uid

    suspend fun updateSafetyStatus(userId: String, isProtected: Boolean) {
        database.child("users").child(userId).child("isProtected").setValue(isProtected).await()
    }

    suspend fun logSOS(elderPin: String, location: String) {
        val sosEvent = mapOf(
            "timestamp" to System.currentTimeMillis(),
            "location" to location,
            "status" to "ACTIVE",
            "elderPin" to elderPin
        )
        // We log under a global events node so Cloud Functions can monitor it easily
        database.child("events").child("sos").push().setValue(sosEvent).await()
    }

    suspend fun reportScam(elderPin: String, phoneNumber: String, location: String, transcript: String) {
        val scamEvent = mapOf(
            "timestamp" to System.currentTimeMillis(),
            "phoneNumber" to phoneNumber,
            "location" to location,
            "transcript" to transcript,
            "elderPin" to elderPin
        )
        database.child("events").child("scams").push().setValue(scamEvent).await()
    }

    suspend fun pairWithElder(elderPin: String, caretakerPin: String, caretakerToken: String) {
        database.child("users").child(elderPin).child("caretakers").child(caretakerPin).setValue(caretakerToken).await()
    }

    fun observeSosEvents(elderPin: String, onEvent: (List<Map<String, Any>>) -> Unit) {
        database.child("events").child("sos")
            .orderByChild("elderPin")
            .equalTo(elderPin)
            .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    val events = snapshot.children.mapNotNull { it.value as? Map<String, Any> }
                    onEvent(events.reversed())
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            })
    }

    fun observeScamEvents(elderPin: String, onEvent: (List<Map<String, Any>>) -> Unit) {
        database.child("events").child("scams")
            .orderByChild("elderPin")
            .equalTo(elderPin)
            .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    val events = snapshot.children.mapNotNull { it.value as? Map<String, Any> }
                    onEvent(events.reversed())
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            })
    }

    suspend fun getFCMToken(): String? {
        return try {
            FirebaseMessaging.getInstance().token.await()
        } catch (e: Exception) {
            null
        }
    }
}
