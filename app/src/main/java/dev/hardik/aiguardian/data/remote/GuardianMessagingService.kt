package dev.hardik.aiguardian.data.remote

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dev.hardik.aiguardian.R

class GuardianMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val type = remoteMessage.data["type"] // "SOS" or "SCAM"
        val title = remoteMessage.data["title"] ?: "AI Guardian Alert"
        val body = remoteMessage.data["body"] ?: "New vulnerability detected"
        val locationUrl = remoteMessage.data["locationUrl"]

        showNotification(title, body, locationUrl, type == "SOS")
    }

    private fun showNotification(title: String, body: String, locationUrl: String?, isSos: Boolean) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "guardian_alerts"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "AI Guardian Safety Alerts",
                if (isSos) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Critical alerts for SOS and Scam detections"
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = if (locationUrl != null && locationUrl.startsWith("http")) {
            Intent(Intent.ACTION_VIEW, Uri.parse(locationUrl))
        } else {
            packageManager.getLaunchIntentForPackage(packageName)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(if (isSos) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setColor(if (isSos) Color.RED else Color.YELLOW)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Token update logic is handled by the CaretakerViewModel during pairing,
        // but real apps would update the token in the backend here if already paired.
    }
}
