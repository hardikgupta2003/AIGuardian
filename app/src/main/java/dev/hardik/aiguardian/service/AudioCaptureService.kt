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
import dev.hardik.aiguardian.stt.AndroidSpeechRecognizerEngine
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
    lateinit var speechEngine: AndroidSpeechRecognizerEngine

    @Inject
    lateinit var scamDetector: ScamDetector

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var usingSpeechRecognizer = false
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
        
        android.util.Log.d("AIGuardianDebug", "PIPELINE: Starting audio monitoring pipeline")

        // Prefer Android SpeechRecognizer for best accuracy (uses Google speech service when available).
        // It captures audio internally, so we do not run our own AudioRecord loop.
        if (speechEngine.isAvailable()) {
            usingSpeechRecognizer = true
            isRecording = true
            android.util.Log.i("AIGuardianDebug", "PIPELINE: Using SpeechRecognizer backend (best accuracy)")
            speechEngine.start(preferOffline = true, languageTag = "en-IN")
            scamDetector.startMonitoring()
            return
        }

        usingSpeechRecognizer = false
        android.util.Log.i("AIGuardianDebug", "PIPELINE: SpeechRecognizer unavailable; falling back to Vosk (PCM)")
        val sampleRateHz = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT

        val minBufferSize = AudioRecord.getMinBufferSize(sampleRateHz, channelConfig, encoding)
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            android.util.Log.e("AIGuardianDebug", "PIPELINE_ERROR: getMinBufferSize failed: $minBufferSize")
            stopSelf()
            return
        }

        // A slightly larger buffer reduces underruns when downstream processing spikes.
        val bufferSize = minBufferSize.coerceAtLeast(sampleRateHz / 2) * 2

        audioRecord = createAudioRecord(
            sampleRateHz = sampleRateHz,
            channelConfig = channelConfig,
            encoding = encoding,
            bufferSize = bufferSize
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
                    // Non-suspending; must not block the AudioRecord loop.
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

    private fun createAudioRecord(
        sampleRateHz: Int,
        channelConfig: Int,
        encoding: Int,
        bufferSize: Int
    ): AudioRecord? {
        val sources = listOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.MIC
        )

        for (source in sources) {
            val record = runCatching {
                AudioRecord(
                    source,
                    sampleRateHz,
                    channelConfig,
                    encoding,
                    bufferSize
                )
            }.getOrNull()

            if (record?.state == AudioRecord.STATE_INITIALIZED) {
                android.util.Log.i("AIGuardianDebug", "PIPELINE: AudioRecord initialized (source=$source)")
                return record
            }

            runCatching { record?.release() }
        }

        return null
    }

    private fun stopRecording() {
        if (!isRecording) return
        isRecording = false
        recordingJob?.cancel()
        if (usingSpeechRecognizer) {
            speechEngine.stop()
            scamDetector.stopMonitoring()
            usingSpeechRecognizer = false
        } else {
            runCatching {
                audioRecord?.stop()
                audioRecord?.release()
            }
            audioRecord = null
            sttEngine.stopRecognition()
            scamDetector.stopMonitoring()
        }
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
