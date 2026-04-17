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
        android.util.Log.i("AIGuardianDebug", "APPLICATION_START: AIGuardianApp created")
        
        // Initialize Vosk model (English by default)
        sttEngine.initModel("model-en-us") { success ->
            android.util.Log.i("AIGuardianDebug", "STT_INIT: Model initialization result: $success")
        }
    }
}

