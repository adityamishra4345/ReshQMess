@file:OptIn(com.google.accompanist.permissions.ExperimentalPermissionsApi::class)
package com.example.reshqmess


import androidx.compose.ui.graphics.graphicsLayer
import android.Manifest
import androidx.compose.animation.core.*
import androidx.compose.ui.geometry.Offset
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.reshqmess.mesh.MeshManager
import com.example.reshqmess.model.SosPayload
import com.example.reshqmess.ui.theme.ReshQMessTheme
import com.example.reshqmess.viewmodel.DisasterViewModel
import com.google.accompanist.permissions.*
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.util.concurrent.Executors

import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush

// --- 🏛️ PROFESSIONAL COLOR SYSTEM ---

val ProSurface = Color(0xFF0A0A0A)   // DARK BACKGROUND
val ProCard = Color(0xFF121212)      // DARK CARD
val ProTextMain = Color(0xFFFFFFFF)  // WHITE TEXT
val ProTextSub = Color(0xFFAAAAAA)   // LIGHT GREY
val ProPrimary = Color(0xFF2563EB)


val ProAlert = Color(0xFFDC2626)
val ProSuccess = Color(0xFF059669)



class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<DisasterViewModel>()
    private lateinit var meshManager: MeshManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val myDeviceName = "Unit-${(100..999).random()}"

    // --- 🚨 LOCK-SCREEN SOS RECEIVER (BACKGROUND GPS) ---
    private val sosReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.reshqmess.SOS_TRIGGERED") {
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

                    fusedLocationClient.lastLocation
                        .addOnSuccessListener { loc ->
                            val lat = loc?.latitude ?: 0.0
                            val lng = loc?.longitude ?: 0.0
                            triggerBackgroundSos(lat, lng)
                        }
                        .addOnFailureListener {
                            triggerBackgroundSos(0.0, 0.0)
                        }
                } else {
                    triggerBackgroundSos(0.0, 0.0)
                }
            }
        }
    }

    private fun triggerBackgroundSos(lat: Double, lng: Double) {
        val criticalSos = SosPayload(
            victimName = myDeviceName,
            lat = lat,
            lng = lng,
            message = "HELP! (Emergency Auto-Connect Triggered)",
            type = "CRITICAL"
        )
        // Auto-Connect & Queue the SOS
        meshManager.triggerEmergencyBroadcast(myDeviceName, criticalSos)
        viewModel.addOrUpdateVictim(criticalSos)
        viewModel.setStatus("🚨 Radios Active: Searching for Rescuers...")
        Toast.makeText(this, "🚨 AUTO-CONNECT BEACON ACTIVE", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().userAgentValue = packageName
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))

        meshManager = MeshManager(this, viewModel)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val serviceIntent = Intent(this, EmergencyService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        val filter = IntentFilter("com.example.reshqmess.SOS_TRIGGERED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(sosReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(sosReceiver, filter)
        }

        setContent {
            ReshQMessTheme {
                val context = LocalContext.current
                val lifecycleOwner = LocalLifecycleOwner.current
                var currentScreen by remember { mutableIntStateOf(0) }

                val permissionsState = rememberMultiplePermissionsState(
                    permissions = listOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_ADVERTISE,
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.NEARBY_WIFI_DEVICES,
                        Manifest.permission.INTERNET,
                        Manifest.permission.ACCESS_NETWORK_STATE,
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.CAMERA // Added Camera Permission
                    )
                )

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) permissionsState.launchMultiplePermissionRequest()
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                val victimList = viewModel.victimList.observeAsState(emptyList())
                val chatHistory = viewModel.chatHistory.observeAsState(emptyList())
                val connectionStatus = viewModel.connectionStatus.observeAsState("Offline")

                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = { ProNavBar(currentScreen) { currentScreen = it } }
                ) { innerPadding ->

                    Box(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                    ) {

                        val infiniteTransition = rememberInfiniteTransition(label = "")

                        val offset1 by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(4000, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ), label = ""
                        )

                        val offset2 by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(6000, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ), label = ""
                        )

                        // 🔴 WAVE 1 (main)
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFF1A0000),
                                            Color(0xFF2A0000),
                                            Color(0xFF3A0000),
                                            Color(0xFF2A0000),
                                            Color(0xFF1A0000)
                                        ),
                                        start = Offset(offset1 * 800f, 0f),
                                        end = Offset(offset1 * 800f + 800f, 800f)
                                    )
                                )
                        )

                        // 🔴 WAVE 2 (soft overlay)
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color(0x001A0000),
                                            Color(0x883A0000),
                                            Color(0x001A0000),

                                            Color(0xFF2A0000),
                                            Color(0xFF3A0000),
                                            Color(0xFF2A0000),
                                            Color(0xFF1A0000)
                                        ),
                                        start = Offset(offset2 * 800f, 800f),
                                        end = Offset(offset2 * 800f + 800f, 0f)
                                    )
                                )
                        )

                        // ✅ YOUR UI (unchanged)
                        AnimatedContent(targetState = currentScreen, label = "Fade") { target ->
                            when (target) {
                                0 -> ProDashboard(
                                    myName = myDeviceName,
                                    status = connectionStatus.value,
                                    onHost = { meshManager.startHosting(myDeviceName) },
                                    onScan = { meshManager.startDiscovery() },
                                    onStop = { meshManager.stopAll() },
                                    onSos = { /* keep your same code */ },
                                    onScanMeds = { currentScreen = 3 }
                                )
                                1 -> MapScreen(victimList.value, myDeviceName)
                                2 -> ProChat(
                                    myName = myDeviceName,
                                    messages = chatHistory.value,
                                    onSend = { text ->
                                        val chat = SosPayload(myDeviceName, 0.0, 0.0, text, "CHAT")
                                        meshManager.sendSos(chat)
                                        viewModel.addOrUpdateVictim(chat)
                                    },
                                    onAudio = { audioBytes ->
                                        val audioPayload = SosPayload(myDeviceName, 0.0, 0.0, "[Live Audio Broadcast]", "AUDIO", audioData = audioBytes)
                                        meshManager.sendSos(audioPayload)
                                    }
                                )
                                3 -> MedScannerScreen(onClose = { currentScreen = 0 })
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(sosReceiver)
        meshManager.stopAll()
    }
}



