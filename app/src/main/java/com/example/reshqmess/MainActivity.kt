package com.example.reshqmess

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Bundle
import android.provider.Settings
import android.preference.PreferenceManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
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

        // 1. Setup GPS Client
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setContent {
            val context = LocalContext.current
            val lifecycleOwner = LocalLifecycleOwner.current
            var showMap by remember { mutableStateOf(false) }

            // Permissions
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

            if (showMap) {
                // --- MAP SCREEN ---
                Box(modifier = Modifier.fillMaxSize()) {
                    OsmMapView(victimList.value)

                    Button(
                        onClick = { showMap = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                        Text(" CLOSE")
                    }
                }
            } else {
                // --- DASHBOARD SCREEN ---
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("ReshQMess Command", style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(20.dp))

                    // Status Indicator
                    Card(colors = CardDefaults.cardColors(containerColor = if (permissionsState.allPermissionsGranted) Color(0xFF4CAF50) else Color.Red)) {
                        Text(
                            text = if (permissionsState.allPermissionsGranted) "✅ SYSTEM READY" else "❌ PERMISSIONS MISSING",
                            color = Color.White,
                            modifier = Modifier.padding(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(30.dp))

                    // Connection Buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(onClick = { meshManager.startHosting("Victim-1") }, modifier = Modifier.weight(1f)) { Text("HOST") }
                        Button(onClick = { meshManager.startDiscovery() }, modifier = Modifier.weight(1f)) { Text("SCAN") }
                    }

                    Spacer(modifier = Modifier.height(30.dp))

                    // 2. THE NEW "REAL GPS" SOS BUTTON
                    Button(
                        onClick = {
                            if (permissionsState.allPermissionsGranted) {
                                Toast.makeText(context, "Getting GPS...", Toast.LENGTH_SHORT).show()

                                // Get Real Location
                                try {
                                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                                        .addOnSuccessListener { location: Location? ->
                                            if (location != null) {
                                                // Success! Send Real Data
                                                val sos = SosPayload(
                                                    victimName = "Me",
                                                    lat = location.latitude,
                                                    lng = location.longitude,
                                                    message = "HELP! (Real GPS)",
                                                    type = "CRITICAL"
                                                )
                                                meshManager.sendSos(sos)
                                                Toast.makeText(context, "SOS Sent at: ${location.latitude}", Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(context, "Error: GPS Signal Lost", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                } catch (e: SecurityException) {
                                    Toast.makeText(context, "Permission Error", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Grant Permissions first!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        modifier = Modifier.fillMaxWidth().height(60.dp)
                    ) {
                        Text("SEND REAL SOS (GPS)", style = MaterialTheme.typography.titleLarge)
                    }

                    Spacer(modifier = Modifier.height(30.dp))

                    Button(
                        onClick = { showMap = true },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Blue)
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null)
                        Text(" OPEN NAVIGATOR")
                    }
                }
            }
        }
    }
}

// (Map code remains exactly the same as before)
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
                val dist = myPos.distanceToAsDouble(victimPos).toInt()
                line.title = "$dist meters"
                line.setOnClickListener { _, _, _ ->
                    Toast.makeText(context, "$dist meters", Toast.LENGTH_SHORT).show()
                    true
                }
                map.overlays.add(line)
            }
        }
        map.invalidate()
    }
}