package dev.hardik.aiguardian.stt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dev.hardik.aiguardian.detection.TranscriptSegment
import java.util.Locale

class AndroidSpeechRecognizerEngine(
    private val context: Context,
    private val hub: TranscriptHub
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    private var recognizer: SpeechRecognizer? = null
    private var running = false
    private var restartAttempts = 0
    private var lastPartial = ""
    private var preferOffline = true
    private var languageTag = Locale.getDefault().toLanguageTag()

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(preferOffline: Boolean = true, languageTag: String = Locale.getDefault().toLanguageTag()) {
        if (running) return
        running = true
        restartAttempts = 0
        lastPartial = ""
        this.preferOffline = preferOffline
        this.languageTag = languageTag
        mainHandler.post { startInternal() }
    }

    fun stop() {
        running = false
        mainHandler.post {
            runCatching { recognizer?.stopListening() }
            runCatching { recognizer?.cancel() }
            runCatching { recognizer?.destroy() }
            recognizer = null
        }
    }

    private fun startInternal() {
        if (!running) return

        if (!isAvailable()) {
            android.util.Log.w("AIGuardianDebug", "STT_SPEECH: SpeechRecognizer not available on this device")
            return
        }

        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).also { it.setRecognitionListener(listener) }
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

        android.util.Log.i(
            "AIGuardianDebug",
            "STT_SPEECH: startListening(preferOffline=$preferOffline, lang=$languageTag)"
        )
        runCatching { recognizer?.startListening(intent) }.onFailure { e ->
            android.util.Log.e("AIGuardianDebug", "STT_SPEECH_ERROR: startListening failed: ${e.message}")
            scheduleRestart()
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            restartAttempts = 0
        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (text.isBlank() || text == lastPartial) return
            lastPartial = text
            hub.tryEmit(TranscriptSegment(text = text, isFinal = false))
            android.util.Log.d("AIGuardianDebug", "STT_RESULT: [Speech Partial] $text")
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (text.isNotBlank()) {
                lastPartial = ""
                hub.tryEmit(TranscriptSegment(text = text, isFinal = true))
                android.util.Log.i("AIGuardianDebug", "STT_RESULT: [Speech FINAL] $text")
            }
            scheduleRestart()
        }

        override fun onError(error: Int) {
            if (!running) return
            android.util.Log.w("AIGuardianDebug", "STT_SPEECH_ERROR: onError=$error (restarting)")
            scheduleRestart()
        }
    }

    private fun scheduleRestart() {
        if (!running) return
        restartAttempts += 1
        val delayMs = (250L * restartAttempts).coerceAtMost(2000L)
        mainHandler.postDelayed({ startInternal() }, delayMs)
    }
}
