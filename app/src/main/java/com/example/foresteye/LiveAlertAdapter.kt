package com.example.foresteye

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.foresteye.databinding.ItemAlertBinding
import java.text.SimpleDateFormat
import java.util.*

class LiveAlertAdapter(
    private val alerts: List<DetectionAlert>,
    private val onItemClick: (DetectionAlert) -> Unit
) : RecyclerView.Adapter<LiveAlertAdapter.AlertViewHolder>() {

    inner class AlertViewHolder(val binding: ItemAlertBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlertViewHolder {
        val binding = ItemAlertBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AlertViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AlertViewHolder, position: Int) {
        val alert = alerts[position]
        with(holder.binding) {
            alertAnimal.text = "🐾 ${alert.animal}"

            // ✅ Safely handle nullable location and text
            val loc = alert.location
            val locationText = when {
                loc == null -> "📍 Unknown location"
                !loc.text.isNullOrEmpty() -> "📍 ${loc.text}"
                else -> "📍 Lat: ${loc.lat}, Lon: ${loc.lon}"
            }
            alertLocation.text = locationText

            // 🕒 Convert UTC → Local time safely
            alertTime.text = "🕒 ${formatToLocalTime(alert.timestamp)}"

            // 🖼️ Load image safely
            Glide.with(alertImage.context)
                .load(alert.imageUrl)
                .placeholder(R.drawable.twotone_forest_24)
                .error(R.drawable.twotone_forest_24)
                .into(alertImage)

            root.setOnClickListener {
                onItemClick(alert)
            }
        }
    }

    override fun getItemCount() = alerts.size

    // 🕓 Convert UTC to local time safely
    private fun formatToLocalTime(utcTime: String?): String {
        if (utcTime.isNullOrEmpty()) return "Unknown time"
        return try {
            val inputFormat = SimpleDateFormat("dd/MM/yyyy, HH:mm:ss a", Locale.getDefault())
            inputFormat.timeZone = TimeZone.getTimeZone("UTC")
            val date = inputFormat.parse(utcTime)
            val outputFormat = SimpleDateFormat("dd/MM/yyyy, hh:mm:ss a", Locale.getDefault())
            outputFormat.timeZone = TimeZone.getDefault()
            outputFormat.format(date!!)
        } catch (e: Exception) {
            utcTime // fallback if parse fails
        }
    }
}