// --- 🧭 NAVIGATION BAR ---
@Composable
fun ProNavBar(current: Int, onSelect: (Int) -> Unit) {
    NavigationBar(
        containerColor = ProCard,
        tonalElevation = 8.dp,
    ) {
        NavigationBarItem(
            icon = { Icon(Icons.Default.Dashboard, null) },
            label = { Text("Command", fontWeight = FontWeight.Medium) },
            selected = current == 0 || current == 3, // Keep Dashboard active when scanning
            onClick = { onSelect(0) },
            colors = NavigationBarItemDefaults.colors(selectedIconColor = ProPrimary, indicatorColor = ProPrimary.copy(alpha = 0.1f))
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Map, null) },
            label = { Text("Map", fontWeight = FontWeight.Medium) },
            selected = current == 1,
            onClick = { onSelect(1) },
            colors = NavigationBarItemDefaults.colors(selectedIconColor = ProPrimary, indicatorColor = ProPrimary.copy(alpha = 0.1f))
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Forum, null) },
            label = { Text("Comms", fontWeight = FontWeight.Medium) },
            selected = current == 2,
            onClick = { onSelect(2) },
            colors = NavigationBarItemDefaults.colors(selectedIconColor = ProPrimary, indicatorColor = ProPrimary.copy(alpha = 0.1f))
        )
    }
}

