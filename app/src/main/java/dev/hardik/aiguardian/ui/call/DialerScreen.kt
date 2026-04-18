package dev.hardik.aiguardian.ui.call

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.hardik.aiguardian.utils.DeviceProfile
import dev.hardik.aiguardian.webrtc.WebRTCManager

@Composable
fun DialerScreen(
    webRTCManager: WebRTCManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val myPin = remember { DeviceProfile.getOrGeneratePin(context) }
    var targetPin by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Your AI Guardian ID",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = myPin,
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Make a Call",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = targetPin,
                    onValueChange = { if (it.length <= 6) targetPin = it },
                    label = { Text("Enter 6-Digit ID") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (targetPin.length == 6) {
                            val callId = webRTCManager.initiateCall(myPin, targetPin)
                            val intent = Intent(context, IncomingCallActivity::class.java).apply {
                                putExtra("CALL_ID", callId)
                                putExtra("IS_CALLER", true)
                            }
                            context.startActivity(intent)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = targetPin.length == 6 && targetPin != myPin
                ) {
                    Text("Call Securely")
                }
            }
        }
    }
}
