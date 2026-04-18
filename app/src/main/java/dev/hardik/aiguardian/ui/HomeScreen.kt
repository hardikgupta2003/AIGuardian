package dev.hardik.aiguardian.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.hardik.aiguardian.utils.DeviceProfile
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    onNavigateToMedicines: () -> Unit,
    onNavigateToScams: () -> Unit,
    onNavigateToDialer: () -> Unit,
    isCallProtectionEnabled: Boolean,
    onEnableCallProtection: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val protectionState by viewModel.protectionState.collectAsState()
    val context = LocalContext.current
    val devicePin = remember { DeviceProfile.getOrGeneratePin(context) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF0D1117), Color(0xFF161B22), Color(0xFF0D1117))
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "AI Guardian",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Protection Active",
                    color = Color(0xFF4ECCA3),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            // Device ID Badge
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = 0.08f)
            ) {
                Text(
                    text = "ID: $devicePin",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        LiveProtectionPanel(
            state = protectionState,
            isCallProtectionEnabled = isCallProtectionEnabled,
            onEnableCallProtection = onEnableCallProtection,
            onRunDemo = viewModel::runDemoScamScan
        )

        Spacer(modifier = Modifier.height(36.dp))

        // SOS Button with Long-Press
        SOSButton(
            onSosTrigger = {
                viewModel.triggerSOS()
                Toast.makeText(context, "🚨 SOS Alert Sent!", Toast.LENGTH_LONG).show()
            },
            context = context
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Action Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionCard(
                title = "Medicines",
                subtitle = "Reminders",
                icon = Icons.Default.Notifications,
                color = Color(0xFF45B7D1),
                onClick = onNavigateToMedicines,
                modifier = Modifier.weight(1f)
            )
            ActionCard(
                title = "Scam Log",
                subtitle = if (protectionState.score > 0) "Score: ${protectionState.score}" else "All clear",
                icon = Icons.Default.Warning,
                color = Color(0xFFF7B731),
                onClick = onNavigateToScams,
                modifier = Modifier.weight(1f)
            )
            ActionCard(
                title = "Secure Call",
                subtitle = "P2P VoIP",
                icon = Icons.Default.Call,
                color = Color(0xFF6C5CE7),
                onClick = onNavigateToDialer,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun SOSButton(
    onSosTrigger: () -> Unit,
    context: Context
) {
    var isHolding by remember { mutableStateOf(false) }
    var holdProgress by remember { mutableFloatStateOf(0f) }
    var sosFired by remember { mutableStateOf(false) }

    // Animate progress while holding
    LaunchedEffect(isHolding) {
        if (isHolding) {
            sosFired = false
            holdProgress = 0f
            val startTime = System.currentTimeMillis()
            val holdDuration = 3000L // 3 seconds

            while (isHolding && holdProgress < 1f) {
                val elapsed = System.currentTimeMillis() - startTime
                holdProgress = (elapsed.toFloat() / holdDuration).coerceIn(0f, 1f)
                if (holdProgress >= 1f && !sosFired) {
                    sosFired = true
                    // Haptic feedback
                    triggerHaptic(context)
                    onSosTrigger()
                }
                delay(16) // ~60fps
            }
        } else {
            // Reset on release
            holdProgress = 0f
        }
    }

    // Pulsing glow animation
    val infiniteTransition = rememberInfiniteTransition(label = "sos_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    val progressColor = when {
        holdProgress >= 1f -> Color(0xFF4ECCA3) // Green = fired
        holdProgress > 0f -> Color(0xFFFFD93D)   // Yellow = holding
        else -> Color(0xFFFF5252)                  // Red = idle
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            // Outer glow ring
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .scale(pulseScale)
                    .drawBehind {
                        drawCircle(
                            color = Color(0xFFFF5252).copy(alpha = glowAlpha),
                            radius = size.minDimension / 2
                        )
                    }
            )

            // Progress arc
            if (holdProgress > 0f) {
                Box(
                    modifier = Modifier
                        .size(248.dp)
                        .drawBehind {
                            drawArc(
                                color = progressColor,
                                startAngle = -90f,
                                sweepAngle = 360f * holdProgress,
                                useCenter = false,
                                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                )
            }

            // Main SOS circle
            Surface(
                modifier = Modifier
                    .size(220.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                isHolding = true
                                tryAwaitRelease()
                                isHolding = false
                            }
                        )
                    },
                shape = CircleShape,
                color = Color.Transparent,
                shadowElevation = 24.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.radialGradient(
                                colors = if (holdProgress >= 1f)
                                    listOf(Color(0xFF4ECCA3), Color(0xFF2D8F6F))
                                else if (isHolding)
                                    listOf(Color(0xFFFF7043), Color(0xFFD32F2F))
                                else
                                    listOf(Color(0xFFFF5252), Color(0xFFB71C1C))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (holdProgress >= 1f) "✓" else "SOS",
                            color = Color.White,
                            fontSize = if (holdProgress >= 1f) 56.sp else 56.sp,
                            fontWeight = FontWeight.Black
                        )
                        if (isHolding && holdProgress < 1f) {
                            Text(
                                text = "${((1f - holdProgress) * 3).toInt() + 1}s",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (holdProgress >= 1f) "SOS Alert Sent!" else "Hold for 3 seconds",
            color = if (holdProgress >= 1f) Color(0xFF4ECCA3) else Color.White.copy(alpha = 0.5f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun triggerHaptic(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            val vibrator = vibratorManager.defaultVibrator
            vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(300)
            }
        }
    } catch (_: Exception) {}
}

@Composable
private fun LiveProtectionPanel(
    state: dev.hardik.aiguardian.detection.ScamProtectionState,
    isCallProtectionEnabled: Boolean,
    onEnableCallProtection: () -> Unit,
    onRunDemo: () -> Unit
) {
    val threatColor = when (state.level) {
        dev.hardik.aiguardian.detection.ThreatLevel.SEVERE -> Color(0xFFFF5252)
        dev.hardik.aiguardian.detection.ThreatLevel.HIGH -> Color(0xFFFFB347)
        dev.hardik.aiguardian.detection.ThreatLevel.CAUTION -> Color(0xFFF7D26A)
        dev.hardik.aiguardian.detection.ThreatLevel.SAFE -> Color(0xFF4ECCA3)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.04f),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Threat indicator dot
                Surface(
                    shape = CircleShape,
                    color = threatColor,
                    modifier = Modifier.size(10.dp)
                ) {}
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Call Shield",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = when {
                            state.modelError != null -> state.modelError!!
                            state.modelReady -> "AI model ready"
                            state.isPreparingModel -> "Preparing model…"
                            else -> "Initializing…"
                        },
                        color = if (state.modelError != null) Color(0xFFFFB3B3) else Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                }
                // Score badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = threatColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "${state.score}/100",
                        color = threatColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Status row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Status: ${state.level.name.lowercase().replaceFirstChar { it.uppercase() }}",
                    color = threatColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (isCallProtectionEnabled) "🛡 Protected" else "⚠ Unprotected",
                    color = if (isCallProtectionEnabled) Color(0xFF9BE7C4) else Color(0xFFFFD9A8),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Latest transcript
            if (state.lastTranscript != "Waiting for speech…") {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.04f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "\"${state.lastTranscript}\"",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(12.dp),
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }

            if (state.isMonitoring) {
                Surface(
                    color = Color(0xFF4ECCA3).copy(alpha = 0.1f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "💡 Turn on Speakerphone so AI can hear the caller",
                        color = Color(0xFF4ECCA3),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(10.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (!isCallProtectionEnabled) {
                Button(
                    onClick = onEnableCallProtection,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4ECCA3)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Enable Call Protection", color = Color(0xFF0D1117), fontWeight = FontWeight.Bold)
                }
            }

            if (state.reasons.isNotEmpty()) {
                Text(
                    text = state.reasons.joinToString(" • "),
                    color = Color(0xFFFFD9A8),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }

            if (state.severeActionTaken) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFFD1D1), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Auto-mute triggered for severe threat",
                        color = Color(0xFFFFD1D1),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            OutlinedButton(
                onClick = onRunDemo,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF7B731)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("▶ Run Scam Demo", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(130.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.04f),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = color.copy(alpha = 0.15f),
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.padding(8.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 11.sp
                )
            }
        }
    }
}
