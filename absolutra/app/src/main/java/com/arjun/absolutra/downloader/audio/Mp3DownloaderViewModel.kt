package com.arjun.absolutra.downloader.audio

import android.content.Context
import android.media.MediaScannerConnection
import android.util.Log
import com.arjun.absolutra.downloader.common.MediaDownloadManager
import com.arjun.absolutra.downloader.common.MediaPaths
import com.arjun.absolutra.downloader.common.MediaType
import com.arjun.absolutra.downloader.common.NotificationState
import com.arjun.absolutra.downloader.common.YoutubeDlHelpers
import com.arjun.absolutra.downloader.core.explorer.BaseMediaViewModel
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class Mp3DownloadState {
    object Idle : Mp3DownloadState()
    object Initializing : Mp3DownloadState()
    data class Downloading(val progress: Float, val eta: String, val currentLine: String) : Mp3DownloadState()
    data class Success(val filePath: String) : Mp3DownloadState()
    data class Error(val message: String) : Mp3DownloadState()
}

class Mp3DownloaderViewModel : BaseMediaViewModel(MediaType.AUDIO) {
    val downloadState: StateFlow<Mp3DownloadState> = MediaDownloadManager.audioState.asStateFlow()
    private var downloadJob: Job? = null

    companion object {
        private var isLibraryInitialized = false
    }

    fun startDownload(context: Context, url: String) {
        if (url.isBlank()) {
            MediaDownloadManager.audioState.value = Mp3DownloadState.Error("URL cannot be empty")
            return
        }

        MediaDownloadManager.audioState.value = Mp3DownloadState.Initializing
        downloadJob?.cancel()

        downloadJob = MediaDownloadManager.applicationScope.launch {
            MediaDownloadManager.taskStarted(context, "Extracting Audio")
            try {
                val appCtx = context.applicationContext
                if (!isLibraryInitialized) {
                    YoutubeDL.getInstance().init(appCtx)
                    FFmpeg.getInstance().init(appCtx)
                    isLibraryInitialized = true
                }

                // If navigating folders, save to current directory!
                val outputDir = currentDir.value ?: MediaPaths.audioDir(create = true)
                val template = "${outputDir.absolutePath}/%(title)s.%(ext)s"

                val request = YoutubeDLRequest(url).apply {
                    addOption("-o", template)
                    addOption("-x")
                    addOption("--audio-format", "mp3")
                    addOption("--audio-quality", "0")
                    addOption("--embed-thumbnail")
                    addOption("--embed-metadata")
                    addOption("--no-mtime")
                    addOption("--no-update")
                    YoutubeDlHelpers.applyAuthAndClient(this, appCtx)
                }

                MediaDownloadManager.audioProcessId = "download_${System.currentTimeMillis()}"

                YoutubeDL.getInstance().execute(request, MediaDownloadManager.audioProcessId) { progress, etaInSeconds, line ->
                    MediaDownloadManager.audioState.value = Mp3DownloadState.Downloading(
                        progress = progress, eta = "${etaInSeconds}s", currentLine = line.ifBlank { "Processing..." }
                    )
                    MediaDownloadManager.notificationState.value = NotificationState("Downloading MP3", progress, "${etaInSeconds}s", line)
                }

                val latestFile = outputDir.listFiles { f -> f.extension.equals("mp3", true) }?.maxByOrNull { it.lastModified() }
                latestFile?.let { file ->
                    MediaScannerConnection.scanFile(appCtx, arrayOf(file.absolutePath), arrayOf("audio/mpeg"), null)
                }

                MediaDownloadManager.audioState.value = Mp3DownloadState.Success("Download completed! Saved locally.")
                refresh() // Sync base ViewModel's list!

            } catch (e: Exception) {
                Log.e("Mp3Downloader", "Download failed", e)
                if (e is YoutubeDLException && (e.message?.contains("canceled", ignoreCase = true) == true)) {
                    MediaDownloadManager.audioState.value = Mp3DownloadState.Idle
                } else {
                    MediaDownloadManager.audioState.value = Mp3DownloadState.Error("Download Error: ${e.cause?.message ?: e.message}")
                }
            } finally {
                MediaDownloadManager.audioProcessId = null
                MediaDownloadManager.taskFinished(context)
            }
        }
    }

    fun cancelDownload() {
        MediaDownloadManager.applicationScope.launch {
            MediaDownloadManager.audioProcessId?.let {
                try { YoutubeDL.getInstance().destroyProcessById(it) } catch (_: Exception) { }
            }
            MediaDownloadManager.audioState.value = Mp3DownloadState.Idle
        }
    }
}
