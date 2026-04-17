package dev.hardik.aiguardian.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log
import androidx.core.app.NotificationCompat
import dev.hardik.aiguardian.R
import dev.hardik.aiguardian.utils.Constants
import dev.hardik.aiguardian.detection.CallInterventionManager
import dev.hardik.aiguardian.detection.ScamDetector
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ScamInCallService : InCallService() {

    @Inject
    lateinit var repository: dev.hardik.aiguardian.data.repository.SafetyRepository

    @Inject
    lateinit var scamDetector: ScamDetector

    @Inject
    lateinit var callInterventionManager: CallInterventionManager

    @Inject
    lateinit var overlayManager: dev.hardik.aiguardian.detection.OverlayManager

    private var currentCall: Call? = null

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call?, state: Int) {
            super.onStateChanged(call, state)
            Log.d("AIGuardianDebug", "Call state changed: $state")
            if (state == Call.STATE_ACTIVE) {
                currentCall = call
                startAudioCapture()
            } else if (state == Call.STATE_DISCONNECTED) {
                stopAudioCapture()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        android.util.Log.i("AIGuardianDebug", "SERVICE_BIND: ScamInCallService bound by system")
        return super.onBind(intent)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        android.util.Log.i("AIGuardianDebug", "SERVICE_BIND: ScamInCallService unbound")
        return super.onUnbind(intent)
    }

    override fun onCallAdded(call: Call?) {
        super.onCallAdded(call)
        android.util.Log.i("AIGuardianDebug", "CALL_EVENT: onCallAdded | Number: ${call?.details?.handle?.schemeSpecificPart}")
        
        // Immediate notification for user confidence
        showCallStatusNotification("Analyzing Incoming Call...", true)
        
        currentCall = call
        call?.registerCallback(callCallback)

        val phoneNumber = call?.details?.handle?.schemeSpecificPart
        scamDetector.updateActivePhoneNumber(phoneNumber)
        overlayManager.showCallStartInstruction {
            android.util.Log.d("AIGuardianDebug", "USER_ACTION: Speakerphone instruction dismissed")
        }
        
        callInterventionManager.registerActions(
            onMute = { 
                android.util.Log.i("AIGuardianDebug", "INTERVENTION: Executing MUTE")
                requestMute() 
            },
            onEndCall = { 
                android.util.Log.i("AIGuardianDebug", "INTERVENTION: Executing HANGUP")
                requestDisconnect() 
            }
        )
        
        phoneNumber?.let { number ->
            kotlinx.coroutines.GlobalScope.launch {
                if (repository.isBlocked(number)) {
                    Log.w("AIGuardianDebug", "Incoming call from BLOCKED number: $number")
                }
            }
        }
        
        if (call?.state == Call.STATE_ACTIVE) {
            startAudioCapture()
        }
    }

    override fun onCallRemoved(call: Call?) {
        super.onCallRemoved(call)
        Log.d("AIGuardianDebug", "Call removed")
        call?.unregisterCallback(callCallback)
        currentCall = null
        callInterventionManager.clearActions()
        scamDetector.updateActivePhoneNumber(null)
        stopAudioCapture()
    }

    private fun startAudioCapture() {
        Log.d("AIGuardianDebug", "Starting audio capture service")
        val intent = Intent(this, AudioCaptureService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopAudioCapture() {
        Log.d("AIGuardianDebug", "Stopping audio capture service")
        stopService(Intent(this, AudioCaptureService::class.java))
    }

    private fun requestMute(): Boolean {
        return runCatching {
            setMuted(true)
            true
        }.getOrElse {
            Log.w("AIGuardianDebug", "Mute request failed", it)
            false
        }
    }

    private fun requestDisconnect(): Boolean {
        return runCatching {
            currentCall?.disconnect()
            currentCall != null
        }.getOrElse {
            Log.w("AIGuardianDebug", "Disconnect request failed", it)
            false
        }
    }

    private fun showCallStatusNotification(message: String, isHighPriority: Boolean) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "call_events",
                "Call Analysis Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, "call_events")
            .setContentTitle("AI Guard: Active Analysis")
            .setContentText(message)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(if (isHighPriority) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(Constants.NOTIFICATION_ID + 1, notification)
    }
}
