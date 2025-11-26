package com.example.foresteye

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.net.URL

object NotificationHelper {

    fun showNotification(context: Context, title: String, message: String, imageUrl: String?) {
        val channelId = "animal_alert_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Animal Alerts", NotificationManager.IMPORTANCE_HIGH
            )
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (imageUrl != null) {
            try {
                val bitmap = BitmapFactory.decodeStream(URL(imageUrl).openConnection().getInputStream())
                builder.setStyle(NotificationCompat.BigPictureStyle().bigPicture(bitmap))
            } catch (_: Exception) { }
        }

        NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), builder.build())
    }
}
