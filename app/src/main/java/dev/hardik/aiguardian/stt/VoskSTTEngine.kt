package dev.hardik.aiguardian.stt

import android.content.Context
import android.util.Log
import dev.hardik.aiguardian.detection.TranscriptSegment
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoskSTTEngine @Inject constructor(
    private val context: Context,
    private val hub: TranscriptHub
) {
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private val recognizerLock = Any()
    private var lastPartial = ""
    private var droppedSegments = 0
    private var lastDropLogAtMs = 0L
    private var audioBytesProcessed: Long = 0

    private val _isModelReady = MutableStateFlow(false)
    val isModelReady: StateFlow<Boolean> = _isModelReady.asStateFlow()

    private val _isInitializing = MutableStateFlow(false)
    val isInitializing: StateFlow<Boolean> = _isInitializing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // IMPORTANT: Never allow STT emission to block the audio capture loop.
    // If collectors are slow, drop oldest segments rather than suspending.
    private val _transcriptionFlow = MutableSharedFlow<TranscriptSegment>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val transcriptionFlow = _transcriptionFlow.asSharedFlow()

    fun initModel(modelPath: String = "model-en-in", onComplete: (Boolean) -> Unit) {
        if (_isModelReady.value) {
            onComplete(true)
            return
        }
        
        Log.d("AIGuardianDebug", "STT: Initializing model from assets: $modelPath")
        _isInitializing.value = true
        _errorMessage.value = null
        
        StorageService.unpack(
            context,
            modelPath,
            "model",
            { model: Model ->
                android.util.Log.i("AIGuardianDebug", "STT: Model unpacked successfully")
                this.model = model
                _isModelReady.value = true
                _isInitializing.value = false
                onComplete(true)
            },
            { exception: IOException ->
                android.util.Log.e("AIGuardianDebug", "STT_ERROR: Failed to unpack model: ${exception.message}")
                _isInitializing.value = false
                _errorMessage.value = "Failed to load speech model: ${exception.message}"
                onComplete(false)
            }
        )
    }

    fun startRecognition() {
        val activeModel = model
        if (activeModel == null) {
            android.util.Log.w("AIGuardianDebug", "STT_ERROR: startRecognition called before model is ready")
            return
        }

        synchronized(recognizerLock) {
            recognizer?.close()
            recognizer = Recognizer(activeModel, 16000.0f).apply {
                setMaxAlternatives(3)
                setWords(true)
            }
            audioBytesProcessed = 0
        }
        lastPartial = ""
        android.util.Log.d("AIGuardianDebug", "STT: Recognizer started (maxAlt=3, words=true)")
    }

    /**
     * Must be fast and non-blocking (called from the real-time AudioRecord loop).
     */
    fun processAudioChunk(data: ByteArray, length: Int) {
        if (length <= 0) return

        synchronized(recognizerLock) {
            val activeRecognizer = recognizer ?: return
            audioBytesProcessed += length.toLong()
            if (activeRecognizer.acceptWaveForm(data, length)) {
                emitTranscript(activeRecognizer.result, isFinal = true)
            } else {
                emitTranscript(activeRecognizer.partialResult, isFinal = false)
            }
        }
    }

    fun stopRecognition() {
        val localRecognizer: Recognizer?
        val hadAudio: Boolean
        synchronized(recognizerLock) {
            localRecognizer = recognizer
            hadAudio = audioBytesProcessed > 0
            recognizer = null
            audioBytesProcessed = 0
        }

        if (localRecognizer != null && hadAudio) {
            // IMPORTANT: Vosk native code can abort the process if FinalResult() is called while audio feeding
            // is still happening. We avoid that by nulling the shared recognizer under lock first.
            val finalResult = localRecognizer.finalResult
            val text = extractText(finalResult, isFinal = true)
            if (text.isNotBlank()) {
                val segment = TranscriptSegment(text = text, isFinal = true)
                _transcriptionFlow.tryEmit(segment)
                hub.tryEmit(segment)
            }
        }

        runCatching { localRecognizer?.close() }
        lastPartial = ""
        android.util.Log.d("AIGuardianDebug", "STT: Recognizer stopped")
    }

    private fun emitTranscript(rawJson: String, isFinal: Boolean) {
        // RADICAL FORENSIC LOG: Print exactly what Vosk returned before any parsing
        if (Log.isLoggable("AIGuardianDebug", Log.VERBOSE)) {
            android.util.Log.v("AIGuardianDebug", "STT_RAW: $rawJson")
        }

        val text = extractText(rawJson, isFinal)
        if (text.isBlank()) return
        if (!isFinal && text == lastPartial) return

        if (isFinal) {
            lastPartial = ""
            android.util.Log.i("AIGuardianDebug", "STT_RESULT: [FINAL] $text")
        } else {
            lastPartial = text
            android.util.Log.d("AIGuardianDebug", "STT_RESULT: [Partial] $text")
        }

        val segment = TranscriptSegment(text = text, isFinal = isFinal)
        val emitted = _transcriptionFlow.tryEmit(segment)
        if (!emitted) {
            droppedSegments += 1
            val now = System.currentTimeMillis()
            if (now - lastDropLogAtMs > 5000) {
                android.util.Log.w(
                    "AIGuardianDebug",
                    "STT_DROP: Dropping transcript segments (dropped=$droppedSegments). Collector too slow."
                )
                lastDropLogAtMs = now
                droppedSegments = 0
            }
        }

        hub.tryEmit(segment)
    }

    private fun extractText(rawJson: String, isFinal: Boolean): String {
        return runCatching {
            val json = JSONObject(rawJson)
            if (isFinal) {
                // With maxAlternatives, results come in an "alternatives" array
                val alternatives = json.optJSONArray("alternatives")
                if (alternatives != null && alternatives.length() > 0) {
                    // Pick the first (highest confidence) alternative
                    val best = alternatives.getJSONObject(0)
                    best.optString("text", "").trim()
                } else {
                    // Fallback to simple "text" key
                    json.optString("text", "").trim()
                }
            } else {
                json.optString("partial", "").trim()
            }
        }.getOrElse {
            Log.w("VoskSTTEngine", "Could not parse recognizer output: $rawJson", it)
            ""
        }
    }
}
