package dev.hardik.aiguardian.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hardik.aiguardian.data.remote.FirebaseRepository
import dev.hardik.aiguardian.utils.DeviceProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CaretakerViewModel @Inject constructor(
    application: Application,
    private val firebaseRepository: FirebaseRepository
) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val caretakerPin = DeviceProfile.getOrGeneratePin(context)

    private val _pairedElderPin = MutableStateFlow<String?>(null)
    val pairedElderPin: StateFlow<String?> = _pairedElderPin.asStateFlow()

    private val _sosEvents = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val sosEvents: StateFlow<List<Map<String, Any>>> = _sosEvents.asStateFlow()

    private val _scamEvents = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val scamEvents: StateFlow<List<Map<String, Any>>> = _scamEvents.asStateFlow()

    init {
        val prefs = context.getSharedPreferences("CaretakerPrefs", 0)
        val savedPin = prefs.getString("PAIRED_ELDER_PIN", null)
        if (savedPin != null) {
            _pairedElderPin.value = savedPin
            startObserving(savedPin)
        }
    }

    fun pairWithElder(elderPin: String) {
        viewModelScope.launch {
            val token = firebaseRepository.getFCMToken() ?: return@launch
            firebaseRepository.pairWithElder(elderPin, caretakerPin, token)
            
            _pairedElderPin.value = elderPin
            val prefs = context.getSharedPreferences("CaretakerPrefs", 0)
            prefs.edit().putString("PAIRED_ELDER_PIN", elderPin).apply()
            
            startObserving(elderPin)
        }
    }

    private fun startObserving(elderPin: String) {
        firebaseRepository.observeSosEvents(elderPin) { events ->
            _sosEvents.value = events
        }
        firebaseRepository.observeScamEvents(elderPin) { events ->
            _scamEvents.value = events
        }
    }

    fun logout() {
        val prefs = context.getSharedPreferences("CaretakerPrefs", 0)
        prefs.edit().remove("PAIRED_ELDER_PIN").apply()
        _pairedElderPin.value = null
        _sosEvents.value = emptyList()
        _scamEvents.value = emptyList()
    }
}
