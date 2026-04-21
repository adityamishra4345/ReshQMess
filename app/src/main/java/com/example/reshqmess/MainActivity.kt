@file:OptIn(com.google.accompanist.permissions.ExperimentalPermissionsApi::class)
package com.example.reshqmess


import android.graphics.Bitmap
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import android.content.Context
import org.json.JSONObject
import java.io.IOException
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.preference.PreferenceManager
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


// --- 🏛️ PROFESSIONAL COLOR SYSTEM ---
val ProPrimary = Color(0xFF2563EB)
val ProSurface = Color(0xFFF3F4F6)
val ProCard = Color(0xFFFFFFFF)
val ProAlert = Color(0xFFDC2626)
val ProSuccess = Color(0xFF059669)
val ProTextMain = Color(0xFF111827)
val ProTextSub = Color(0xFF6B7280)

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
        ContextCompat.registerReceiver(
            this,
            sosReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = ProPrimary,
                    background = ProSurface,
                    surface = ProCard,
                    onSurface = ProTextMain
                )
            ) {
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
                    containerColor = ProSurface,
                    bottomBar = { ProNavBar(currentScreen) { currentScreen = it } }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                        AnimatedContent(targetState = currentScreen, label = "Fade") { target ->
                            when (target) {
                                0 -> ProDashboard(
                                    myName = myDeviceName,
                                    status = connectionStatus.value,
                                    onHost = { meshManager.startHosting(myDeviceName) },
                                    onScan = { meshManager.startDiscovery() },
                                    onStop = { meshManager.stopAll() },
                                    onSos = {
                                        if (permissionsState.allPermissionsGranted) {
                                            Toast.makeText(context, "Acquiring Precision Location...", Toast.LENGTH_SHORT).show()
                                            try {
                                                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                                                    .addOnSuccessListener { loc ->
                                                        val lat = loc?.latitude ?: 0.0
                                                        val lng = loc?.longitude ?: 0.0
                                                        val sos = SosPayload(myDeviceName, lat, lng, "HELP! (Dashboard)", "CRITICAL")
                                                        meshManager.sendSos(sos)
                                                        viewModel.addOrUpdateVictim(sos)
                                                        Toast.makeText(context, "🚨 EMERGENCY BEACON ACTIVE", Toast.LENGTH_LONG).show()
                                                    }
                                            } catch (e: SecurityException) {
                                                Toast.makeText(context, "Location Error", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            permissionsState.launchMultiplePermissionRequest()
                                        }
                                    },
                                    onScanMeds = {
                                        // CHECK CAMERA PERMISSION BEFORE OPENING
                                        if (permissionsState.permissions.find { it.permission == Manifest.permission.CAMERA }?.status?.isGranted == true) {
                                            currentScreen = 3 // Open Scanner Screen
                                        } else {
                                            Toast.makeText(context, "Camera permission required.", Toast.LENGTH_SHORT).show()
                                            permissionsState.launchMultiplePermissionRequest()
                                        }
                                    }
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
                                3 -> MedScannerScreen(onClose = { currentScreen = 0 }) // THE NEW SCANNER SCREEN
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

        Button(
            onClick = onSos,
            modifier = Modifier.fillMaxWidth().height(72.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ProAlert),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, null, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.Start) {
                    Text("EMERGENCY ALERT", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Broadcast Critical GPS Beacon", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
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
    Column(modifier = Modifier.fillMaxSize().background(ProSurface)) {
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

data class MedInfo(
    val indications: String,
    val dosage: String,
    val warnings: String
)

// --- 📷 OFFLINE MEDICINE SCANNER (Advanced Fuzzy Matching) ---
// --- 📷 OFFLINE MEDICINE SCANNER (Advanced Fuzzy Matching) ---
@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
fun MedScannerScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Updated parsing logic
    val jsonMeds = remember {
        val map = mutableMapOf<String, MedInfo>()
        val jsonString = loadJSONFromAsset(context, "medicines.json")
        if (jsonString != null) {
            try {
                val rootObject = JSONObject(jsonString)
                rootObject.keys().forEach { key ->
                    val detailsObj = rootObject.getJSONObject(key)
                    map[key.uppercase()] = MedInfo(
                        indications = detailsObj.optString("indications", "Data unavailable"),
                        dosage = detailsObj.optString("dosage_adult", "Data unavailable"),
                        warnings = detailsObj.optString("critical_warnings", "Data unavailable")
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        map
    }
    var foundMedInfo by remember { mutableStateOf<MedInfo?>(null) }

    var foundMedName by remember { mutableStateOf<String?>(null) }

    var liveScannedText by remember { mutableStateOf("Align medicine text in the box...") }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // 1. THE CAMERA PREVIEW & ANALYZER
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                previewView.scaleType = PreviewView.ScaleType.FILL_CENTER

                val cameraExecutor = Executors.newSingleThreadExecutor()
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    val imageAnalyzer = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                val fullBitmap = imageProxy.toBitmap()

                                val cropWidth = (fullBitmap.width * 0.8).toInt()
                                val cropHeight = (fullBitmap.height * 0.25).toInt()
                                val startX = (fullBitmap.width - cropWidth) / 2
                                val startY = (fullBitmap.height - cropHeight) / 2

                                val croppedBitmap = Bitmap.createBitmap(
                                    fullBitmap, startX, startY, cropWidth, cropHeight
                                )

                                val image = InputImage.fromBitmap(croppedBitmap, 0)

                                recognizer.process(image)
                                    .addOnSuccessListener { visionText ->
                                        val scannedText = visionText.text.uppercase()

                                        if (scannedText.isNotBlank()) {
                                            liveScannedText = "Seeing: " + scannedText.take(30).replace("\n", " ") + "..."
                                        }

                                        // Update inside the imageAnalyzer where you loop through jsonMeds
                                        // Inside your analyzer success listener:
                                        for ((medName, info) in jsonMeds) {
                                            if (scannedText.contains(medName) || fuzzyMatch(scannedText, medName)) {
                                                foundMedName = medName
                                                foundMedInfo = info
                                                break
                                            }
                                        }
                                    }
                                    .addOnFailureListener { e -> Log.e("MLKit", "Text recognition failed", e) }
                                    .addOnCompleteListener { imageProxy.close() }
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

        // 2. THE VISUAL OVERLAY (The "Target Box")
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            val rectWidth = canvasWidth * 0.8f
            val rectHeight = canvasHeight * 0.25f
            val left = (canvasWidth - rectWidth) / 2
            val top = (canvasHeight - rectHeight) / 2

            // Draw the semi-transparent black background
            drawRect(Color.Black.copy(alpha = 0.7f))

            // Punch the clear hole in the middle
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(left, top),
                size = Size(rectWidth, rectHeight),
                cornerRadius = CornerRadius(24f, 24f),
                blendMode = BlendMode.Clear
            )

            // Draw a nice green border around the hole
            drawRoundRect(
                color = ProSuccess,
                topLeft = Offset(left, top),
                size = Size(rectWidth, rectHeight),
                cornerRadius = CornerRadius(24f, 24f),
                style = Stroke(width = 8f)
            )
        }

        // 3. UI CONTROLS
        IconButton(
            onClick = onClose,
            modifier = Modifier.padding(16.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape).align(Alignment.TopStart)
        ) {
            Icon(Icons.Default.Close, "Close", tint = Color.White)
        }

        Surface(
            color = Color.Black.copy(alpha = 0.8f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp).widthIn(max = 250.dp)
        ) {
            Text(
                text = liveScannedText,
                color = Color.Green,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(12.dp)
            )
        }

        // 4. FOUND MEDICINE CARD
        // 4. FOUND MEDICINE CARD
        if (foundMedName != null && foundMedInfo != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.BottomCenter),
                colors = CardDefaults.cardColors(containerColor = ProSuccess)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .heightIn(max = 300.dp) // Prevents the card from taking over the whole screen
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(foundMedName!!, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Indications
                    Text("Indications:", fontWeight = FontWeight.Bold, color = Color.White)
                    Text(foundMedInfo!!.indications, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Dosage
                    Text("Adult Dosage:", fontWeight = FontWeight.Bold, color = Color.White)
                    Text(foundMedInfo!!.dosage, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Warnings
                    Text("Critical Warnings:", fontWeight = FontWeight.Bold, color = Color.White)
                    Text(foundMedInfo!!.warnings, style = MaterialTheme.typography.bodyMedium, color = Color.White)

                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            foundMedName = null
                            foundMedInfo = null // This hides the card and resumes scanning
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = ProSuccess
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Scan Another")
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

// Reads the raw JSON file from the offline assets folder
fun loadJSONFromAsset(context: Context, fileName: String): String? {
    return try {
        val inputStream = context.assets.open(fileName)
        val size = inputStream.available()
        val buffer = ByteArray(size)
        inputStream.read(buffer)
        inputStream.close()
        String(buffer, Charsets.UTF_8)
    } catch (ex: IOException) {
        ex.printStackTrace()
        null
    }
}