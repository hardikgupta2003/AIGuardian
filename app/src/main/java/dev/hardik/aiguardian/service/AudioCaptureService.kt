package dev.hardik.aiguardian.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import dev.hardik.aiguardian.R
import dev.hardik.aiguardian.stt.VoskSTTEngine
import dev.hardik.aiguardian.detection.ScamDetector
import dev.hardik.aiguardian.utils.Constants

import android.content.pm.ServiceInfo
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AudioCaptureService : Service() {

    @Inject
    lateinit var sttEngine: VoskSTTEngine

    @Inject
    lateinit var scamDetector: ScamDetector

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                Constants.NOTIFICATION_ID + 1,
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(Constants.NOTIFICATION_ID + 1, createNotification())
        }
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.util.Log.d("AIGuardianDebug", "SERVICE_START: AudioCaptureService.onStartCommand")
        startRecording()
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startRecording() {
        if (isRecording) return
        
        android.util.Log.d("AIGuardianDebug", "PIPELINE: Initializing AudioRecord")
        val bufferSize = AudioRecord.getMinBufferSize(
            16000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            16000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            android.util.Log.e("AIGuardianDebug", "PIPELINE_ERROR: AudioRecord failed to initialize")
            stopSelf()
            return
        }

        try {
            audioRecord?.startRecording()
            android.util.Log.i("AIGuardianDebug", "PIPELINE: AudioRecord started recording")
        } catch (e: Exception) {
            android.util.Log.e("AIGuardianDebug", "PIPELINE_ERROR: Failed to start AudioRecord: ${e.message}")
            stopSelf()
            return
        }

        isRecording = true
        sttEngine.startRecognition()
        scamDetector.startMonitoring()

        recordingJob = serviceScope.launch {
            val buffer = ByteArray(bufferSize)
            var lastLogTime = 0L
            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    sttEngine.processAudioChunk(buffer, read)
                    
                    val now = System.currentTimeMillis()
                    if (now - lastLogTime > 3000) {
                        android.util.Log.d("AIGuardianDebug", "PIPELINE_HEARTBEAT: Reading audio chunks...")
                        lastLogTime = now
                    }
                } else if (read < 0) {
                    android.util.Log.e("AIGuardianDebug", "PIPELINE_ERROR: Read error: $read")
                }
            }
        }
    }

    private fun stopRecording() {
        if (!isRecording) return
        isRecording = false
        recordingJob?.cancel()
        runCatching {
            audioRecord?.stop()
            audioRecord?.release()
        }
        audioRecord = null
        sttEngine.stopRecognition()
        scamDetector.stopMonitoring()
        Log.d("AudioCaptureService", "Recording pipeline stopped")
    }


    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, Constants.CHANNEL_ID)
            .setContentTitle("Call Monitoring Active")
            .setContentText("Listening via speakerphone for safety")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.CHANNEL_ID,
                Constants.CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
