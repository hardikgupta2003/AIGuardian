package dev.hardik.aiguardian.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF1A1A2E), Color(0xFF16213E))
                )
            )
            .padding(24.dp),
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
                    text = "You are protected",
                    color = Color(0xFF4ECCA3),
                    fontSize = 14.sp
                )
            }
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.1f),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        LiveProtectionPanel(
            state = protectionState,
            isCallProtectionEnabled = isCallProtectionEnabled,
            onEnableCallProtection = onEnableCallProtection,
            onRunDemo = viewModel::runDemoScamScan
        )

        Spacer(modifier = Modifier.weight(0.35f))

        // Large SOS Button
        Surface(
            modifier = Modifier
                .size(240.dp)
                .clickable { viewModel.triggerSOS() },
            shape = CircleShape,
            color = Color.Transparent,
            shadowElevation = 16.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFFFF5252), Color(0xFFD32F2F))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "SOS",
                    color = Color.White,
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
        
        Text(
            text = "Press for 3 seconds",
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 16.dp)
        )

        Spacer(modifier = Modifier.weight(0.4f))

        // Action Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ActionCard(
                title = "Medicines",
                subtitle = "3 Pending",
                icon = Icons.Default.Notifications,
                color = Color(0xFF45B7D1),
                onClick = onNavigateToMedicines,
                modifier = Modifier.weight(1f)
            )
            ActionCard(
                title = "Scam Alerts",
                subtitle = "No threats",
                icon = Icons.Default.Warning,
                color = Color(0xFFF7B731),
                onClick = onNavigateToScams,
                modifier = Modifier.weight(1f)
            )
            ActionCard(
                title = "Secure Call",
                subtitle = "P2P VoIP",
                icon = Icons.Default.Notifications,
                color = Color(0xFF45B7D1),
                onClick = onNavigateToDialer,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun LiveProtectionPanel(
    state: dev.hardik.aiguardian.detection.ScamProtectionState,
    isCallProtectionEnabled: Boolean,
    onEnableCallProtection: () -> Unit,
    onRunDemo: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color.White.copy(alpha = 0.06f),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (state.isMonitoring) Icons.Default.Notifications else Icons.Default.Warning,
                    contentDescription = null,
                    tint = when (state.level) {
                        dev.hardik.aiguardian.detection.ThreatLevel.SEVERE -> Color(0xFFFF7A6A)
                        dev.hardik.aiguardian.detection.ThreatLevel.HIGH -> Color(0xFFFFB347)
                        dev.hardik.aiguardian.detection.ThreatLevel.CAUTION -> Color(0xFFF7D26A)
                        dev.hardik.aiguardian.detection.ThreatLevel.SAFE -> Color(0xFF4ECCA3)
                    }
                )
                Column {
                    Text(
                        text = "Local Call Shield",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    Text(
                        text = when {
                            state.modelError != null -> state.modelError!!
                            state.modelReady -> "Offline speech model ready"
                            state.isPreparingModel -> "Preparing speech model..."
                            else -> "Waiting for speech model"
                        },
                        color = if (state.modelError != null) Color(0xFFFFB3B3) else Color.White.copy(alpha = 0.72f),
                        fontSize = 13.sp
                    )
                }
            }

            Text(
                text = "Status: ${state.level.name.lowercase().replaceFirstChar { it.uppercase() }}  •  Score ${state.score}/100",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Caller: ${state.activePhoneNumber}",
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 14.sp
            )
            Text(
                text = if (isCallProtectionEnabled) "Call protection role: enabled" else "Call protection role: not enabled",
                color = if (isCallProtectionEnabled) Color(0xFF9BE7C4) else Color(0xFFFFD9A8),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Latest transcript: ${state.lastTranscript}",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
            
            if (state.isMonitoring) {
                Surface(
                    color = Color(0xFF4ECCA3).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "💡 Tip: Turn on Speakerphone so AI can hear the caller.",
                        color = Color(0xFF4ECCA3),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
            if (!isCallProtectionEnabled) {
                Button(
                    onClick = onEnableCallProtection,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4ECCA3))
                ) {
                    Text("Enable Call Protection", color = Color(0xFF102033), fontWeight = FontWeight.Bold)
                }
            }
            if (state.reasons.isNotEmpty()) {
                Text(
                    text = state.reasons.joinToString(separator = " • "),
                    color = Color(0xFFFFD9A8),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
            if (state.severeActionTaken) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFFD1D1))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Automatic mute requested for severe threat",
                        color = Color(0xFFFFD1D1),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Button(
                onClick = onRunDemo,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF7B731))
            ) {
                Text("Run IRS Scam Demo", color = Color(0xFF1A1A2E), fontWeight = FontWeight.Bold)
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
            .height(140.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.05f),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
        }
    }
}
