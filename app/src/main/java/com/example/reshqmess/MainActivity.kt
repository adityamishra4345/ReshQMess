package com.example.reshqmess

import android.Manifest
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.preference.PreferenceManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
            MaterialTheme(
                colorScheme = if(android.os.Build.VERSION.SDK_INT >= 31) dynamicDarkColorScheme(LocalContext.current) else darkColorScheme()
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ReshQApp(viewModel, meshManager, fusedLocationClient)
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ReshQApp(
    viewModel: DisasterViewModel,
    meshManager: MeshManager,
    locationClient: com.google.android.gms.location.FusedLocationProviderClient
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showMap by remember { mutableStateOf(false) }

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
        Box(modifier = Modifier.fillMaxSize()) {
            OsmMapView(victimList.value)
            SmallFloatingActionButton(
                onClick = { showMap = false },
                containerColor = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close Map", tint = Color.White)
            }
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ReshQ Command", fontWeight = FontWeight.Bold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .padding(16.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // *** FIX WAS HERE: Passing the whole state object, not just a boolean ***
                StatusCard(permissionsState)

                ActionCard(title = "Mesh Network", icon = Icons.Default.Settings) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { meshManager.startHosting("Victim-1") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("HOST")
                        }
                        OutlinedButton(
                            onClick = { meshManager.startDiscovery() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("SCAN")
                        }
                    }
                }

                ActionCard(title = "Navigation", icon = Icons.Default.LocationOn) {
                    Button(
                        onClick = { showMap = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("OPEN LIVE MAP RADAR")
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    "EMERGENCY TRIGGERS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )

                Button(
                    onClick = {
                        if (permissionsState.allPermissionsGranted) {
                            Toast.makeText(context, "Acquiring GPS...", Toast.LENGTH_SHORT).show()
                            try {
                                locationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                                    .addOnSuccessListener { location: Location? ->
                                        if (location != null) {
                                            val sos = SosPayload("Me", location.latitude, location.longitude, "HELP! (GPS)", "CRITICAL")
                                            meshManager.sendSos(sos)
                                            Toast.makeText(context, "CRITICAL SOS SENT", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, "GPS Signal Lost - Try Outdoors", Toast.LENGTH_LONG).show()
                                        }
                                    }
                            } catch (e: SecurityException) { /* Handle error */ }
                        } else {
                            permissionsState.launchMultiplePermissionRequest()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(32.dp))
                        Text("BROADCAST SOS", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// --- HELPER COMPONENTS ---

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun StatusCard(permissionsState: MultiplePermissionsState) {
    val context = LocalContext.current
    val isReady = permissionsState.allPermissionsGranted
    val revokedPermissions = permissionsState.permissions.filter { !it.status.isGranted }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isReady) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isReady) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isReady) Color(0xFF2E7D32) else Color(0xFFC62828),
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = if (isReady) "System Ready" else "Permissions Missing",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )

                    if (!isReady) {
                        Text(
                            text = "Tap to Fix in Settings",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Blue,
                            modifier = Modifier.clickable {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            }
                        )
                    }
                }
            }

            if (!isReady) {
                Spacer(modifier = Modifier.height(10.dp))
                Text("Missing these specific permissions:", style = MaterialTheme.typography.labelSmall, color = Color.Black)
                revokedPermissions.forEach { perm ->
                    val name = perm.permission.substringAfterLast(".")
                    Text("• $name", color = Color.Red, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun ActionCard(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

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
            if(victim.type == "CRITICAL") marker.title = "CRITICAL: ${victim.victimName}"
            map.overlays.add(marker)

            if (myPos != null) {
                val line = Polyline()
                line.addPoint(myPos)
                line.addPoint(victimPos)
                line.color = android.graphics.Color.RED
                line.width = 5.0f

                val dist = myPos.distanceToAsDouble(victimPos).toInt()
                line.title = "$dist meters to target"
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