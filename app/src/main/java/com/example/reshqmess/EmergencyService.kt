
package com.example.reshqmess

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class EmergencyService : Service() {

    private val CHANNEL_ID = "ReshQMess_Emergency_Channel"
    val ACTION_SOS_TRIGGER = "com.example.reshqmess.SOS_TRIGGERED"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 1. Create the Intent that opens the app when the notification is tapped
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingOpenApp = PendingIntent.getActivity(
            this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE
        )

        // 2. Create the Intent for the SOS Button (Sends a Broadcast)
        val sosIntent = Intent(ACTION_SOS_TRIGGER)
        val pendingSos = PendingIntent.getBroadcast(
            this, 1, sosIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3. Build the Notification
        // 3. Build the Notification
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ReshQMess is Active")
            .setContentText("Mesh network running in background.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingOpenApp)
            // THIS IS THE LINE THAT CREATES THE BUTTON:
            .addAction(
                android.R.drawable.ic_menu_send,
                "🚨 BROADCAST SOS",
                pendingSos
            )
            .setOngoing(true)
            .build()

        // 4. Start the Foreground Service
         startForeground(1, notification)

        return START_STICKY // Restart automatically if the system kills it
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // We don't need binding for this
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Emergency Mesh Service",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}