package dev.hardik.aiguardian

import android.app.Application

import dev.hardik.aiguardian.stt.VoskSTTEngine
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AIGuardianApp : Application() {
    
    @Inject
    lateinit var sttEngine: VoskSTTEngine

    override fun onCreate() {
        super.onCreate()
        // Initialize Vosk model (English by default)
        // Note: Model files must be in assets/model-en-us
        sttEngine.initModel("model-en-us") { success ->
            // Model loaded
        }
    }
}

