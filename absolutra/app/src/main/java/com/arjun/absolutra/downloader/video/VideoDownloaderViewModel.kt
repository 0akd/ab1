package com.arjun.absolutra.downloader.video

import android.content.Context
import android.media.MediaScannerConnection
import androidx.lifecycle.viewModelScope
import com.arjun.absolutra.downloader.common.*
import com.arjun.absolutra.downloader.core.explorer.BaseMediaViewModel
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed class VideoDownloadState {
    object Idle : VideoDownloadState()
    object FetchingFormats : VideoDownloadState()
    data class FormatSelection(val url: String, val title: String, val videoFormats: List<MediaFormat>, val audioFormats: List<MediaFormat>, val isPlaylist: Boolean = false, val playlistCount: Int? = null) : VideoDownloadState()
    object Initializing : VideoDownloadState()
    data class Downloading(val progress: Float, val eta: String, val currentLine: String) : VideoDownloadState()
    data class Success(val filePath: String) : VideoDownloadState()
    data class Error(val message: String) : VideoDownloadState()
}

class VideoDownloaderViewModel : BaseMediaViewModel(MediaType.VIDEO) {
    val downloadState: StateFlow<VideoDownloadState> = MediaDownloadManager.videoState.asStateFlow()
    private var downloadJob: Job? = null

    companion object {
        private var isLibraryInitialized = false
        private val PLAYLIST_PRESETS = listOf(
            MediaFormat("bestvideo[height<=1080][ext=mp4]+bestaudio/best[height<=1080]", "mp4", "1080p", "Best ≤1080p", "auto"),
            MediaFormat("bestvideo[height<=720][ext=mp4]+bestaudio/best[height<=720]", "mp4", "720p", "Best ≤720p", "auto"),
            MediaFormat("bestvideo[height<=480][ext=mp4]+bestaudio/best[height<=480]", "mp4", "480p", "Best ≤480p", "auto"),
            MediaFormat("best", "mp4", "Best available", "Highest quality", "auto")
        )
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "Unknown size"
        val mb = bytes / (1024.0 * 1024.0)
        return String.format("%.1f MB", mb)
    }

