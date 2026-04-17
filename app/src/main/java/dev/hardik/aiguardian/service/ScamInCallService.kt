package dev.hardik.aiguardian.service

import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log
import dev.hardik.aiguardian.detection.CallInterventionManager
import dev.hardik.aiguardian.detection.ScamDetector
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ScamInCallService : InCallService() {

    @Inject
    lateinit var repository: dev.hardik.aiguardian.data.repository.SafetyRepository

    @Inject
    lateinit var scamDetector: ScamDetector

    @Inject
    lateinit var callInterventionManager: CallInterventionManager

    private var currentCall: Call? = null

    private val callCallback = object : Call.Callback() {

        override fun onStateChanged(call: Call?, state: Int) {
            super.onStateChanged(call, state)
            Log.d("ScamInCallService", "Call state changed: $state")
            if (state == Call.STATE_ACTIVE) {
                currentCall = call
                startAudioCapture()
            } else if (state == Call.STATE_DISCONNECTED) {
                stopAudioCapture()
            }
        }
    }

    override fun onCallAdded(call: Call?) {
        super.onCallAdded(call)
        Log.d("ScamInCallService", "Call added")
        currentCall = call
        call?.registerCallback(callCallback)

        val phoneNumber = call?.details?.handle?.schemeSpecificPart
        scamDetector.updateActivePhoneNumber(phoneNumber)
        callInterventionManager.registerActions(
            onMute = { requestMute() },
            onEndCall = { requestDisconnect() }
        )
        phoneNumber?.let { number ->
            kotlinx.coroutines.GlobalScope.launch {
                if (repository.isBlocked(number)) {
                    Log.w("ScamInCallService", "Incoming call from BLOCKED number: $number")
                    // Handle blocked call (e.g. show immediate warning)
                }
            }
        }
        
        // If already active (e.g. service started during active call)
        if (call?.state == Call.STATE_ACTIVE) {
            startAudioCapture()
        }
    }


    override fun onCallRemoved(call: Call?) {
        super.onCallRemoved(call)
        Log.d("ScamInCallService", "Call removed")
        call?.unregisterCallback(callCallback)
        currentCall = null
        callInterventionManager.clearActions()
        scamDetector.updateActivePhoneNumber(null)
        stopAudioCapture()
    }

    private fun startAudioCapture() {
        Log.d("ScamInCallService", "Starting audio capture service")
        val intent = Intent(this, AudioCaptureService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopAudioCapture() {
        Log.d("ScamInCallService", "Stopping audio capture service")
        stopService(Intent(this, AudioCaptureService::class.java))
    }

    private fun requestMute(): Boolean {
        return runCatching {
            setMuted(true)
            true
        }.getOrElse {
            Log.w("ScamInCallService", "Mute request failed", it)
            false
        }
    }

    private fun requestDisconnect(): Boolean {
        return runCatching {
            currentCall?.disconnect()
            currentCall != null
        }.getOrElse {
            Log.w("ScamInCallService", "Disconnect request failed", it)
            false
        }
    }
}
