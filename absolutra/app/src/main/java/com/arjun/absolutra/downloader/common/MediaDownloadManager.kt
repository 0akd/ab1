package com.arjun.absolutra.downloader.common

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.arjun.absolutra.downloader.audio.Mp3DownloadState
import com.arjun.absolutra.downloader.frames.FrameExtractionState
import com.arjun.absolutra.downloader.video.VideoDownloadState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.atomic.AtomicInteger

data class NotificationState(
    val title: String,
    val progress: Float,
    val eta: String,
    val line: String
)

object MediaDownloadManager {
    // Escapes UI lifecycle. Survives swipe-to-kill while the foreground service is running.
    val applicationScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Shared global states for the UI to observe (any new ViewModel reconnects here)
    val audioState = MutableStateFlow<Mp3DownloadState>(Mp3DownloadState.Idle)
    val videoState = MutableStateFlow<VideoDownloadState>(VideoDownloadState.Idle)
    val frameState = MutableStateFlow<FrameExtractionState>(FrameExtractionState.Idle)

    // yt-dlp process ids live here so Cancel still works after ViewModel recreation
    @Volatile var audioProcessId: String? = null
    @Volatile var videoProcessId: String? = null
    @Volatile var frameProcessId: String? = null

    // Drives the persistent foreground notification
    val notificationState = MutableStateFlow(NotificationState("Starting...", 0f, "", ""))
    private val activeTasks = AtomicInteger(0)

    fun taskStarted(context: Context, title: String) {
        val current = activeTasks.incrementAndGet()
        notificationState.value = NotificationState(title, 0f, "Calculating...", "Starting task...")
        
        if (current == 1) {
            val intent = Intent(context.applicationContext, MediaDownloadService::class.java)
            ContextCompat.startForegroundService(context.applicationContext, intent)
        }
    }

    fun taskFinished(context: Context) {
        val current = activeTasks.decrementAndGet()
        if (current <= 0) {
            activeTasks.set(0)
            context.applicationContext.stopService(Intent(context.applicationContext, MediaDownloadService::class.java))
        }
    }
}
