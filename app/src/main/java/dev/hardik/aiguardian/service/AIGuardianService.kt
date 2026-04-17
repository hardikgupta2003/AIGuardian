package dev.hardik.aiguardian.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import dev.hardik.aiguardian.R
import dev.hardik.aiguardian.utils.Constants
import dev.hardik.aiguardian.sos.GeoSafetyManager
import dev.hardik.aiguardian.detection.ScamDetector
import dev.hardik.aiguardian.detection.OverlayManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import android.content.pm.ServiceInfo

@AndroidEntryPoint
class AIGuardianService : Service() {

    @Inject
    lateinit var geoSafetyManager: GeoSafetyManager

    @Inject
    lateinit var scamDetector: ScamDetector

    @Inject
    lateinit var overlayManager: OverlayManager

    private lateinit var telephonyManager: TelephonyManager
    private var isMonitoringCall = false

    private val phoneStateListener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            when (state) {
                TelephonyManager.CALL_STATE_RINGING -> {
                    android.util.Log.i("AIGuardianDebug", "CALL_STATE: Ringing. Incoming from: $phoneNumber")
                    handleCallStarted(phoneNumber)
                }
                TelephonyManager.CALL_STATE_OFFHOOK -> {
                    android.util.Log.i("AIGuardianDebug", "CALL_STATE: Offhook (Call Active)")
                    if (!isMonitoringCall) {
                        handleCallStarted(phoneNumber)
                    }
                }
                TelephonyManager.CALL_STATE_IDLE -> {
                    android.util.Log.i("AIGuardianDebug", "CALL_STATE: Idle (Call Ended)")
                    handleCallEnded()
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                Constants.NOTIFICATION_ID,
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(Constants.NOTIFICATION_ID, createNotification())
        }
        
        // Start geo-safety monitoring
        geoSafetyManager.startMonitoring()

        // Start Call Detection (User Approach)
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
        android.util.Log.d("AIGuardianDebug", "SERVICE_INIT: PhoneStateListener registered")
    }

    private fun handleCallStarted(number: String?) {
        if (isMonitoringCall) return
        isMonitoringCall = true
        
        android.util.Log.i("AIGuardianDebug", "PIPELINE: Triggering audio monitoring for $number")
        scamDetector.updateActivePhoneNumber(number)
        
        // Start Overlay
        overlayManager.showCallStartInstruction {
            android.util.Log.d("AIGuardianDebug", "USER_ACTION: Instruction dismissed")
        }

        // Start Audio Capture Service
        val intent = Intent(this, AudioCaptureService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun handleCallEnded() {
        if (!isMonitoringCall) return
        isMonitoringCall = false
        
        android.util.Log.i("AIGuardianDebug", "PIPELINE: Stopping audio monitoring")
        scamDetector.updateActivePhoneNumber(null)
        
        // Stop Audio Capture Service
        stopService(Intent(this, AudioCaptureService::class.java))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.util.Log.d("AIGuardianDebug", "SERVICE_HEARTBEAT: AIGuardianService active")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        geoSafetyManager.stopMonitoring()
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
        handleCallEnded()
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, Constants.CHANNEL_ID)
            .setContentTitle("AI Guard: Protection Active")
            .setContentText("Monitoring calls and background safety")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.CHANNEL_ID,
                Constants.CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Shows the active protection status of AI Guardian"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
