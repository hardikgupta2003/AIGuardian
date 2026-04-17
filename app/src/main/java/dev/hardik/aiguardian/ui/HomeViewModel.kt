package dev.hardik.aiguardian.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.hardik.aiguardian.detection.ScamDetector
import dev.hardik.aiguardian.detection.ScamProtectionState
import dev.hardik.aiguardian.sos.SOSManager
import dev.hardik.aiguardian.stt.VoskSTTEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sosManager: SOSManager,
    private val scamDetector: ScamDetector,
    sttEngine: VoskSTTEngine
) : ViewModel() {

    val protectionState: StateFlow<ScamProtectionState> = combine(
        scamDetector.protectionState,
        sttEngine.isModelReady,
        sttEngine.isInitializing,
        sttEngine.errorMessage
    ) { state, modelReady, initializing, error ->
        state.copy(
            modelReady = modelReady,
            isPreparingModel = initializing,
            modelError = error
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ScamProtectionState()
    )

    fun triggerSOS() {
        sosManager.triggerSOS()
    }

    fun runDemoScamScan() {
        scamDetector.runDemoIrsScenario()
    }
}
