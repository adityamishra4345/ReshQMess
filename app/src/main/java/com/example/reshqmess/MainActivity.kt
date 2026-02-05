package com.example.reshqmess

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.reshqmess.mesh.MeshManager
import com.example.reshqmess.model.SosPayload
import com.example.reshqmess.viewmodel.DisasterViewModel
import com.google.accompanist.permissions.*

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<DisasterViewModel>()
    private lateinit var meshManager: MeshManager

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        meshManager = MeshManager(this, viewModel)

        setContent {
            val permissionsState = rememberMultiplePermissionsState(
                permissions = listOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.NEARBY_WIFI_DEVICES
                )
            )

            val logs = viewModel.logs.observeAsState(emptyList())

            Column(modifier = Modifier.padding(16.dp)) {
                Text("ReshQMess Console", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(10.dp))

                // 1. CONNECT
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { meshManager.startHosting("Victim-1") }) { Text("HOST") }
                    Button(onClick = { meshManager.startDiscovery() }) { Text("SCAN") }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 2. SEND SOS (The New Feature)
                Button(
                    onClick = {
                        // Create a dummy payload
                        val sos = SosPayload(
                            victimName = "Victim-1",
                            lat = 26.1445, // Dummy Lat (Guwahati)
                            lng = 91.7362, // Dummy Lng
                            message = "I need help! Leg broken."
                        )
                        meshManager.sendSos(sos)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("SEND EMERGENCY SOS")
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 3. LOGS
                Text("Incoming Data:", style = MaterialTheme.typography.labelLarge)
                LazyColumn {
                    items(logs.value) { log ->
                        Text("• $log")
                    }
                }
            }
        }
    }
}