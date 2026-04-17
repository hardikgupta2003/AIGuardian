package dev.hardik.aiguardian.stt

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import dev.hardik.aiguardian.sos.SOSManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceCommandManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sttEngine: VoskSTTEngine,
    private val sosManager: SOSManager
) {
    private var audioRecord: AudioRecord? = null
    private var isMonitoring = false
    private var monitoringJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val hotwords = listOf("help", "emergency", "bachao") // Hindi "Bachao" means help

    @SuppressLint("MissingPermission")
    fun startHotwordDetection() {
        if (isMonitoring) return
        
        val bufferSize = AudioRecord.getMinBufferSize(
            16000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            16000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        audioRecord?.startRecording()
        isMonitoring = true
        sttEngine.startRecognition()

        monitoringJob = scope.launch {
            val buffer = ByteArray(bufferSize)
            launch {
                sttEngine.transcriptionFlow.collect { transcription ->
                    checkHotwords(transcription)
                }
            }
            
            while (isMonitoring) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    sttEngine.processAudioChunk(buffer, read)
                }
            }
        }
    }

    private fun checkHotwords(text: String) {
        val lowercaseText = text.lowercase()
        hotwords.forEach { word ->
            if (lowercaseText.contains(word)) {
                Log.i("VoiceCommandManager", "Hotword detected: $word")
                scope.launch(Dispatchers.Main) {
                    sosManager.triggerSOS()
                }
            }
        }
    }

    fun stopHotwordDetection() {
        isMonitoring = false
        monitoringJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }
}
