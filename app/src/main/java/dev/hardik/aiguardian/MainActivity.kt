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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

@OptIn(ExperimentalPermissionsApi::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val navController = rememberNavController()
            var isCallProtectionEnabled by remember { mutableStateOf(isCallProtectionEnabled()) }
            val permissionState = rememberMultiplePermissionsState(
                permissions = PermissionUtils.REQUIRED_PERMISSIONS.toList()
            )
            val callProtectionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) {
                isCallProtectionEnabled = isCallProtectionEnabled()
            }

            // Start service ONLY after permissions are granted AND we are in the foreground
            LaunchedEffect(permissionState.allPermissionsGranted) {
                if (permissionState.allPermissionsGranted) {
                    // Small delay to ensure Activity is fully in foreground and permission state is synced
                    kotlinx.coroutines.delay(500)
                    val serviceIntent = Intent(this@MainActivity, AIGuardianService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent)
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
                        AppNavigation(
                            navController = navController,
                            isCallProtectionEnabled = isCallProtectionEnabled,
                            onEnableCallProtection = {
                                requestCallProtection(callProtectionLauncher::launch)
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
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

private fun MainActivity.isCallProtectionEnabled(): Boolean {
    val telecomManager = getSystemService(TelecomManager::class.java)
    return telecomManager?.defaultDialerPackage == packageName
}

private fun MainActivity.requestCallProtection(launch: (Intent) -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = getSystemService(RoleManager::class.java)
        if (roleManager != null &&
            roleManager.isRoleAvailable(RoleManager.ROLE_DIALER) &&
            !roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
        ) {
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
        modifier = Modifier.fillMaxSize().padding(24.dp),
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
