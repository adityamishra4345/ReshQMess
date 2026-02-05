package com.example.reshqmess.mesh

import android.content.Context
import com.example.reshqmess.model.SosPayload
import com.example.reshqmess.viewmodel.DisasterViewModel
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*

class MeshManager(private val context: Context, private val viewModel: DisasterViewModel) {

    private val connectionsClient = Nearby.getConnectionsClient(context)
    private var myEndpointId: String? = null
    private val STRATEGY = Strategy.P2P_CLUSTER

    // 1. HOSTING
    fun startHosting(name: String) {
        viewModel.setStatus("📡 Initializing Mesh Host...")
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

    // 3. STOP
    fun stopAll() {
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        myEndpointId = null
        viewModel.setStatus("🛑 Disconnected")
    }

    // 4. CONNECTION HANDLER (Standard Stable Version)
    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            viewModel.setStatus("🔗 Connecting to ${info.endpointName}...")
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                myEndpointId = endpointId
                viewModel.setStatus("✅ Connected to $endpointId")
            } else {
                viewModel.setStatus("❌ Connection Rejected")
            }
        }

        override fun onDisconnected(endpointId: String) {
            viewModel.setStatus("⚠️ Disconnected: $endpointId")
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            viewModel.setStatus("🔎 Found: ${info.endpointName}")
            connectionsClient.requestConnection("Rescuer", endpointId, connectionLifecycleCallback)
        }
        override fun onEndpointLost(endpointId: String) {}
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            payload.asBytes()?.let { bytes ->
                SosPayload.fromBytes(bytes)?.let { viewModel.addOrUpdateVictim(it) }
            }
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    fun sendSos(payload: SosPayload) {
        val bytes = payload.toBytes()
        if (myEndpointId != null) {
            connectionsClient.sendPayload(myEndpointId!!, Payload.fromBytes(bytes))
        }
    }
}