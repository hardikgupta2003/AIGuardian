package dev.hardik.aiguardian.detection

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.hardik.aiguardian.MainActivity
import dev.hardik.aiguardian.ui.theme.AIGuardianTheme
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OverlayManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    fun showScamAlert(
        state: ScamProtectionState,
        onMute: () -> Boolean,
        onHangUp: () -> Boolean,
        onDismiss: () -> Unit
    ) {
        if (overlayView != null) return

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
            else 
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            y = 100
        }

        val composeView = ComposeView(context).apply {
            setContent {
                AIGuardianTheme {
                    ScamAlertUI(
                        state = state,
                        onOpenDashboard = { openDashboard() },
                        onMute = onMute,
                        onHangUp = onHangUp,
                        onDismiss = {
                            removeOverlay()
                            onDismiss()
                        }
                    )
                }
            }
        }

        // Custom lifecycle for ComposeView in WindowManager
        val lifecycleOwner = MyLifecycleOwner()
        lifecycleOwner.performRestore(null)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        
        composeView.setViewTreeLifecycleOwner(lifecycleOwner)
        composeView.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        composeView.setViewTreeViewModelStoreOwner(object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = ViewModelStore()
        })

        overlayView = composeView
        windowManager?.addView(overlayView, params)
    }

    private fun openDashboard() {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        context.startActivity(intent)
    }

    private fun removeOverlay() {
        overlayView?.let {
            windowManager?.removeView(it)
            overlayView = null
        }
    }

    @Composable
    private fun ScamAlertUI(
        state: ScamProtectionState,
        onOpenDashboard: () -> Unit,
        onMute: () -> Boolean,
        onHangUp: () -> Boolean,
        onDismiss: () -> Unit
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.Transparent
        ) {
            Column(
                modifier = Modifier
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFFFF6B57), Color(0xFFB91C1C))
                        )
                    )
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.height(42.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Possible scam call detected",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "🚨 SCAM ALERT",
                    color = Color.Transparent,
                    fontSize = 1.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(0.dp))
                Text(
                    text = "Do not share OTP, card, bank, or UPI details.",
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 19.sp,
                    lineHeight = 26.sp,
                    textAlign = TextAlign.Center
                )
                if (state.reasons.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = state.reasons.joinToString(separator = "\n") { "• $it" },
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { onMute() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFB91C1C))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Mute", color = Color(0xFFB91C1C), fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { onHangUp() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B0B0B)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("End", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onOpenDashboard,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open Protection Dashboard", color = Color.White)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Latest heard: \"${state.lastTranscript}\"",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.16f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("I understand", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Helper class for Lifecycle inside WindowManager
    internal class MyLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

        fun handleLifecycleEvent(event: Lifecycle.Event) = lifecycleRegistry.handleLifecycleEvent(event)
        fun performRestore(savedState: android.os.Bundle?) = savedStateRegistryController.performRestore(savedState)
    }
}
