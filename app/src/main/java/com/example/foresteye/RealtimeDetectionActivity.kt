package com.example.foresteye

import android.app.NotificationManager
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.foresteye.databinding.ActivityRealtimeDetectionBinding
import com.google.firebase.database.*

data class Detection(
    val animal: String? = null,
    val imageUrl: String? = null,
    val location: String? = null,
    val timestamp: String? = null
)

class RealtimeDetectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRealtimeDetectionBinding
    private lateinit var database: DatabaseReference
    private lateinit var adapter: DetectionAdapter
    private val detections = mutableListOf<Detection>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRealtimeDetectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Firebase reference
        database = FirebaseDatabase.getInstance().getReference("detections")

        adapter = DetectionAdapter(detections)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        listenForDetections()
    }

    private fun listenForDetections() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                detections.clear()
                for (child in snapshot.children) {
                    val det = child.getValue(Detection::class.java)
                    if (det != null) detections.add(det)
                }
                adapter.notifyDataSetChanged()

                if (detections.isNotEmpty()) {
                    val latest = detections.last()
                    NotificationHelper.showNotification(
                        this@RealtimeDetectionActivity,
                        "Animal Detected: ${latest.animal}",
                        "Location: ${latest.location}",
                        latest.imageUrl
                    )
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@RealtimeDetectionActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }
}
