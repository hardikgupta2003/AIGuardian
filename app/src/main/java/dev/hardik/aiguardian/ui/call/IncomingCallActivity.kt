package dev.hardik.aiguardian.ui.call

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dagger.hilt.android.AndroidEntryPoint
import dev.hardik.aiguardian.detection.ScamDetector
import dev.hardik.aiguardian.stt.VoskSTTEngine
import dev.hardik.aiguardian.webrtc.WebRTCManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.lifecycleScope
import javax.inject.Inject

@AndroidEntryPoint
class IncomingCallActivity : ComponentActivity() {

    @Inject
    lateinit var webRTCManager: WebRTCManager

    @Inject
    lateinit var sttEngine: VoskSTTEngine

    @Inject
    lateinit var scamDetector: ScamDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val callId = intent.getStringExtra("CALL_ID") ?: run {
            finish()
            return
        }
        val isCaller = intent.getBooleanExtra("IS_CALLER", false)
        val myPin = dev.hardik.aiguardian.utils.DeviceProfile.getOrGeneratePin(this)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF6200EE),
                    secondary = Color(0xFF03DAC6),
                    background = Color(0xFF121212)
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CallScreen(
                        callId = callId,
                        isCaller = isCaller,
                        myPin = myPin,
                        onAnswer = {
                            sttEngine.initModel { ready ->
                                if (ready) {
                                    sttEngine.startRecognition()
                                    scamDetector.startMonitoring()
                                    webRTCManager.startCallAudio(callId, isCaller) { audioData ->
                                        lifecycleScope.launch {
                                            sttEngine.processAudioChunk(audioData, audioData.size)
                                        }
                                    }
                                }
                            }
                        },
                        onHangUp = {
                            webRTCManager.endCall(myPin, callId)
                            sttEngine.stopRecognition()
                            scamDetector.stopMonitoring()
                            finish()
                        }
                    )
                }
            }
        }
    }

    @Composable
    fun CallScreen(
        callId: String,
        isCaller: Boolean,
        myPin: String,
        onAnswer: @DisallowComposableCalls () -> Unit,
        onHangUp: @DisallowComposableCalls () -> Unit
    ) {
        var isCallActive by remember { mutableStateOf(false) }
        var transcriptions by remember { mutableStateOf(listOf<String>()) }
        var scamThreatLevel by remember { mutableStateOf("SAFE") }
        var showScamAlert by remember { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope()

        LaunchedEffect(scamThreatLevel) {
            if (scamThreatLevel == "CAUTION" || scamThreatLevel == "HIGH" || scamThreatLevel == "SEVERE") {
                if (!showScamAlert) {
                    showScamAlert = true
                    try {
                        val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                        coroutineScope.launch {
                            for (i in 1..25) { // Beeps 5 times per second for 5 seconds
                                toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_NETWORK_LITE, 100)
                                delay(200)
                            }
                            toneGen.release()
                        }
                    } catch (e: Exception) {}
                    delay(5000)
                    onHangUp()
                }
            }
        }

        LaunchedEffect(Unit) {
            sttEngine.transcriptionFlow.collect { segment ->
                if (segment.isFinal) {
                    transcriptions = (transcriptions + segment.text).takeLast(10)
                    webRTCManager.sendLiveTranscription(callId, segment.text)
                }
            }
        }

        LaunchedEffect(Unit) {
            scamDetector.protectionState.collectLatest { state ->
                scamThreatLevel = state.level.name
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF1A237E), Color(0xFF121212))
                    )
                )
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "AI GUARD",
                    color = Color.Cyan,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (isCallActive) "CALL IN PROGRESS" else "INCOMING AI CALL",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Light
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "ID: $callId",
                    color = Color.Gray,
                    fontSize = 12.sp
                )

                if (showScamAlert) {
                    Spacer(modifier = Modifier.height(48.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFAA0000)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = "Warning", tint = Color.White, modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("SCAM DETECTED!", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Terminating call in 5s...", color = Color.White, fontSize = 16.sp)
                        }
                    }
                } else if (isCallActive) {
                    Spacer(modifier = Modifier.height(48.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Real-time Analysis", color = Color.Gray, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                Column {
                                    transcriptions.forEach { text ->
                                        Text(text = text, color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(bottom = 4.dp))
                                    }
                                }
                            }
                            Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Threat Status: ", color = Color.Gray)
                                Text(
                                    text = scamThreatLevel,
                                    color = when(scamThreatLevel) {
                                        "HIGH", "SEVERE" -> Color.Red
                                        "MEDIUM" -> Color.Yellow
                                        else -> Color.Green
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(64.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    if (!isCallActive) {
                        FloatingActionButton(
                            onClick = {
                                isCallActive = true
                                onAnswer()
                            },
                            containerColor = Color.Green,
                            shape = CircleShape,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Icon(Icons.Default.Call, contentDescription = "Answer", tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                    }

                    FloatingActionButton(
                        onClick = onHangUp,
                        containerColor = Color.Red,
                        shape = CircleShape,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Hang up", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                }
            }
        }
    }
}
