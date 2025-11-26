package com.example.foresteye

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.foresteye.databinding.ItemDetectionBinding

class DetectionAdapter(private val detections: List<Detection>) :
    RecyclerView.Adapter<DetectionAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemDetectionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDetectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount() = detections.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = detections[position]
        holder.binding.animalName.text = item.animal ?: "Unknown Animal"
        holder.binding.locationText.text = "📍 ${item.location ?: "N/A"}"
        holder.binding.timeText.text = "🕓 ${item.timestamp ?: ""}"

        Glide.with(holder.itemView)
            .load(item.imageUrl)
            .into(holder.binding.animalImage)
    }
}
