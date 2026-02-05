package com.example.reshqmess

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Bundle
import android.preference.PreferenceManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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

            // Observe Status & Data
            val victimList = viewModel.victimList.observeAsState(emptyList())
            val chatHistory = viewModel.chatHistory.observeAsState(emptyList())
            val connectionStatus = viewModel.connectionStatus.observeAsState("Idle") // <--- NEW OBSERVER

            when (currentScreen) {
                1 -> { // MAP
                    Box(modifier = Modifier.fillMaxSize()) {
                        OsmMapView(victimList.value)
                        Button(
                            onClick = { currentScreen = 0 },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                        ) { Icon(Icons.Default.Close, "Close"); Text(" CLOSE") }
                    }
                }
                2 -> { // CHAT
                    ChatScreen(
                        messages = chatHistory.value,
                        status = connectionStatus.value, // Pass status to chat too
                        onSend = { text ->
                            val chatPayload = SosPayload("Me", 0.0, 0.0, text, "CHAT")
                            meshManager.sendSos(chatPayload)
                            viewModel.addOrUpdateVictim(chatPayload)
                        },
                        onClose = { currentScreen = 0 }
                    )
                }
                else -> { // DASHBOARD
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("ReshQMess Command", style = MaterialTheme.typography.headlineMedium)

                        Spacer(modifier = Modifier.height(10.dp))

                        // --- NEW: CONNECTION STATUS BAR ---
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)), // Light Blue
                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                        ) {
                            Text(
                                text = "STATUS: ${connectionStatus.value}",
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                color = Color(0xFF0D47A1),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        // ----------------------------------

                        Card(colors = CardDefaults.cardColors(containerColor = if (permissionsState.allPermissionsGranted) Color(0xFF4CAF50) else Color.Red)) {
                            Text(if (permissionsState.allPermissionsGranted) "✅ PERMISSIONS READY" else "❌ PERMISSIONS MISSING", color = Color.White, modifier = Modifier.padding(16.dp))
                        }

                        Spacer(modifier = Modifier.height(30.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Button(onClick = { meshManager.startHosting("Victim-1") }, modifier = Modifier.weight(1f)) { Text("HOST") }
                            Button(onClick = { meshManager.startDiscovery() }, modifier = Modifier.weight(1f)) { Text("SCAN") }
                        }

                        Spacer(modifier = Modifier.height(30.dp))

                        Button(
                            onClick = {
                                if (permissionsState.allPermissionsGranted) {
                                    Toast.makeText(context, "Getting GPS...", Toast.LENGTH_SHORT).show()
                                    try {
                                        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                                            .addOnSuccessListener { location: Location? ->
                                                if (location != null) {
                                                    val sos = SosPayload("Me", location.latitude, location.longitude, "HELP! (GPS)", "CRITICAL")
                                                    meshManager.sendSos(sos)
                                                    viewModel.addOrUpdateVictim(sos)
                                                    Toast.makeText(context, "SOS Sent!", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                    } catch (e: SecurityException) {}
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                            modifier = Modifier.fillMaxWidth().height(60.dp)
                        ) { Text("SEND SOS (GPS)", style = MaterialTheme.typography.titleLarge) }

                        Spacer(modifier = Modifier.height(30.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Button(
                                onClick = { currentScreen = 1 },
                                modifier = Modifier.weight(1f).height(60.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Blue)
                            ) { Icon(Icons.Default.LocationOn, null); Text(" MAP") }

                            Button(
                                onClick = { currentScreen = 2 },
                                modifier = Modifier.weight(1f).height(60.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF673AB7))
                            ) { Icon(Icons.Default.Email, null); Text(" CHAT") }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(messages: List<SosPayload>, status: String, onSend: (String) -> Unit, onClose: () -> Unit) {
    var textState by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF0F0F0))) {
        // Chat Header
        Column(modifier = Modifier.fillMaxWidth().background(Color(0xFF673AB7))) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Emergency Chat", color = Color.White, style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
            }
            // Status in Chat
            Text(
                text = "Network: $status",
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                style = MaterialTheme.typography.labelMedium
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f).padding(8.dp),
            reverseLayout = true
        ) {
            items(messages.reversed()) { msg ->
                val isMe = msg.victimName == "Me"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (isMe) Color(0xFFD1C4E9) else Color.White),
                        modifier = Modifier.padding(4.dp).widthIn(max = 250.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(msg.victimName, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Text(msg.message, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        Row(modifier = Modifier.padding(8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = textState,
                onValueChange = { textState = it },
                placeholder = { Text("Type message...") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent)
            )
            Spacer(modifier = Modifier.width(8.dp))
            FloatingActionButton(onClick = { if (textState.isNotBlank()) { onSend(textState); textState = "" } }, containerColor = Color(0xFF673AB7)) {
                Icon(Icons.Default.Send, "Send", tint = Color.White)
            }
        }
    }
}

// (Keep OsmMapView same as before, no changes needed there)
@Composable
fun OsmMapView(victims: List<SosPayload>) {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(16.0)
            controller.setCenter(GeoPoint(26.1445, 91.7362))
        }
    }
    val myLocationOverlay = remember {
        MyLocationNewOverlay(GpsMyLocationProvider(context), mapView).apply {
            enableMyLocation()
            enableFollowLocation()
        }
    }
    AndroidView(factory = {
        if (!mapView.overlays.contains(myLocationOverlay)) mapView.overlays.add(myLocationOverlay)
        mapView
    }) { map ->
        val staticOverlays = map.overlays.filterIsInstance<MyLocationNewOverlay>()
        map.overlays.clear()
        map.overlays.addAll(staticOverlays)
        val myPos = myLocationOverlay.myLocation
        for (victim in victims) {
            if (victim.type == "CHAT") continue
            val victimPos = GeoPoint(victim.lat, victim.lng)
            val marker = Marker(map)
            marker.position = victimPos
            marker.title = victim.victimName
            marker.subDescription = victim.message
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            map.overlays.add(marker)
            if (myPos != null) {
                val line = Polyline()
                line.addPoint(myPos)
                line.addPoint(victimPos)
                line.color = android.graphics.Color.RED
                line.width = 5.0f
                map.overlays.add(line)
            }
        }
        map.invalidate()
    }
}