    fun fetchFormats(context: Context, url: String) {
        if (url.isBlank()) {
            MediaDownloadManager.videoState.value = VideoDownloadState.Error("URL cannot be empty")
            return
        }

        MediaDownloadManager.videoState.value = VideoDownloadState.FetchingFormats
        downloadJob?.cancel()

        downloadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val appCtx = context.applicationContext
                if (!isLibraryInitialized) {
                    YoutubeDL.getInstance().init(appCtx)
                    FFmpeg.getInstance().init(appCtx)
                    isLibraryInitialized = true
                }

                val isPlaylist = url.contains("playlist?list=", ignoreCase = true) || (url.contains("list=", ignoreCase = true) && (url.contains("youtube.com", ignoreCase = true) || url.contains("youtu.be", ignoreCase = true)))

                if (isPlaylist) {
                    MediaDownloadManager.videoState.value = VideoDownloadState.FormatSelection(url = url, title = "Playlist", videoFormats = PLAYLIST_PRESETS, audioFormats = emptyList(), isPlaylist = true)
                    return@launch
                }

                val request = YoutubeDLRequest(url).also { YoutubeDlHelpers.applyAuthAndClient(it, appCtx) }
                val videoInfo = YoutubeDL.getInstance().getInfo(request)
                val formats = videoInfo.formats ?: emptyList()

                fun hasVideo(f: com.yausername.youtubedl_android.mapper.VideoFormat) = !f.vcodec.isNullOrEmpty() && f.vcodec != "none"
                fun hasAudio(f: com.yausername.youtubedl_android.mapper.VideoFormat) = !f.acodec.isNullOrEmpty() && f.acodec != "none"

                val videoOnly = formats.filter { hasVideo(it) && !hasAudio(it) }.sortedByDescending { it.height }.map {
                    MediaFormat(it.formatId ?: "", it.ext ?: "", "${it.height ?: 0}p${if (it.fps > 0) " ${it.fps}fps" else ""}", it.formatNote ?: "", formatSize(it.fileSize))
                }.distinctBy { it.resolution }

                val audioOnly = formats.filter { !hasVideo(it) && hasAudio(it) }.sortedByDescending { it.fileSize }.map {
                    MediaFormat(it.formatId ?: "", it.ext ?: "", "Audio", it.formatNote ?: "", formatSize(it.fileSize))
                }.distinctBy { it.formatId }

                val combined = formats.filter { hasVideo(it) && hasAudio(it) }.sortedByDescending { it.height }.map {
                    MediaFormat(it.formatId ?: "", it.ext ?: "", "${it.height ?: 0}p (Merged)", it.formatNote ?: "", formatSize(it.fileSize))
                }

                MediaDownloadManager.videoState.value = VideoDownloadState.FormatSelection(
                    url = url, title = videoInfo.title ?: "Unknown Video", videoFormats = videoOnly.ifEmpty { combined }, audioFormats = audioOnly, isPlaylist = false
                )
            } catch (e: Exception) {
                MediaDownloadManager.videoState.value = VideoDownloadState.Error("Fetch Error: ${e.cause?.message ?: e.message ?: "Unknown error"}")
            }
        }
    }

    fun startDownloadWithFormats(context: Context, url: String, videoFormatId: String, audioFormatId: String, title: String) {
        MediaDownloadManager.videoState.value = VideoDownloadState.Initializing
        downloadJob?.cancel()

        downloadJob = MediaDownloadManager.applicationScope.launch {
            MediaDownloadManager.taskStarted(context, "Downloading Video")
            try {
                val appCtx = context.applicationContext
                val outputDir = currentDir.value ?: MediaPaths.videoDir(create = true)
                val safeTitle = MediaPaths.sanitizeFileName(title)
                val template = "${outputDir.absolutePath}/$safeTitle.%(ext)s"
                val formatString = if (audioFormatId.isNotEmpty()) "$videoFormatId+$audioFormatId" else videoFormatId

                val request = YoutubeDLRequest(url).apply {
                    addOption("-o", template)
                    addOption("-f", formatString)
                    addOption("--merge-output-format", "mp4")
                    addOption("--no-playlist")
                    addOption("--no-mtime")
                    YoutubeDlHelpers.applyAuthAndClient(this, appCtx)
                }

                MediaDownloadManager.videoProcessId = "vid_download_${System.currentTimeMillis()}"
                YoutubeDL.getInstance().execute(request, MediaDownloadManager.videoProcessId) { progress, etaInSeconds, line ->
                    MediaDownloadManager.videoState.value = VideoDownloadState.Downloading(progress, "${etaInSeconds}s", line.ifBlank { "Processing..." })
                    MediaDownloadManager.notificationState.value = NotificationState("Downloading Video", progress, "${etaInSeconds}s", line)
                }

                outputDir.listFiles { f -> f.name.contains(safeTitle) && f.extension.equals("mp4", true) }?.maxByOrNull { it.lastModified() }?.let { file ->
                    MediaScannerConnection.scanFile(appCtx, arrayOf(file.absolutePath), arrayOf("video/mp4"), null)
                }

                MediaDownloadManager.videoState.value = VideoDownloadState.Success("Video saved!")
                refresh()
            } catch (e: Exception) {
                if (e is YoutubeDLException && e.message?.contains("canceled", ignoreCase = true) == true) {
                    MediaDownloadManager.videoState.value = VideoDownloadState.Idle
                } else {
                    MediaDownloadManager.videoState.value = VideoDownloadState.Error("Error: ${e.cause?.message ?: e.message}")
                }
            } finally {
                MediaDownloadManager.videoProcessId = null
                MediaDownloadManager.taskFinished(context)
            }
        }
    }

    fun startPlaylistDownload(context: Context, url: String, formatSelector: String, title: String) {
        MediaDownloadManager.videoState.value = VideoDownloadState.Initializing
        downloadJob?.cancel()

        downloadJob = MediaDownloadManager.applicationScope.launch {
            MediaDownloadManager.taskStarted(context, "Downloading Playlist")
            try {
                val appCtx = context.applicationContext
                if (!isLibraryInitialized) {
                    YoutubeDL.getInstance().init(appCtx)
                    FFmpeg.getInstance().init(appCtx)
                    isLibraryInitialized = true
                }

                val outputDir = currentDir.value ?: MediaPaths.videoDir(create = true)
                val safePlaylistTitle = MediaPaths.sanitizeFileName(title)
                val useDynamicPlaylistTitle = safePlaylistTitle.isBlank() || safePlaylistTitle.equals("Playlist", ignoreCase = true)

                val playlistDir: File?
                val template: String
                val archiveFile: String

                if (useDynamicPlaylistTitle) {
                    playlistDir = null
                    template = "${outputDir.absolutePath}/%(playlist_title)s/%(playlist_index)03d - %(title)s.%(ext)s"
                    archiveFile = File(outputDir, ".playlist_archive.txt").absolutePath
                } else {
                    val dir = File(outputDir, safePlaylistTitle).apply { mkdirs() }
                    playlistDir = dir
                    template = "${dir.absolutePath}/%(playlist_index)03d - %(title)s.%(ext)s"
                    archiveFile = File(dir, ".playlist_archive.txt").absolutePath
                }

                val request = YoutubeDLRequest(url).apply {
                    addOption("-o", template)
                    addOption("-f", formatSelector.ifBlank { "best" })
                    addOption("--merge-output-format", "mp4")
                    addOption("--yes-playlist")
                    addOption("--ignore-errors")
                    addOption("--download-archive", archiveFile)
                    addOption("--no-mtime")
                    YoutubeDlHelpers.applyAuthAndClient(this, appCtx)
                }

                MediaDownloadManager.videoProcessId = "playlist_${System.currentTimeMillis()}"

                YoutubeDL.getInstance().execute(request, MediaDownloadManager.videoProcessId) { progress, etaInSeconds, line ->
                    MediaDownloadManager.videoState.value = VideoDownloadState.Downloading(progress, "${etaInSeconds}s", line.ifBlank { "Downloading playlist..." })
                    MediaDownloadManager.notificationState.value = NotificationState("Downloading Playlist", progress, "${etaInSeconds}s", line)
                }

                val scanRoots = playlistDir?.let { listOf(it) } ?: outputDir.listFiles { f -> f.isDirectory }?.toList().orEmpty()
                scanRoots.flatMap { dir -> dir.listFiles { f -> f.isFile && f.extension.equals("mp4", true) }?.toList().orEmpty() }
                    .forEach { file -> MediaScannerConnection.scanFile(appCtx, arrayOf(file.absolutePath), arrayOf("video/mp4"), null) }

                MediaDownloadManager.videoState.value = VideoDownloadState.Success("Playlist download finished!")
                refresh()
            } catch (e: Exception) {
                if (e is YoutubeDLException && e.message?.contains("canceled", ignoreCase = true) == true) {
                    MediaDownloadManager.videoState.value = VideoDownloadState.Idle
                } else {
                    MediaDownloadManager.videoState.value = VideoDownloadState.Error("Error: ${e.cause?.message ?: e.message}")
                }
            } finally {
                MediaDownloadManager.videoProcessId = null
                MediaDownloadManager.taskFinished(context)
            }
        }
    }

    fun cancelDownload() {
        MediaDownloadManager.applicationScope.launch {
            MediaDownloadManager.videoProcessId?.let {
                try { YoutubeDL.getInstance().destroyProcessById(it) } catch (_: Exception) { }
            }
            MediaDownloadManager.videoState.value = VideoDownloadState.Idle
        }
    }
}
