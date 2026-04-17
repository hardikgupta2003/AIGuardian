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

    fun initModel(modelPath: String = "model-en-us", onComplete: (Boolean) -> Unit) {
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
            recognizer = Recognizer(it, 16000.0f)
            lastPartial = ""
            android.util.Log.d("AIGuardianDebug", "STT: Recognizer started")
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
        val text = extractText(rawJson, isFinal)
        if (text.isBlank()) return
        if (!isFinal && text == lastPartial) return

        if (isFinal) {
            lastPartial = ""
            android.util.Log.i("AIGuardianDebug", "STT_RESULT: >>> FINAL TRANSCRIPT: $text")
        } else {
            lastPartial = text
            android.util.Log.v("AIGuardianDebug", "STT_RESULT: Partial: $text")
        }

        _transcriptionFlow.emit(TranscriptSegment(text = text, isFinal = isFinal))
    }

    private fun extractText(rawJson: String, isFinal: Boolean): String {
        return runCatching {
            val key = if (isFinal) "text" else "partial"
            JSONObject(rawJson).optString(key).trim()
        }.getOrElse {
            Log.w("VoskSTTEngine", "Could not parse recognizer output: $rawJson", it)
            ""
        }
    }
}
