package com.example.foresteye

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.foresteye.databinding.ActivityAlertDetailsBinding
import java.text.SimpleDateFormat
import java.util.*

class AlertDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlertDetailsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlertDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ✅ Get the exact extras passed from LiveAlertsActivity
        val animal = intent.getStringExtra("animal") ?: "Unknown Animal"
        val imageUrl =
            intent.getStringExtra("imageUrl1") // from LiveAlertsActivity (uses imageUrl1 for token URLs)
                ?: intent.getStringExtra("imageUrl") // fallback to older field
        val location = intent.getStringExtra("location") ?: "Unknown Location"
        val timestampRaw = intent.getStringExtra("timestamp") ?: "Unknown Time"

        // ✅ Properly handle timestamp (both string & millis)
        val formattedTime = formatTimestamp(timestampRaw)

        // ✅ Set text fields
        binding.detailAnimal.text = "🐾 $animal"
        binding.detailLocation.text = "📍 $location"
        binding.detailTimestamp.text = "🕒 $formattedTime"

        // ✅ Debug log (optional)
        println("🌿 DEBUG → imageUrl received = $imageUrl")

        // ✅ Correct Glide setup (works for Firebase URLs)
        if (!imageUrl.isNullOrBlank()) {
            Glide.with(this)
                .load(imageUrl.trim())
                .placeholder(R.drawable.twotone_forest_24) // Shown while loading
                .error(R.drawable.twotone_forest_24)       // Shown if fails
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(binding.detailImage)
        } else {
            binding.detailImage.setImageResource(R.drawable.twotone_forest_24)
        }

        // ✅ Verified button
        binding.btnMarkVerified.setOnClickListener {
            binding.btnMarkVerified.apply {
                text = "✅ Marked as Verified"
                isEnabled = false
                alpha = 0.7f
            }
        }
    }

    // ✅ Helper function to handle both timestamp types
    private fun formatTimestamp(timestamp: String): String {
        return try {
            val millis = timestamp.toLongOrNull() ?: return timestamp
            val sdf = SimpleDateFormat("dd/MM/yyyy, hh:mm:ss a", Locale.getDefault())
            sdf.format(Date(millis))
        } catch (e: Exception) {
            timestamp
        }
    }
}
