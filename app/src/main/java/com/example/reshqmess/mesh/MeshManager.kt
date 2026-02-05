package com.example.reshqmess.mesh

import android.content.Context
import com.example.reshqmess.model.SosPayload
import com.example.reshqmess.viewmodel.DisasterViewModel
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import java.nio.charset.StandardCharsets

class MeshManager(private val context: Context, private val viewModel: DisasterViewModel) {

    private val connectionsClient = Nearby.getConnectionsClient(context)
    private var myEndpointId: String? = null

    // 1. START HOSTING (Advertising)
    fun startHosting(name: String) {
        viewModel.setStatus("📡 Advertising as $name...")
        val options = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()

        connectionsClient.startAdvertising(
            name,
            "com.example.reshqmess",
            connectionLifecycleCallback,
            options
        ).addOnSuccessListener {
            viewModel.setStatus("✅ You are now HOSTING")
        }.addOnFailureListener {
            viewModel.setStatus("❌ Host Failed: ${it.message}")
        }
    }

    // 2. START SCANNING (Discovery)
    fun startDiscovery() {
        viewModel.setStatus("🔍 Scanning for nearby devices...")
        val options = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()

        connectionsClient.startDiscovery(
            "com.example.reshqmess",
            endpointDiscoveryCallback,
            options
        ).addOnSuccessListener {
            viewModel.setStatus("✅ Scan Started. Waiting for devices...")
        }.addOnFailureListener {
            viewModel.setStatus("❌ Scan Failed: ${it.message}")
        }
    }

    // 3. HANDLE CONNECTIONS
    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // Auto-Accept Connection
            viewModel.setStatus("🔗 Connecting to ${info.endpointName}...")
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    viewModel.setStatus("✅ Connected to $endpointId")
                    myEndpointId = endpointId
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    viewModel.setStatus("❌ Connection Rejected")
                }
                ConnectionsStatusCodes.STATUS_ERROR -> {
                    viewModel.setStatus("❌ Connection Error")
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            viewModel.setStatus("⚠️ Disconnected from $endpointId")
        }
    }

    // 4. FIND DEVICES
    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            viewModel.setStatus("🔎 Found ${info.endpointName}. Requesting connection...")
            connectionsClient.requestConnection("Rescuer", endpointId, connectionLifecycleCallback)
        }

        override fun onEndpointLost(endpointId: String) {
            viewModel.setStatus("⚠️ Lost sight of $endpointId")
        }
    }

    // 5. RECEIVE DATA
    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            payload.asBytes()?.let { bytes ->
                val data = SosPayload.fromBytes(bytes)
                if (data != null) {
                    viewModel.addOrUpdateVictim(data)
                }
            }
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    // 6. SEND DATA
    fun sendSos(payload: SosPayload) {
        val bytes = payload.toBytes()
        connectionsClient.sendPayload(
            myEndpointId ?: return, // Send to specific connected device (or loop for all if needed)
            Payload.fromBytes(bytes)
        )
        // If broadcasting to multiple, usually you keep a list of connected endpoints
        // For this simple version, we assume 1-to-1 or use sendPayload(listOfEndpoints, ...)
        // To be safe, use sendPayload(listOf(myEndpointId!!), ...) if connected.
        // Note: For true mesh, you need to maintain a list of `connectedEndpoints`.
    }
}