package com.example.foresteye

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import com.example.foresteye.databinding.ActivityDashboardBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.messaging.FirebaseMessaging

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Toast.makeText(this, "🔔 Notifications enabled", Toast.LENGTH_SHORT).show()
                subscribeToForestEyeTopic()
            } else {
                Toast.makeText(this, "⚠️ Notifications permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ Inflate using DataBindingUtil (important for <layout> XML)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_dashboard)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference("LiveAlerts")

        // Set lifecycle owner to make LiveData updates observable
        binding.lifecycleOwner = this

        // 🔥 Listen for live alert count from Firebase Realtime Database
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val count = snapshot.childrenCount
                binding.tvLiveAlertCount.text = "$count Live Alerts"
            }

            override fun onCancelled(error: DatabaseError) {
                binding.tvLiveAlertCount.text = "Error loading alerts"
            }
        })

        // Buttons
        binding.btnLiveAlerts.setOnClickListener {
            startActivity(Intent(this, LiveAlertsActivity::class.java))
        }

        binding.btnManualDetection.setOnClickListener {
            startActivity(Intent(this, ManualDetectionActivity::class.java))
        }

        binding.btnLogout.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        // ✅ Subscribe to ForestEye alerts (handles permission for Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    subscribeToForestEyeTopic()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    Toast.makeText(
                        this,
                        "Notification permission is needed for wildlife alerts.",
                        Toast.LENGTH_LONG
                    ).show()
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            subscribeToForestEyeTopic()
        }
    }

    private fun subscribeToForestEyeTopic() {
        FirebaseMessaging.getInstance().subscribeToTopic("foresteye_alerts")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(
                        this,
                        "🦉 Subscribed to ForestEye Alerts",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this,
                        "⚠️ Subscription failed. Check connection.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }
}
