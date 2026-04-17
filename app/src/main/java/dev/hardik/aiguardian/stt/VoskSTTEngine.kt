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

    private val _transcriptionFlow = MutableSharedFlow<TranscriptSegment>(extraBufferCapacity = 16)
    val transcriptionFlow = _transcriptionFlow.asSharedFlow()

    fun initModel(modelPath: String = "model-en-us", onComplete: (Boolean) -> Unit) {
        StorageService.unpack(
            context,
            modelPath,
            "model",
            { model: Model ->
                this.model = model
                _isModelReady.value = true
                onComplete(true)
            },
            { exception: IOException ->
                Log.e("VoskSTTEngine", "Failed to unpack model", exception)
                _isModelReady.value = false
                onComplete(false)
            }
        )
    }

    fun startRecognition() {
        model?.let {
            recognizer = Recognizer(it, 16000.0f)
            lastPartial = ""
            Log.d("VoskSTTEngine", "Recognizer started")
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
    }

    private suspend fun emitTranscript(rawJson: String, isFinal: Boolean) {
        val text = extractText(rawJson, isFinal)
        if (text.isBlank()) return
        if (!isFinal && text == lastPartial) return

        if (isFinal) {
            lastPartial = ""
        } else {
            lastPartial = text
        }

        Log.d("VoskSTTEngine", if (isFinal) "Final transcript: $text" else "Partial transcript: $text")
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
