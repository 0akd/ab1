package com.arjun.absolutra.downloader.common

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.arjun.absolutra.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MediaDownloadService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private val CHANNEL_ID = "media_download_channel"
    private val NOTIFICATION_ID = 1001

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Initializing...", 0, "", ""))

        // Listen for progress changes in real-time, throttled to 1 update/sec to avoid System UI freezes
        serviceScope.launch {
            var lastUpdate = 0L
            MediaDownloadManager.notificationState.collect { state ->
                val now = System.currentTimeMillis()
                if (now - lastUpdate > 1000 || state.progress >= 100f) {
                    lastUpdate = now
                    val notification = buildNotification(state.title, state.progress.toInt(), state.eta, state.line)
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, 
                buildNotification("Processing Media...", 0, "", ""), 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification("Processing Media...", 0, "", ""))
        }
        return START_STICKY
    }

    private fun buildNotification(title: String, progress: Int, eta: String, line: String): android.app.Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(if (eta.isNotBlank()) "$line | ETA: $eta" else line)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, progress == 0 && eta.isBlank())
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Media Background Process",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress for ongoing background media downloads"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
