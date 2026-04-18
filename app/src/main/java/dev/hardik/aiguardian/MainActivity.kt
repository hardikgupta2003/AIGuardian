package dev.hardik.aiguardian

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telecom.TelecomManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import dagger.hilt.android.AndroidEntryPoint
import dev.hardik.aiguardian.service.AIGuardianService
import dev.hardik.aiguardian.ui.AppNavigation
import dev.hardik.aiguardian.ui.theme.AIGuardianTheme
import dev.hardik.aiguardian.utils.PermissionUtils

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalPermissionsApi::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var webRTCManager: dev.hardik.aiguardian.webrtc.WebRTCManager

    @Inject
    lateinit var firebaseRepository: dev.hardik.aiguardian.data.remote.FirebaseRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            firebaseRepository.signInAnonymously()
        }

        setContent {
            val context = androidx.compose.ui.platform.LocalContext.current
            val navController = rememberNavController()
            var isCallProtectionEnabled by remember { mutableStateOf(isCallProtectionEnabled()) }
            var canDrawOverlays by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
            var userRole by remember { mutableStateOf(dev.hardik.aiguardian.utils.DeviceProfile.getRole(context)) }

            val permissionState = rememberMultiplePermissionsState(
                permissions = PermissionUtils.REQUIRED_PERMISSIONS.toList()
            )

            // Launcher for Dialer Role
            val callProtectionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) {
                isCallProtectionEnabled = isCallProtectionEnabled()
                android.util.Log.d("AIGuardianDebug", "DIALER_ROLE_RESULT: roleHeld=$isCallProtectionEnabled")
            }

            // Launcher for Overlay Permission (Still needed for alerts!)
            val overlayLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) {
                canDrawOverlays = Settings.canDrawOverlays(context)
                android.util.Log.d("AIGuardianDebug", "OVERLAY_PERMISSION_RESULT: granted=$canDrawOverlays")
            }

            // Sync status
            androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
                isCallProtectionEnabled = isCallProtectionEnabled()
                canDrawOverlays = Settings.canDrawOverlays(context)
                onPauseOrDispose { }
            }

            // AUTO-REQUEST DIALER ROLE: Trigger as soon as basic permissions are granted
            LaunchedEffect(permissionState.allPermissionsGranted, canDrawOverlays) {
                if (permissionState.allPermissionsGranted && canDrawOverlays && !isCallProtectionEnabled) {
                    android.util.Log.i("AIGuardianDebug", "AUTO_ACTION: Requesting Dialer Role for microphone persistence")
                    requestCallProtection(callProtectionLauncher::launch)
                }
            }

            // Start service once basic permissions are granted
            LaunchedEffect(permissionState.allPermissionsGranted) {
                if (permissionState.allPermissionsGranted) {
                    android.util.Log.d("AIGuardianDebug", "PERMISSION_CHECK: Basic permissions granted")
                    val serviceIntent = Intent(this@MainActivity, AIGuardianService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        try {
                            startForegroundService(serviceIntent)
                            android.util.Log.d("AIGuardianDebug", "SERVICE_START: Background protection started")
                        } catch (e: Exception) {
                            android.util.Log.e("AIGuardianDebug", "SERVICE_START_ERROR: ${e.message}")
                        }
                    } else {
                        startService(serviceIntent)
                    }
                }
            }

            LaunchedEffect(Unit) {
                permissionState.launchMultiplePermissionRequest()
            }

            AIGuardianTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (permissionState.allPermissionsGranted) {
                        if (userRole == dev.hardik.aiguardian.utils.DeviceProfile.Role.NONE) {
                            dev.hardik.aiguardian.ui.RoleSelectionScreen { role ->
                                dev.hardik.aiguardian.utils.DeviceProfile.setRole(context, role)
                                userRole = role
                            }
                        } else if (canDrawOverlays) {
                            AppNavigation(
                                navController = navController,
                                webRTCManager = webRTCManager,
                                isCallProtectionEnabled = isCallProtectionEnabled,
                                onEnableCallProtection = {
                                    requestCallProtection(callProtectionLauncher::launch)
                                },
                                userRole = userRole,
                                modifier = Modifier.padding(innerPadding)
                            )
                        } else {
                            OverlayPermissionRequiredScreen {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    android.net.Uri.parse("package:$packageName")
                                )
                                overlayLauncher.launch(intent)
                            }
                        }
                    } else {
                        PermissionRequiredScreen {
                            permissionState.launchMultiplePermissionRequest()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OverlayPermissionRequiredScreen(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Overlay Permission Required", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text("AI Guardian needs to display alerts over other apps to warn you during active scam calls.")
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRequest) {
            Text("Grant Overlay Permission")
        }
    }
}

private fun MainActivity.isCallProtectionEnabled(): Boolean {
    val telecomManager = getSystemService(TelecomManager::class.java)
    val defaultDialer = telecomManager?.defaultDialerPackage
    val isEnabled = defaultDialer == packageName
    android.util.Log.d("AIGuardianDebug", "ROLE_FORENSICS: MyPackage=$packageName | DefaultDialer=$defaultDialer | Match=$isEnabled")
    return isEnabled
}

private fun MainActivity.requestCallProtection(launch: (Intent) -> Unit) {
    android.util.Log.d("AIGuardianDebug", "ROLE_REQUEST: Initiating Dialer Role request")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = getSystemService(RoleManager::class.java)
        if (roleManager != null &&
            roleManager.isRoleAvailable(RoleManager.ROLE_DIALER) &&
            !roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
        ) {
            android.util.Log.d("AIGuardianDebug", "ROLE_REQUEST: Using RoleManager")
            launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER))
            return
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val telecomManager = getSystemService(TelecomManager::class.java)
        val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
            putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
        }
        if (telecomManager?.defaultDialerPackage != packageName &&
            intent.resolveActivity(packageManager) != null
        ) {
            launch(intent)
            return
        }
    }

    startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
}

@Composable
fun PermissionRequiredScreen(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Permissions Required", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text("AI Guardian needs these permissions to protect you from scams and handle emergencies.")
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRequest) {
            Text("Grant Permissions")
        }
    }
}
