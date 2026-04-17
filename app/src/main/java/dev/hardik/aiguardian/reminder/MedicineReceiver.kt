package dev.hardik.aiguardian.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import dev.hardik.aiguardian.R
import dev.hardik.aiguardian.utils.Constants
import java.util.*

class MedicineReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra("medicine_name") ?: "Medicine"
        val dosage = intent.getStringExtra("medicine_dosage") ?: ""
        
        showNotification(context, name, dosage)
        speakReminder(context, name, dosage)
    }

    private fun showNotification(context: Context, name: String, dosage: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "medicine_channel",
                "Medicine Reminders",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, "medicine_channel")
            .setContentTitle("Medicine Reminder")
            .setContentText("It's time for $name ($dosage)")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun speakReminder(context: Context, name: String, dosage: String) {
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                val message = "Excuse me, it's time to take your $name, $dosage"
                tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }
    }
}
