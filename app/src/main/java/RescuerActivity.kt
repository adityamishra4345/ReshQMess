package com.example.reshqmess

import android.location.Location
import android.os.Bundle
import android.preference.PreferenceManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.osmdroid.config.Configuration
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class RescuerActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var locationOverlay: MyLocationNewOverlay

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
        setContentView(R.layout.activity_rescuer)

        map = findViewById(R.id.mapView)
        map.setMultiTouchControls(true)

        // Guwahati Home Base
        val mapController = map.controller
        mapController.setZoom(14.5)
        mapController.setCenter(GeoPoint(26.1445, 91.7362))

        // Lock to Guwahati
        map.setScrollableAreaLimitDouble(BoundingBox(26.25, 91.90, 26.05, 91.55))

        // Setup User Location (The Puck)
        locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), map)
        locationOverlay.enableMyLocation()
        map.overlays.add(locationOverlay)

        // Setup the Open Scanner Button
        val btnOpenScanner = findViewById<android.widget.Button>(R.id.btnOpenScanner)
        btnOpenScanner.setOnClickListener {
            // An 'Intent' is Android's way of moving from one screen to another
            val intent = android.content.Intent(this, ScannerActivity::class.java)
            startActivity(intent)
        }

        // TEST: Add a victim near Uzan Bazar
        addVictimMarker(26.1912, 91.7502, "Victim A")
    }

    // 1. Logic to calculate distance between you and the victim
    private fun getDistanceString(victimLat: Double, victimLng: Double): String {
        val userLocation = locationOverlay.myLocation ?: return "Distance unknown"

        val results = FloatArray(1)
        Location.distanceBetween(
            userLocation.latitude, userLocation.longitude,
            victimLat, victimLng,
            results
        )

        val distanceInMeters = results[0]
        return if (distanceInMeters >= 1000) {
            String.format("%.1f km away", distanceInMeters / 1000)
        } else {
            String.format("%d m away", distanceInMeters.toInt())
        }
    }

    // 2. Updated marker logic to include the distance label
    private fun addVictimMarker(lat: Double, lng: Double, name: String) {
        val victimMarker = Marker(map)
        victimMarker.position = GeoPoint(lat, lng)
        victimMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        victimMarker.icon = ContextCompat.getDrawable(this, R.drawable.ic_victim_pin)

        // Combine Name and Distance
        val distanceLabel = getDistanceString(lat, lng)
        victimMarker.title = "$name\n$distanceLabel"

        map.overlays.add(victimMarker)
        map.invalidate()
    }
}