// --- 🏠 DASHBOARD ---
@Composable
fun ProDashboard(
    myName: String,
    status: String,
    onHost: () -> Unit,
    onScan: () -> Unit,
    onStop: () -> Unit,
    onSos: () -> Unit,
    onScanMeds: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(ProPrimary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Security, null, tint = Color.White)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("ReshQMess", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = ProTextMain)
                Text("Unit ID: $myName", style = MaterialTheme.typography.bodySmall, color = ProTextSub)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("SYSTEM STATUS", style = MaterialTheme.typography.labelSmall, color = ProTextSub, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = ProCard),
            elevation = CardDefaults.cardElevation(2.dp),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                val dotColor = if (status.contains("Connected")) ProSuccess else if (status.contains("Scanning") || status.contains("Host")) Color(0xFFF59E0B) else Color.Gray
                Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(dotColor))

                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(status.uppercase(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = ProTextMain)
                    Text(if (status.contains("Connected")) "Mesh Active" else "Standby Mode", style = MaterialTheme.typography.bodySmall, color = ProTextSub)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("COMMANDS", style = MaterialTheme.typography.labelSmall, color = ProTextSub, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        // Row 1: Network Controls
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ProCommandCard(
                title = "Broadcast",
                icon = Icons.Default.WifiTethering,
                color = ProPrimary,
                modifier = Modifier.weight(1f),
                onClick = onHost
            )
            ProCommandCard(
                title = "Search Area",
                icon = Icons.Default.Radar,
                color = Color(0xFF7C3AED),
                modifier = Modifier.weight(1f),
                onClick = onScan
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Row 2: Offline Tools
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ProCommandCard(
                title = "Scan Meds",
                icon = Icons.Default.CameraAlt,
                color = ProSuccess,
                modifier = Modifier.weight(1f),
                onClick = onScanMeds
            )
            // Empty spacer to keep the grid perfectly balanced
            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onStop,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ProTextSub)
        ) {
            Icon(Icons.Default.PowerSettingsNew, null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("TERMINATE CONNECTION")
        }

        Spacer(modifier = Modifier.weight(1f))
        val infiniteTransition = rememberInfiniteTransition(label = "")

        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ), label = ""
        )

        val colorShift by infiniteTransition.animateFloat(
            initialValue = 0.28f,   // balanced dark red
            targetValue = 0.36f,    // slightly brighter
            animationSpec = infiniteRepeatable(
                animation = tween(1200),
                repeatMode = RepeatMode.Reverse
            ), label = ""
        )

        Button(
            onClick = onSos,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale
                ),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(
                    red = colorShift,
                    green = 0f,
                    blue = 0f
                )
            )
        )   {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, null, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.Start) {
                    Text("EMERGENCY ALERT", fontWeight = FontWeight.Bold)
                    Text(
                        "Broadcast Critical GPS Beacon",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
fun ProCommandCard(title: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = ProCard),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.height(110.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = ProTextMain)
        }
    }
}

// --- 💬 CHAT & LIVE AUDIO ---
@Composable
fun ProChat(myName: String, messages: List<SosPayload>, onSend: (String) -> Unit, onAudio: (ByteArray) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Surface(shadowElevation = 2.dp, color = ProCard) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Secure Comms", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(ProSuccess))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Encrypted", style = MaterialTheme.typography.labelSmall, color = ProSuccess)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            reverseLayout = true,
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            items(messages.reversed()) { msg ->
                val isMe = msg.victimName == myName
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
                ) {
                    if (!isMe) {
                        Text(msg.victimName, style = MaterialTheme.typography.labelSmall, color = ProTextSub, modifier = Modifier.padding(start = 12.dp, bottom = 2.dp))
                    }

                    Surface(
                        color = if (isMe) ProPrimary else Color.White,
                        shape = RoundedCornerShape(
                            topStart = 18.dp, topEnd = 18.dp,
                            bottomStart = if (isMe) 18.dp else 4.dp,
                            bottomEnd = if (isMe) 4.dp else 18.dp
                        ),
                        shadowElevation = 1.dp,
                        modifier = Modifier.widthIn(max = 280.dp)
                    ) {
                        Text(
                            text = msg.message,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            color = if (isMe) Color.White else ProTextMain,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        ChatInputArea(onSend = onSend, onAudio = onAudio)
    }
}

@androidx.annotation.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInputArea(onSend: (String) -> Unit, onAudio: (ByteArray) -> Unit) {
    var textState by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }
    val audioHelper = remember { AudioHelper() }

    Surface(color = ProCard, tonalElevation = 4.dp) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = textState,
                onValueChange = { textState = it },
                placeholder = { Text("Enter message...") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = ProSurface,
                    unfocusedContainerColor = ProSurface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))

            if (textState.isNotBlank()) {
                IconButton(
                    onClick = { onSend(textState); textState = "" },
                    modifier = Modifier.background(ProPrimary, CircleShape).size(48.dp)
                ) {
                    Icon(Icons.Default.Send, null, tint = Color.White)
                }
            } else {
                IconButton(
                    onClick = {
                        if (isRecording) {
                            isRecording = false
                            audioHelper.stopStreaming()
                        } else {
                            isRecording = true
                            audioHelper.startStreaming { chunk -> onAudio(chunk) }
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(if (isRecording) ProAlert else ProSuccess, CircleShape)
                ) {
                    Icon(
                        if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }
        }
    }
}

// --- 🗺️ MAP (Advanced Auto-Zoom & Vector Path) ---
@Composable
fun MapScreen(victims: List<SosPayload>, myName: String) {
    val context = LocalContext.current

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.ALWAYS)
            controller.setZoom(15.0)
            controller.setCenter(GeoPoint(26.1445, 91.7362))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            update = { map ->
                var myLocationOverlay = map.overlays.firstOrNull { it is MyLocationNewOverlay } as? MyLocationNewOverlay
                if (myLocationOverlay == null) {
                    myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), map)
                    myLocationOverlay.enableMyLocation()
                    myLocationOverlay.enableFollowLocation()
                    map.overlays.add(myLocationOverlay)
                }

                map.overlays.clear()
                map.overlays.add(myLocationOverlay)

                val myPos = myLocationOverlay.myLocation
                val pointsToFit = mutableListOf<GeoPoint>()

                if (myPos != null) pointsToFit.add(myPos)

                for (victim in victims) {
                    if (victim.type == "CHAT" || victim.victimName == myName || victim.lat == 0.0) continue

                    val victimPos = GeoPoint(victim.lat, victim.lng)
                    pointsToFit.add(victimPos)

                    val marker = Marker(map)
                    marker.position = victimPos
                    marker.title = victim.victimName
                    marker.subDescription = "SOS: ${victim.message}"
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    marker.icon = ContextCompat.getDrawable(context, org.osmdroid.library.R.drawable.marker_default)
                    map.overlays.add(marker)

                    if (myPos != null) {
                        val pathLine = Polyline()
                        pathLine.addPoint(myPos)
                        pathLine.addPoint(victimPos)
                        pathLine.outlinePaint.color = android.graphics.Color.RED
                        pathLine.outlinePaint.strokeWidth = 8f
                        map.overlays.add(pathLine)
                    }
                }

                if (pointsToFit.size >= 2) {
                    val box = org.osmdroid.util.BoundingBox.fromGeoPoints(pointsToFit)
                    val latPadding = if (box.latitudeSpan > 0) box.latitudeSpan * 0.2 else 0.005
                    val lonPadding = if (box.longitudeSpan > 0) box.longitudeSpan * 0.2 else 0.005

                    val paddedBox = org.osmdroid.util.BoundingBox(
                        box.latNorth + latPadding,
                        box.lonEast + lonPadding,
                        box.latSouth - latPadding,
                        box.lonWest - lonPadding
                    )

                    map.zoomToBoundingBox(paddedBox, true)
                }

                map.invalidate()
            }
        )
    }
}

