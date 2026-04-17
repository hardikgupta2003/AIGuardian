package dev.hardik.aiguardian.detection

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallInterventionManager @Inject constructor() {

    private var muteAction: (() -> Boolean)? = null
    private var endCallAction: (() -> Boolean)? = null

    private val _callMuted = MutableStateFlow(false)
    val callMuted: StateFlow<Boolean> = _callMuted.asStateFlow()

    fun registerActions(
        onMute: () -> Boolean,
        onEndCall: () -> Boolean
    ) {
        muteAction = onMute
        endCallAction = onEndCall
        _callMuted.value = false
    }

    fun clearActions() {
        muteAction = null
        endCallAction = null
        _callMuted.value = false
    }

    fun muteCaller(): Boolean {
        val muted = muteAction?.invoke() == true
        if (muted) {
            _callMuted.value = true
        }
        return muted
    }

    fun endCall(): Boolean {
        return endCallAction?.invoke() == true
    }
}
