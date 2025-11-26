package com.example.foresteye

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.SmsManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.foresteye.databinding.ActivityLiveAlertsBinding
import com.google.firebase.database.*

// Updated data model to match Firebase structure
data class LocationData(
    val lat: Double? = null,
    val lon: Double? = null,
    val text: String? = null
)

data class DetectionAlert(
    val animal: String = "",
    val imageUrl1: String? = null,  // for token-based URLs
    val imageUrl: String? = null,   // for older entries
    val location: LocationData? = null,
    val timestamp: String? = null
)

class LiveAlertsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLiveAlertsBinding
    private lateinit var databaseRef: DatabaseReference
    private lateinit var adapter: LiveAlertAdapter
    private val alertList = mutableListOf<DetectionAlert>()
    private var lastAlertCount = 0

    // Replace with your phone number for testing
    private val ALERT_PHONE_NUMBER = "+911234567890" // ← your number

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLiveAlertsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkSmsPermission()

        // Setup RecyclerView
        binding.recyclerAlerts.layoutManager = LinearLayoutManager(this)
        adapter = LiveAlertAdapter(alertList) { alert ->
            val intent = Intent(this, AlertDetailsActivity::class.java)
            intent.putExtra("animal", alert.animal)
            intent.putExtra("imageUrl", alert.imageUrl)
            intent.putExtra("location", alert.location?.text ?: "Unknown")
            intent.putExtra("timestamp", alert.timestamp)
            startActivity(intent)
        }
        binding.recyclerAlerts.adapter = adapter

        // Firebase reference
        databaseRef = FirebaseDatabase.getInstance().getReference("detections")

        // Fetch live data
        fetchLiveAlerts()
    }

    private fun checkSmsPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.SEND_SMS),
                101
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "SMS permission granted ✅", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "SMS permission denied ⚠️", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchLiveAlerts() {
        databaseRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val tempList = mutableListOf<DetectionAlert>()
                for (alertSnap in snapshot.children) {
                    val alert = alertSnap.getValue(DetectionAlert::class.java)
                    if (alert != null) tempList.add(alert)
                }

                if (tempList.size > lastAlertCount) {
                    // New detection found
                    val newAlerts = tempList.takeLast(tempList.size - lastAlertCount)
                    for (newAlert in newAlerts) {
                        sendSmsAlert(newAlert)
                    }
                }
                lastAlertCount = tempList.size

                alertList.clear()
                alertList.addAll(tempList)

                if (alertList.isEmpty()) {
                    binding.statusText.text = "🌿 No new detections yet."
                } else {
                    binding.statusText.text = "🦜 Live Animal Detections"
                }
                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                binding.statusText.text = "⚠️ Error loading data."
            }
        })
    }

    private fun sendSmsAlert(alert: DetectionAlert) {
        try {
            val smsManager = SmsManager.getDefault()

            val locationText = alert.location?.text ?: "Unknown location"
            val lat = alert.location?.lat
            val lon = alert.location?.lon
            val mapLink = if (lat != null && lon != null) {
                "https://maps.google.com/?q=$lat,$lon"
            } else {
                "Location unavailable"
            }

            val message = """
                🚨 Animal Detection Alert!
                Animal: ${alert.animal}
                Location: $locationText
                Map: $mapLink
                Time: ${alert.timestamp}
                Check the app for image & details.
            """.trimIndent()

            smsManager.sendTextMessage(ALERT_PHONE_NUMBER, null, message, null, null)
            Toast.makeText(this, "SMS sent successfully 📩", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to send SMS ❌: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
