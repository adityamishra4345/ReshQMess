package com.example.reshqmess

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Initialize Osmdroid Configuration
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))

        setContentView(R.layout.activity_rescuer)

        // 2. Setup the Map View
        map = findViewById(R.id.mapView)
        map.setMultiTouchControls(true)

        // 3. Set Home Base to Guwahati Center
        val mapController = map.controller
        mapController.setZoom(14.5)
        val guwahatiCenter = GeoPoint(26.1445, 91.7362)
        mapController.setCenter(guwahatiCenter)

        // 4. Set Bounding Box (Lock to Guwahati)
        val guwahatiBounds = BoundingBox(26.25, 91.90, 26.05, 91.55)
        map.setScrollableAreaLimitDouble(guwahatiBounds)

        // 5. Show Real-time Rescuer Location (The "Puck")
        val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), map)
        locationOverlay.enableMyLocation()
        locationOverlay.enableFollowLocation()
        map.overlays.add(locationOverlay)

        // --- TEST: Add a Victim Pin (You can delete this later) ---
        addVictimMarker(26.185, 91.748, "Distress Signal A")
    }

    // Function to add the red pin for victims
    private fun addVictimMarker(lat: Double, lng: Double, title: String) {
        val victimMarker = Marker(map)
        victimMarker.position = GeoPoint(lat, lng)
        victimMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

        // Use the icon you added to the drawable folder
        victimMarker.icon = ContextCompat.getDrawable(this, R.drawable.ic_victim_pin)
        victimMarker.title = title

        map.overlays.add(victimMarker)
        map.invalidate() // Refresh map
    }
}