// --- 📷 OFFLINE MEDICINE SCANNER (Advanced Fuzzy Matching) ---
@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
fun MedScannerScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Upgraded Dictionary: Includes Generic, Indian Brands, and L-Hist Mont
    val disasterMeds = mapOf(
        "AMOXICILLIN" to "Antibiotic. Treats bacterial infections. Do NOT use for viruses.",
        "AUGMENTIN" to "Antibiotic (Amoxicillin/Clavulanate). Treats severe bacterial infections.",
        "IBUPROFEN" to "Pain/Fever reducer. Reduces inflammation. Take with food.",
        "PARACETAMOL" to "Pain/Fever reducer. Safe for mild to moderate pain.",
        "DOLO" to "Pain/Fever reducer (Paracetamol 650mg). Do not exceed 4 tablets a day.",
        "CROCIN" to "Pain/Fever reducer (Paracetamol). Safe for mild to moderate pain.",
        "CALPOL" to "Pain/Fever reducer (Paracetamol). Often given to children.",
        "LOPERAMIDE" to "Anti-diarrheal. Controls acute diarrhea. Stay hydrated!",
        "AZITHROMYCIN" to "Antibiotic. Often used for respiratory/throat infections.",
        "ASPIRIN" to "Pain reliever / Blood thinner. Can be chewed during a suspected heart attack.",
        "CETIRIZINE" to "Antihistamine. Treats allergic reactions and hives.",
        "OFLOXACIN" to "Antibiotic. Often used for severe bacterial diarrhea or UTIs.",
        "PANTOPRAZOLE" to "Antacid. Reduces stomach acid. Used for severe acidity/ulcers.",
        "DIGENE" to "Antacid. Chewable tablet for fast relief from gas and acidity.",

        // Added specifically for the photo you shared
        "LHIST" to "Allergy & Asthma Relief. Treats severe allergies, sneezing, and prevents asthma attacks. (Levocetirizine + Montelukast)",
        "LEVOCETIRIZINE" to "Antihistamine. Treats allergic reactions, runny nose, and hives. May cause drowsiness.",
        "MONTELUKAST" to "Anti-asthmatic. Prevents asthma attacks and treats severe allergies."
    )

    var foundMedName by remember { mutableStateOf<String?>(null) }
    var foundMedDesc by remember { mutableStateOf<String?>(null) }
    var liveScannedText by remember { mutableStateOf("Initializing Scanner...") }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraExecutor = Executors.newSingleThreadExecutor()
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }

                    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    val imageAnalyzer = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                val mediaImage = imageProxy.image
                                if (mediaImage != null) {
                                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                    recognizer.process(image)
                                        .addOnSuccessListener { visionText ->
                                            val scannedText = visionText.text.uppercase()

                                            // Update UI with what it actually sees
                                            if (scannedText.isNotBlank()) {
                                                liveScannedText = "Seeing: " + scannedText.take(40).replace("\n", " ") + "..."
                                            }

                                            // THE MAGIC: Fuzzy Searching the garbage text
                                            for ((medName, description) in disasterMeds) {
                                                if (scannedText.contains(medName) || fuzzyMatch(scannedText, medName)) {
                                                    foundMedName = medName
                                                    foundMedDesc = description
                                                    break
                                                }
                                            }
                                        }
                                        .addOnFailureListener { e -> Log.e("MLKit", "Text recognition failed", e) }
                                        .addOnCompleteListener { imageProxy.close() }
                                } else {
                                    imageProxy.close()
                                }
                            }
                        }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalyzer)
                    } catch (e: Exception) {
                        Log.e("CameraX", "Binding failed", e)
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Close Button
        IconButton(onClick = onClose, modifier = Modifier.padding(16.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape).align(Alignment.TopStart)) {
            Icon(Icons.Default.Close, "Close Scanner", tint = Color.White)
        }

        // Live Text Debugger
        Surface(color = Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(12.dp), modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp).widthIn(max = 250.dp)) {
            Text(text = liveScannedText, color = Color.Green, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        }

        // Target UI
        Box(modifier = Modifier.size(300.dp, 120.dp).align(Alignment.Center)) {
            // Draws a subtle white box to guide the user's camera
            Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp)))
            Text("Center Medicine Name Here", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
        }

        // Result Card
        if (foundMedName != null) {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp).align(Alignment.BottomCenter), colors = CardDefaults.cardColors(containerColor = ProSuccess), elevation = CardDefaults.cardElevation(8.dp), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MedicalServices, null, tint = Color.White, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(foundMedName!!, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(foundMedDesc!!, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { foundMedName = null }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = ProSuccess), modifier = Modifier.fillMaxWidth()) {
                        Text("Scan Another", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// --- 🧠 LEVENSHTEIN DISTANCE ALGORITHM (Fuzzy Matching) ---
// This separates the actual name from the manufacturing noise and allows for 1 or 2 letter typos!
fun fuzzyMatch(scannedText: String, target: String): Boolean {
    // 1. Clean the text (remove brackets, numbers, special chars, leave only uppercase letters)
    val cleanedText = scannedText.replace(Regex("[^A-Z\\s]"), "")

    // 2. Split the giant block of text into individual words
    val words = cleanedText.split(Regex("\\s+")).filter { it.length >= target.length - 2 }

    // 3. Set how many "typos" we will accept (e.g. DOLO allows 1 typo, PARACETAMOL allows 2)
    val maxTyposAllowed = if (target.length <= 5) 1 else 2

    // 4. Check every word on the medicine strip against our target
    for (word in words) {
        if (levenshtein(word, target) <= maxTyposAllowed) {
            return true
        }
    }
    return false
}

// Calculates the exact number of edits required to turn String A into String B
fun levenshtein(lhs: CharSequence, rhs: CharSequence): Int {
    val lhsLength = lhs.length
    val rhsLength = rhs.length

    var cost = IntArray(lhsLength + 1) { it }
    var newCost = IntArray(lhsLength + 1)

    for (i in 1..rhsLength) {
        newCost[0] = i
        for (j in 1..lhsLength) {
            val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
            val costReplace = cost[j - 1] + match
            val costInsert = cost[j] + 1
            val costDelete = newCost[j - 1] + 1
            newCost[j] = minOf(costInsert, costDelete, costReplace)
        }
        val swap = cost; cost = newCost; newCost = swap
    }
    return cost[lhsLength]
}