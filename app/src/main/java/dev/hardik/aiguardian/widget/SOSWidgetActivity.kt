package dev.hardik.aiguardian.widget

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dagger.hilt.android.AndroidEntryPoint
import dev.hardik.aiguardian.sos.SOSManager
import dev.hardik.aiguardian.ui.theme.AIGuardianTheme
import kotlinx.coroutines.delay
import javax.inject.Inject

/**
 * Transparent full-screen Activity launched when the SOS widget is tapped.
 *
 * Displays the exact same 3-second long-press SOS experience:
 *  - Pulsing red glow ring
 *  - Animated progress arc that fills over 3 seconds
 *  - Countdown number inside the button
 *  - Haptic burst + SOS trigger on completion
 *  - ✕ button to cancel and dismiss
 */
@AndroidEntryPoint
class SOSWidgetActivity : ComponentActivity() {

    @Inject
    lateinit var sosManager: SOSManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make the window transparent and draw over home screen
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)

        setContent {
            AIGuardianTheme {
                SOSWidgetOverlay(
                    onSosTrigger = {
                        sosManager.triggerSOS()
                        Toast.makeText(this, "🚨 SOS Alert Sent!", Toast.LENGTH_LONG).show()
                        finish()
                    },
                    onDismiss = { finish() },
                    context = this
                )
            }
        }
    }
}

@Composable
fun SOSWidgetOverlay(
    onSosTrigger: () -> Unit,
    onDismiss: () -> Unit,
    context: Context
) {
    var isHolding by remember { mutableStateOf(false) }
    var holdProgress by remember { mutableFloatStateOf(0f) }
    var sosFired by remember { mutableStateOf(false) }

    // Drive the hold progress
    LaunchedEffect(isHolding) {
        if (isHolding) {
            sosFired = false
            holdProgress = 0f
            val startTime = System.currentTimeMillis()
            val holdDuration = 3000L

            while (isHolding && holdProgress < 1f) {
                val elapsed = System.currentTimeMillis() - startTime
                holdProgress = (elapsed.toFloat() / holdDuration).coerceIn(0f, 1f)
                if (holdProgress >= 1f && !sosFired) {
                    sosFired = true
                    triggerWidgetHaptic(context)
                    onSosTrigger()
                }
                delay(16)
            }
        } else {
            holdProgress = 0f
        }
    }

    // Pulsing glow
    val infiniteTransition = rememberInfiniteTransition(label = "widget_sos_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    val progressColor = when {
        holdProgress >= 1f -> Color(0xFF4ECCA3)
        holdProgress > 0f -> Color(0xFFFFD93D)
        else -> Color(0xFFFF5252)
    }

    // Dim background scrim
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Title
            Text(
                text = "AI Guardian SOS",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Hold the button to send an emergency alert",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 40.dp)
            )

            // SOS Button with pulse + progress ring
            Box(contentAlignment = Alignment.Center) {
                // Outer glow ring
                Box(
                    modifier = Modifier
                        .size(280.dp)
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
                            .size(260.dp)
                            .drawBehind {
                                drawArc(
                                    color = progressColor,
                                    startAngle = -90f,
                                    sweepAngle = 360f * holdProgress,
                                    useCenter = false,
                                    style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }
                    )
                }

                // Main SOS circle — long-press target
                Surface(
                    modifier = Modifier
                        .size(230.dp)
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
                    shadowElevation = 32.dp
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
                                fontSize = 60.sp,
                                fontWeight = FontWeight.Black
                            )
                            if (isHolding && holdProgress < 1f) {
                                Text(
                                    text = "${((1f - holdProgress) * 3).toInt() + 1}s",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = if (holdProgress >= 1f) "SOS Sent!" else "Hold for 3 seconds",
                color = if (holdProgress >= 1f) Color(0xFF4ECCA3) else Color.White.copy(alpha = 0.5f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Cancel button
            Surface(
                onClick = onDismiss,
                shape = RoundedCornerShape(50.dp),
                color = Color.White.copy(alpha = 0.10f),
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Cancel",
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Text(
                text = "Cancel",
                color = Color.White.copy(alpha = 0.35f),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

private fun triggerWidgetHaptic(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator.vibrate(
                VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(300)
            }
        }
    } catch (_: Exception) {}
}
