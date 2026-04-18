package dev.hardik.aiguardian.stt

import android.content.Context
import android.util.Log
import dev.hardik.aiguardian.detection.TranscriptSegment
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
    private val context: Context
) {
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var lastPartial = ""

    private val _isModelReady = MutableStateFlow(false)
    val isModelReady: StateFlow<Boolean> = _isModelReady.asStateFlow()

    private val _isInitializing = MutableStateFlow(false)
    val isInitializing: StateFlow<Boolean> = _isInitializing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _transcriptionFlow = MutableSharedFlow<TranscriptSegment>(extraBufferCapacity = 16)
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
        model?.let {
            recognizer = Recognizer(it, 16000.0f).apply {
                setMaxAlternatives(3)
                setWords(true)
            }
            lastPartial = ""
            android.util.Log.d("AIGuardianDebug", "STT: Recognizer started (maxAlt=3, words=true)")
        }
    }

    suspend fun processAudioChunk(data: ByteArray, length: Int) {
        recognizer?.let {
            if (it.acceptWaveForm(data, length)) {
                emitTranscript(it.result, isFinal = true)
            } else {
                emitTranscript(it.partialResult, isFinal = false)
            }
        }
    }

    fun stopRecognition() {
        recognizer?.finalResult?.let { finalResult ->
            val text = extractText(finalResult, isFinal = true)
            if (text.isNotBlank()) {
                _transcriptionFlow.tryEmit(TranscriptSegment(text = text, isFinal = true))
            }
        }
        recognizer?.close()
        recognizer = null
        lastPartial = ""
        android.util.Log.d("AIGuardianDebug", "STT: Recognizer stopped")
    }

    private suspend fun emitTranscript(rawJson: String, isFinal: Boolean) {
        // RADICAL FORENSIC LOG: Print exactly what Vosk returned before any parsing
        android.util.Log.v("AIGuardianDebug", "STT_RAW: $rawJson")

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

        _transcriptionFlow.emit(TranscriptSegment(text = text, isFinal = isFinal))
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
