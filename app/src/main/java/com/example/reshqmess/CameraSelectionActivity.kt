package com.example.reshqmess

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class CameraSelectionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_selection)

        val btnScanInjury = findViewById<Button>(R.id.btnScanInjury)
        val btnScanMedicine = findViewById<Button>(R.id.btnScanMedicine)

        // Option 1: Scan Injury (Placeholder)
        btnScanInjury.setOnClickListener {
            Toast.makeText(this, "Injury AI Model Coming Soon!", Toast.LENGTH_SHORT).show()
        }

        // Option 2: Scan Medicine (Launches your working ML Kit scanner)
        btnScanMedicine.setOnClickListener {
            val intent = Intent(this, ScannerActivity::class.java)
            startActivity(intent)
        }
    }
}