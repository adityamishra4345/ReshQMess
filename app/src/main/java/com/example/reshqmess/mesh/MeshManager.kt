package com.example.reshqmess.mesh

import android.content.Context
import com.example.reshqmess.AudioHelper
import com.example.reshqmess.model.SosPayload
import com.example.reshqmess.viewmodel.DisasterViewModel
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*

class MeshManager(private val context: Context, private val viewModel: DisasterViewModel) {

    private val connectionsClient = Nearby.getConnectionsClient(context)

    // --- MESH STATE ---
    private val connectedEndpoints = mutableSetOf<String>()
    private val seenMessageIds = mutableSetOf<String>()

    // Audio Helper for Real-Time Calls
    private val audioHelper = AudioHelper()

    // NEW: Holds the SOS payload if we are completely disconnected
    private var queuedEmergencyPayload: SosPayload? = null

    private val STRATEGY = Strategy.P2P_CLUSTER

    // 1. HOSTING
    fun startHosting(name: String) {
        viewModel.setStatus("📡 Initializing Host...")
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()

        connectionsClient.startAdvertising(
            name,
            "com.example.reshqmess",
            connectionLifecycleCallback,
            options
        ).addOnSuccessListener {
            viewModel.setStatus("✅ Host Active ($name)")
        }.addOnFailureListener {
            viewModel.setStatus("❌ Host Error: ${it.message}")
        }
    }

    // 2. SCANNING
    fun startDiscovery() {
        viewModel.setStatus("🔍 Scanning for Mesh...")
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()

        connectionsClient.startDiscovery(
            "com.example.reshqmess",
            endpointDiscoveryCallback,
            options
        ).addOnSuccessListener {
            viewModel.setStatus("✅ Scanning Active")
        }.addOnFailureListener {
            viewModel.setStatus("❌ Scan Error: ${it.message}")
        }
    }

    // 3. STOP EVERYTHING
    fun stopAll() {
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()

        connectedEndpoints.clear()
        seenMessageIds.clear()
        queuedEmergencyPayload = null
        audioHelper.stopStreaming()

        viewModel.setStatus("🛑 Disconnected")
    }

    // 4. CONNECTION HANDLER (SAFE VERSION)
    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            viewModel.setStatus("🔗 Negotiating with ${info.endpointName}...")
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                connectedEndpoints.add(endpointId)
                viewModel.setStatus("✅ Connected to $endpointId")

                // THE MAGIC: If we have a queued SOS, fire it INSTANTLY at the new connection
                queuedEmergencyPayload?.let { payload ->
                    val bytes = payload.toBytes()
                    connectionsClient.sendPayload(endpointId, Payload.fromBytes(bytes))
                }

            } else {
                viewModel.setStatus("❌ Connection Rejected")
            }
        }

        override fun onDisconnected(endpointId: String) {
            connectedEndpoints.remove(endpointId)
            viewModel.setStatus("⚠️ Lost: $endpointId")
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            // Auto-connect to anyone we find
            connectionsClient.requestConnection("Rescuer", endpointId, connectionLifecycleCallback)
        }
        override fun onEndpointLost(endpointId: String) {}
    }

    // 5. DATA HANDLING (Text + Audio + Hopping)
    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            payload.asBytes()?.let { bytes ->
                val data = SosPayload.fromBytes(bytes)

                if (data != null) {
                    // LOOP PREVENTION: Ignore messages we've already seen and forwarded
                    if (seenMessageIds.contains(data.id)) return
                    seenMessageIds.add(data.id)

                    // HANDLE CONTENT
                    if (data.type == "AUDIO" && data.audioData != null) {
                        viewModel.setStatus("🔊 Live Audio: ${data.victimName}")
                        audioHelper.playStream(data.audioData)
                    } else {
                        viewModel.addOrUpdateVictim(data)
                    }

                    // HOPPING: Forward to everyone else
                    for (neighbor in connectedEndpoints) {
                        if (neighbor != endpointId) {
                            connectionsClient.sendPayload(neighbor, payload)
                        }
                    }
                }
            }
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    // NORMAL SEND (Broadcasts to everyone currently connected)
    fun sendSos(payload: SosPayload) {
        val bytes = payload.toBytes()
        seenMessageIds.add(payload.id)

        if (connectedEndpoints.isNotEmpty()) {
            connectionsClient.sendPayload(connectedEndpoints.toList(), Payload.fromBytes(bytes))
        }
    }

    // NEW: EMERGENCY DEPLOYMENT (Queues the message and hunts for a connection)
    fun triggerEmergencyBroadcast(name: String, payload: SosPayload) {
        // 1. Queue it so it fires upon finding a rescuer
        queuedEmergencyPayload = payload
        seenMessageIds.add(payload.id)

        // 2. Turn on everything to maximize discovery
        startHosting("SOS-$name")
        startDiscovery()

        // 3. Try sending immediately if we happen to already have active connections
        if (connectedEndpoints.isNotEmpty()) {
            val bytes = payload.toBytes()
            connectionsClient.sendPayload(connectedEndpoints.toList(), Payload.fromBytes(bytes))
        }
    }
}