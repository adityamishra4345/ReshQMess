package com.example.reshqmess

import android.Manifest
import androidx.compose.material.icons.filled.* // DELETE references to "rounded.emergency_share" or "rounded.radar"
import android.location.Location
import android.os.Bundle
import android.preference.PreferenceManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.reshqmess.mesh.MeshManager
import com.example.reshqmess.model.SosPayload
import com.example.reshqmess.viewmodel.DisasterViewModel
import com.google.accompanist.permissions.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

// --- 🏛️ PROFESSIONAL COLOR SYSTEM ---
val ProPrimary = Color(0xFF2563EB) // Royal Blue (Trust)
val ProSurface = Color(0xFFF3F4F6) // Cool Grey (Background)
val ProCard = Color(0xFFFFFFFF)    // Pure White
val ProAlert = Color(0xFFDC2626)   // Signal Red
val ProSuccess = Color(0xFF059669) // Emerald Green
val ProTextMain = Color(0xFF111827) // Almost Black
val ProTextSub = Color(0xFF6B7280)  // Grey Text

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<DisasterViewModel>()
    private lateinit var meshManager: MeshManager

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().userAgentValue = packageName
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
        meshManager = MeshManager(this, viewModel)
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setContent {
            val myDeviceName = remember { "Unit-${(100..999).random()}" }

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
                        Manifest.permission.ACCESS_NETWORK_STATE
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
                    bottomBar = { ProNavBar(currentScreen) { currentScreen = it } },
                    floatingActionButton = {
                        val context = LocalContext.current
                        FloatingActionButton(onClick = {
                            val intent = android.content.Intent(context, CameraSelectionActivity::class.java)
                            context.startActivity(intent)
                        }) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = "Camera")
                        }
                    }

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
                                                        if (loc != null) {
                                                            val sos = SosPayload(myDeviceName, loc.latitude, loc.longitude, "HELP! (GPS)", "CRITICAL")
                                                            meshManager.sendSos(sos)
                                                            viewModel.addOrUpdateVictim(sos)
                                                            Toast.makeText(context, "🚨 EMERGENCY BEACON ACTIVE", Toast.LENGTH_LONG).show()
                                                        }
                                                    }
                                            } catch (e: SecurityException) {}
                                        } else {
                                            permissionsState.launchMultiplePermissionRequest()
                                        }
                                    }
                                )
                                1 -> MapScreen(victimList.value, myDeviceName)
                                2 -> ProChat(myDeviceName, chatHistory.value) { text ->
                                    val chat = SosPayload(myDeviceName, 0.0, 0.0, text, "CHAT")
                                    meshManager.sendSos(chat)
                                    viewModel.addOrUpdateVictim(chat)
                                }
                            }
                        }
                    }
                }
            }
        }
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
            selected = current == 0,
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
    onSos: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        // 1. Header
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

        // 2. Status Monitor
        Text("SYSTEM STATUS", style = MaterialTheme.typography.labelSmall, color = ProTextSub, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = ProCard),
            elevation = CardDefaults.cardElevation(2.dp),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                // Status Dot
                val dotColor = if (status.contains("Connected")) ProSuccess else if (status.contains("Scanning")) Color(0xFFF59E0B) else Color.Gray
                Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(dotColor))

                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(status.uppercase(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = ProTextMain)
                    Text(if (status.contains("Connected")) "Mesh Active" else "Standby Mode", style = MaterialTheme.typography.bodySmall, color = ProTextSub)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 3. COMMAND CENTER
        Text("COMMANDS", style = MaterialTheme.typography.labelSmall, color = ProTextSub, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        // First Row: Mesh Networking
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

        // Second Row: AI Tools (This is where our new button goes!)
        val context = LocalContext.current
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ProCommandCard(
                title = "CAMERA TEST",
                icon = Icons.Default.PhotoCamera, // New Icon
                color = ProSuccess,             // Green for safety/medical
                modifier = Modifier.weight(1f),
                onClick = {
                    // This launches the intermediate selection screen we created
                    val intent = android.content.Intent(context, CameraSelectionActivity::class.java)
                    context.startActivity(intent)
                }
            )
            // Placeholder for a future tool (or keep empty for now)
            Spacer(modifier = Modifier.weight(1f))
        }

        // Stop Button
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

        // 4. SOS Trigger
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

// --- 💬 CHAT ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProChat(myName: String, messages: List<SosPayload>, onSend: (String) -> Unit) {
    var textState by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().background(ProSurface)) {
        // Chat Header
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

        // Message List
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

        // Input Area
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
                IconButton(
                    onClick = { if (textState.isNotBlank()) { onSend(textState); textState = "" } },
                    modifier = Modifier.background(ProPrimary, CircleShape).size(48.dp)
                ) {
                    Icon(Icons.Default.ArrowUpward, null, tint = Color.White)
                }
            }
        }
    }
}

// --- 🗺️ MAP (Standard) ---
@Composable
fun MapScreen(victims: List<SosPayload>, myName: String) {
    val context = LocalContext.current
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = {
            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(17.0)
                controller.setCenter(GeoPoint(26.1445, 91.7362))
                val myLoc = MyLocationNewOverlay(GpsMyLocationProvider(context), this)
                myLoc.enableMyLocation()
                myLoc.enableFollowLocation()
                overlays.add(myLoc)
            }
        }, update = { map ->
            val myLoc = map.overlays.firstOrNull { it is MyLocationNewOverlay }
            map.overlays.clear()
            if (myLoc != null) map.overlays.add(myLoc)
            val myPos = (myLoc as? MyLocationNewOverlay)?.myLocation

            for (victim in victims) {
                if (victim.type == "CHAT" || victim.victimName == myName) continue
                val pos = GeoPoint(victim.lat, victim.lng)
                val marker = Marker(map)
                marker.position = pos
                marker.title = victim.victimName
                marker.subDescription = victim.message
                marker.icon = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_victim_pin)
                map.overlays.add(marker)
                if (myPos != null) {
                    val line = Polyline()
                    line.addPoint(myPos); line.addPoint(pos)
                    line.color = android.graphics.Color.RED; line.width = 5.0f
                    map.overlays.add(line)
                }
            }
            map.invalidate()
        })
    }
    val x = 1
}