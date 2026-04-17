package dev.hardik.aiguardian.detection

import dev.hardik.aiguardian.data.model.ScamEvent
import dev.hardik.aiguardian.data.repository.SafetyRepository
import dev.hardik.aiguardian.stt.VoskSTTEngine
import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Singleton
class ScamDetector @Inject constructor(
    private val sttEngine: VoskSTTEngine,
    private val overlayManager: OverlayManager,
    private val repository: SafetyRepository,
    private val callInterventionManager: CallInterventionManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val analyzer = ScamRiskAnalyzer()
    private val rollingSegments = ArrayDeque<TranscriptSegment>()

    private var observationJob: Job? = null
    private var activePhoneNumber = "Incoming Call"
    private var lastLoggedAtMs = 0L
    private var severeActionTaken = false

    private val _protectionState = MutableStateFlow(ScamProtectionState())
    val protectionState: StateFlow<ScamProtectionState> = _protectionState.asStateFlow()

    fun startMonitoring() {
        stopMonitoring()
        rollingSegments.clear()
        severeActionTaken = false
        _protectionState.value = _protectionState.value.copy(
            isMonitoring = true,
            modelReady = sttEngine.isModelReady.value,
            activePhoneNumber = activePhoneNumber,
            lastTranscript = "Listening for scam language…",
            score = 0,
            level = ThreatLevel.SAFE,
            reasons = emptyList(),
            severeActionTaken = false,
            lastUpdateMs = System.currentTimeMillis()
        )

        observationJob = scope.launch {
            sttEngine.transcriptionFlow.collect { segment ->
                analyzeTranscription(segment)
            }
        }
    }

    fun updateActivePhoneNumber(phoneNumber: String?) {
        activePhoneNumber = phoneNumber?.ifBlank { "Incoming Call" } ?: "Incoming Call"
        _protectionState.value = _protectionState.value.copy(activePhoneNumber = activePhoneNumber)
    }

    fun runDemoIrsScenario() {
        val demoText = buildString {
            append("Hello this is officer Martin from the IRS. ")
            append("There is a warrant on your account and you must stay on the line. ")
            append("Verify your account number and OTP immediately to avoid arrest.")
        }
        analyzeTranscription(TranscriptSegment(text = demoText, isFinal = true))
    }

    private fun analyzeTranscription(segment: TranscriptSegment) {
        rollingSegments.addLast(segment)
        pruneRollingWindow(segment.timestampMs)

        val windowText = rollingSegments.joinToString(" ") { it.text }.trim()
        val analysis = analyzer.analyze(windowText)

        _protectionState.value = ScamProtectionState(
            isMonitoring = true,
            modelReady = sttEngine.isModelReady.value,
            activePhoneNumber = activePhoneNumber,
            lastTranscript = segment.text,
            score = analysis.score,
            level = analysis.level,
            reasons = analysis.reasons,
            severeActionTaken = severeActionTaken,
            lastUpdateMs = segment.timestampMs
        )

        if (analysis.level == ThreatLevel.SEVERE || analysis.level == ThreatLevel.HIGH) {
            maybePersistDetection(analysis, segment.timestampMs)
        }

        if (analysis.level == ThreatLevel.SEVERE && !severeActionTaken) {
            severeActionTaken = callInterventionManager.muteCaller()
            _protectionState.value = _protectionState.value.copy(severeActionTaken = severeActionTaken)
            scope.launch {
                overlayManager.showScamAlert(
                    state = _protectionState.value,
                    onMute = { callInterventionManager.muteCaller() },
                    onHangUp = { callInterventionManager.endCall() },
                    onDismiss = {}
                )
            }
        }
    }

    private fun pruneRollingWindow(nowMs: Long) {
        while (rollingSegments.isNotEmpty() && nowMs - rollingSegments.first().timestampMs > 3_000) {
            rollingSegments.removeFirst()
        }
    }

    private fun maybePersistDetection(
        analysis: ScamAnalysis,
        timestampMs: Long
    ) {
        if (timestampMs - lastLoggedAtMs < 5_000) return
        lastLoggedAtMs = timestampMs
        scope.launch {
            repository.logScam(
                ScamEvent(
                    phoneNumber = activePhoneNumber,
                    transcription = analysis.windowText
                )
            )
        }
    }

    fun stopMonitoring() {
        observationJob?.cancel()
        observationJob = null
        rollingSegments.clear()
        severeActionTaken = false
        _protectionState.value = _protectionState.value.copy(
            isMonitoring = false,
            score = 0,
            level = ThreatLevel.SAFE,
            reasons = emptyList(),
            severeActionTaken = false,
            lastTranscript = "Protection idle",
            lastUpdateMs = System.currentTimeMillis()
        )
    }
}
