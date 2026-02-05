package com.example.reshqmess.mesh

import android.content.Context
import com.example.reshqmess.model.SosPayload
import com.example.reshqmess.viewmodel.DisasterViewModel
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*

class MeshManager(private val context: Context, private val viewModel: DisasterViewModel) {

    private val STRATEGY = Strategy.P2P_STAR
    private val SERVICE_ID = "com.example.reshqmess.SERVICE"

    // Track who we are talking to
    private val connectedEndpoints = mutableListOf<String>()

    // 1. HOST (Victim)
    fun startHosting(name: String) {
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        Nearby.getConnectionsClient(context)
            .startAdvertising(name, SERVICE_ID, connectionLifecycleCallback, options)
            .addOnSuccessListener { viewModel.addLog("Broadcasting as $name...") }
            .addOnFailureListener { viewModel.addLog("Error: ${it.message}") }
    }

    // 2. CLIENT (Rescuer)
    fun startDiscovery() {
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        Nearby.getConnectionsClient(context)
            .startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
            .addOnSuccessListener { viewModel.addLog("Scanning for signals...") }
            .addOnFailureListener { viewModel.addLog("Error: ${it.message}") }
    }

    // 3. THE MISSING PIECE: Function to Send Data
    fun sendSos(payload: SosPayload) {
        val bytes = payload.toBytes()
        val dataPayload = Payload.fromBytes(bytes)

        if (connectedEndpoints.isEmpty()) {
            viewModel.addLog("No one to send to! (Wait for connection)")
            return
        }

        // Send to everyone connected
        Nearby.getConnectionsClient(context).sendPayload(connectedEndpoints, dataPayload)
        viewModel.addLog("Sent SOS: ${payload.message}")
    }

    // --- CALLBACKS ---

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            viewModel.addLog("Found Peer: ${info.endpointName}")
            Nearby.getConnectionsClient(context)
                .requestConnection("Rescuer", endpointId, connectionLifecycleCallback)
        }
        override fun onEndpointLost(endpointId: String) {}
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Nearby.getConnectionsClient(context).acceptConnection(endpointId, payloadCallback)
            viewModel.addLog("Connecting to ${info.endpointName}...")
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                connectedEndpoints.add(endpointId)
                viewModel.addLog("Connected to ${endpointId}!")
            } else {
                viewModel.addLog("Connection Rejected")
            }
        }

        override fun onDisconnected(endpointId: String) {
            connectedEndpoints.remove(endpointId)
            viewModel.addLog("Peer Disconnected")
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            payload.asBytes()?.let {
                val data = SosPayload.fromBytes(it)
                if (data != null) viewModel.addOrUpdateVictim(data)
            }
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }
}