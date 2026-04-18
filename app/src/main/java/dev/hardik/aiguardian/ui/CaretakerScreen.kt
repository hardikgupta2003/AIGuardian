package dev.hardik.aiguardian.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.hardik.aiguardian.utils.DeviceProfile
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaretakerScreen(
    viewModel: CaretakerViewModel = hiltViewModel()
) {
    val pairedElderPin by viewModel.pairedElderPin.collectAsState()
    val sosEvents by viewModel.sosEvents.collectAsState()
    val scamEvents by viewModel.scamEvents.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Caretaker Dashboard", color = Color.White, fontWeight = FontWeight.Bold) },
                actions = {
                    if (pairedElderPin != null) {
                        IconButton(onClick = { viewModel.logout() }) {
                            Icon(Icons.Default.ExitToApp, contentDescription = "Logout", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent,
        modifier = Modifier.background(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF0D1117), Color(0xFF161B22), Color(0xFF0D1117))
            )
        )
    ) { innerPadding ->
        if (pairedElderPin == null) {
            PairWithElderContent(onPair = { viewModel.pairWithElder(it) })
        } else {
            CaretakerDashboardContent(
                innerPadding = innerPadding,
                elderPin = pairedElderPin!!,
                sosEvents = sosEvents,
                scamEvents = scamEvents
            )
        }
    }
}

@Composable
fun PairWithElderContent(onPair: (String) -> Unit) {
    var pinInput by remember { mutableStateOf("") }
    val context = LocalContext.current
    val caretakerId = remember { DeviceProfile.getOrGeneratePin(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF45B7D1).copy(alpha = 0.15f),
            modifier = Modifier.size(72.dp)
        ) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                tint = Color(0xFF45B7D1),
                modifier = Modifier.padding(16.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Link to Your Loved One",
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Enter the 6-digit Device ID shown on their phone to start monitoring.",
            color = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            fontSize = 14.sp
        )

        OutlinedTextField(
            value = pinInput,
            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pinInput = it },
            label = { Text("Elder Device ID", color = Color.White.copy(alpha = 0.4f)) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF45B7D1),
                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                cursorColor = Color(0xFF45B7D1)
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { onPair(pinInput) },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            enabled = pinInput.length == 6,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF45B7D1),
                contentColor = Color.Black
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Link to Elder", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(32.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.White.copy(alpha = 0.04f)
        ) {
            Text(
                text = "Your Caretaker ID: $caretakerId",
                color = Color.White.copy(alpha = 0.35f),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
fun CaretakerDashboardContent(
    innerPadding: PaddingValues,
    elderPin: String,
    sosEvents: List<Map<String, Any>>,
    scamEvents: List<Map<String, Any>>
) {
    val uriHandler = LocalUriHandler.current

    Column(modifier = Modifier
        .padding(innerPadding)
        .padding(16.dp)) {
        // Status card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Color.White.copy(alpha = 0.06f)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = Color(0xFF4ECCA3),
                    modifier = Modifier.size(10.dp)
                ) {}
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Monitoring Active",
                        color = Color(0xFF4ECCA3),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Elder PIN: $elderPin",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Recent Alerts",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (sosEvents.isEmpty() && scamEvents.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 64.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "✅", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "All clear! No alerts yet.",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 15.sp
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(sosEvents) { event ->
                    AlertItem(
                        title = "EMERGENCY SOS",
                        subtitle = "Elder pressed the SOS button!",
                        timestamp = event["timestamp"] as? Long ?: 0L,
                        location = event["location"] as? String,
                        icon = Icons.Default.LocationOn,
                        color = Color(0xFFFF5252),
                        onLocationClick = {
                            (event["location"] as? String)?.let { if (it.startsWith("http")) uriHandler.openUri(it) }
                        }
                    )
                }

                items(scamEvents) { event ->
                    AlertItem(
                        title = "SCAM DETECTED",
                        subtitle = "Blocked call from ${event["phoneNumber"]}",
                        timestamp = event["timestamp"] as? Long ?: 0L,
                        location = event["location"] as? String,
                        icon = Icons.Default.Warning,
                        color = Color(0xFFFFB300),
                        onLocationClick = {
                            (event["location"] as? String)?.let { if (it.startsWith("http")) uriHandler.openUri(it) }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AlertItem(
    title: String,
    subtitle: String,
    timestamp: Long,
    location: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onLocationClick: () -> Unit
) {
    val date = SimpleDateFormat("hh:mm a, dd MMM", Locale.getDefault()).format(Date(timestamp))

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.06f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = color.copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.padding(8.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(text = subtitle, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                Text(text = date, color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
            }

            if (location != null && location.startsWith("http")) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onLocationClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.08f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View on Map", color = Color.White, fontSize = 13.sp)
                }
            }
        }
    }
}
