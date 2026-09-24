package com.arjun.absolutra.downloader.frames

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.arjun.absolutra.downloader.common.*
import com.arjun.absolutra.downloader.core.explorer.BaseMediaViewModel
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

data class PlaylistEntry(val id: String?, val title: String?, val url: String?, val duration: Long?, val playlistIndex: Int = 0)

sealed class FrameExtractionState {
    object Idle : FrameExtractionState()
    object FetchingFormats : FrameExtractionState()
    data class FormatSelection(val url: String, val title: String, val videoFormats: List<MediaFormat>, val hasCookies: Boolean, val isPlaylist: Boolean = false, val playlistEntries: List<PlaylistEntry> = emptyList()) : FrameExtractionState()
    object Initializing : FrameExtractionState()
    data class DownloadingVideo(val progress: Float, val eta: String, val currentLine: String, val contextInfo: String = "") : FrameExtractionState()
    data class ExtractingFrames(val contextInfo: String = "") : FrameExtractionState()
    data class Success(val folderPath: String) : FrameExtractionState()
    data class Error(val message: String) : FrameExtractionState()
}

class FrameExtractorViewModel : BaseMediaViewModel(MediaType.FRAMES) {
    val extractionState: StateFlow<FrameExtractionState> = MediaDownloadManager.frameState.asStateFlow()
    private var processJob: Job? = null
    private var countJob: Job? = null

    /** Precomputed JPG counts for the current directory listing (path → count). */
    private val _frameCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val frameCounts: StateFlow<Map<String, Int>> = _frameCounts.asStateFlow()

    companion object {
        private var isYoutubeDlInitialized = false
        private val PLAYLIST_PRESETS = listOf(
            MediaFormat("bestvideo[height<=1080][ext=mp4]/best[ext=mp4]", "mp4", "1080p", "Best ≤1080p", "auto"),
            MediaFormat("bestvideo[height<=720][ext=mp4]/best[ext=mp4]", "mp4", "720p", "Best ≤720p", "auto"),
            MediaFormat("bestvideo[height<=480][ext=mp4]/best[ext=mp4]", "mp4", "480p", "Best ≤480p", "auto")
        )
    }

    /**
     * Batch-count frames for visible folders once per listing refresh.
     * Uses MediaStore + cache so the LazyColumn never walks the filesystem per row.
     */
    fun loadFrameCounts(context: Context, files: List<File>) {
        countJob?.cancel()
        val dirs = files.filter { it.isDirectory }
        if (dirs.isEmpty()) {
            _frameCounts.value = emptyMap()
            return
        }
        countJob = viewModelScope.launch(Dispatchers.IO) {
            val appCtx = context.applicationContext
            // Keep prior counts for paths still visible so rows don't flash "Scanning..." on refresh
            val map = LinkedHashMap<String, Int>(_frameCounts.value.filterKeys { key ->
                dirs.any { it.absolutePath == key }
            })
            if (_frameCounts.value !== map) _frameCounts.value = map
            for (dir in dirs) {
                if (!isActive) return@launch
                // Helper is cheap on cache hit (path + mtime); always call so extract/delete stay accurate
                val count = FrameCountHelper.getFrameCount(appCtx, dir)
                if (map[dir.absolutePath] != count) {
                    map[dir.absolutePath] = count
                    _frameCounts.value = LinkedHashMap(map)
                }
            }
        }
    }

    override fun deleteItems(files: Set<File>) {
        FrameCountHelper.invalidateTree(files)
        // Drop counts immediately so rows don't briefly show stale numbers
        val removed = files.map { it.absolutePath }.toSet()
        _frameCounts.value = _frameCounts.value.filterKeys { it !in removed }
        super.deleteItems(files)
    }

    fun extractFromPdf(context: Context, pdfUri: Uri, fileName: String) {
        MediaDownloadManager.frameState.value = FrameExtractionState.Initializing
        processJob?.cancel()

        processJob = MediaDownloadManager.applicationScope.launch(Dispatchers.IO) {
            MediaDownloadManager.taskStarted(context, "Extracting PDF")
            MediaDownloadManager.frameProcessId = "pdf_extract_${System.currentTimeMillis()}"
            try {
                val targetDir = currentDir.value ?: MediaPaths.framesDir(create = true)
                val targetFolder = File(targetDir, MediaPaths.sanitizeFileName(fileName)).also { it.mkdirs() }

                val pfd = context.contentResolver.openFileDescriptor(pdfUri, "r") ?: throw Exception("Could not open PDF file")

                pfd.use { fd ->
                    android.graphics.pdf.PdfRenderer(fd).use { pdfRenderer ->
                        val pageCount = pdfRenderer.pageCount
                        var extractedCount = 0

                        for (i in 0 until pageCount) {
                            if (MediaDownloadManager.frameProcessId == "canceled") throw Exception("canceled")
                            val progress = (i.toFloat() / pageCount.toFloat()) * 100f
                            MediaDownloadManager.notificationState.value = NotificationState("Extracting PDF", progress, "", "Saving page ${i + 1} of $pageCount")
                            MediaDownloadManager.frameState.value = FrameExtractionState.DownloadingVideo(progress = progress, eta = "", currentLine = "Saving page ${i + 1} of $pageCount", contextInfo = "PDF: $fileName\nTotal Pages: $pageCount")

                            pdfRenderer.openPage(i).use { page ->
                                val width = (page.width * 2.0f).toInt()
                                val height = (page.height * 2.0f).toInt()
                                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                bitmap.eraseColor(android.graphics.Color.WHITE)
                                page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                                val outFile = File(targetFolder, String.format(Locale.US, "page_%04d.jpg", i + 1))
                                FileOutputStream(outFile).use { fos -> bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos) }
                                bitmap.recycle()
                            }
                            extractedCount++
                        }

                        targetFolder.listFiles { f -> f.extension.equals("jpg", ignoreCase = true) }?.map { it.absolutePath }?.toTypedArray()?.let {
                            MediaScannerConnection.scanFile(context, it, null, null)
                        }
                        FrameCountHelper.putCount(targetFolder, extractedCount)
                        MediaDownloadManager.frameState.value = FrameExtractionState.Success("Extracted $extractedCount pages from PDF")
                        refresh()
                    }
                }
            } catch (e: Exception) {
                if (e.message == "canceled") MediaDownloadManager.frameState.value = FrameExtractionState.Idle
                else MediaDownloadManager.frameState.value = FrameExtractionState.Error("PDF Error: ${e.message}")
            } finally {
                if (MediaDownloadManager.frameProcessId != "canceled") MediaDownloadManager.frameProcessId = null
                MediaDownloadManager.taskFinished(context)
            }
        }
    }

    fun fetchFormats(context: Context, url: String) {
        if (url.isBlank()) {
            MediaDownloadManager.frameState.value = FrameExtractionState.Error("URL cannot be empty")
            return
        }

        MediaDownloadManager.frameState.value = FrameExtractionState.FetchingFormats
        processJob?.cancel()

        processJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val appCtx = context.applicationContext
                if (!isYoutubeDlInitialized) {
                    YoutubeDL.getInstance().init(appCtx)
                    isYoutubeDlInitialized = true
                }

                val hasCookies = YoutubeDlHelpers.cookieFile(appCtx) != null
                val isPlaylist = url.contains("playlist?list=", ignoreCase = true) || (url.contains("list=", ignoreCase = true) && (url.contains("youtube.com", ignoreCase = true) || url.contains("youtu.be", ignoreCase = true)))

                if (isPlaylist) {
                    val (playlistTitle, entries) = fetchPlaylistEntries(appCtx, url)
                    MediaDownloadManager.frameState.value = FrameExtractionState.FormatSelection(url, playlistTitle, PLAYLIST_PRESETS, hasCookies, true, entries)
                    return@launch
                }

                val request = YoutubeDLRequest(url).also { YoutubeDlHelpers.applyAuthAndClient(it, appCtx) }
                val videoInfo = YoutubeDL.getInstance().getInfo(request)
                val videoFormats = videoInfo.formats?.filter { !it.vcodec.isNullOrEmpty() && it.vcodec != "none" }
                    ?.sortedByDescending { it.height }
                    ?.map { MediaFormat(it.formatId ?: "", it.ext ?: "", "${(it.height ?: 0)}p", it.formatNote ?: "", "auto") }
                    ?.distinctBy { it.resolution } ?: emptyList()

                MediaDownloadManager.frameState.value = FrameExtractionState.FormatSelection(url, videoInfo.title ?: "Unknown Video", videoFormats, hasCookies, false, emptyList())
            } catch (e: Exception) {
                MediaDownloadManager.frameState.value = FrameExtractionState.Error("Fetch Error: ${e.cause?.message ?: e.message}")
            }
        }
    }

    private fun fetchPlaylistEntries(appCtx: Context, url: String): Pair<String, List<PlaylistEntry>> {
        val request = YoutubeDLRequest(url).apply {
            addOption("--flat-playlist")
            addOption("--dump-single-json")
            addOption("--no-download")
            YoutubeDlHelpers.applyAuthAndClient(this, appCtx)
        }
        MediaDownloadManager.frameProcessId = "playlist_list_${System.currentTimeMillis()}"
        val response = YoutubeDL.getInstance().execute(request, MediaDownloadManager.frameProcessId, null)
        val root = JSONObject(response.out.trim())
        val playlistTitle = root.optString("title").ifBlank { "Extracted_Playlist" }
        val entriesArr = root.optJSONArray("entries") ?: JSONArray()
        val entries = mutableListOf<PlaylistEntry>()

        for (i in 0 until entriesArr.length()) {
            val obj = entriesArr.optJSONObject(i) ?: continue
            val id = obj.optString("id").takeIf { it.isNotBlank() }
            val title = obj.optString("title").takeIf { it.isNotBlank() }
            val entryUrl = sequenceOf("url", "webpage_url", "original_url").map { obj.optString(it).takeIf { it.isNotBlank() } }.firstOrNull { it != null }
            val duration = obj.optLong("duration", -1L).takeIf { it > 0 }
            entries += PlaylistEntry(id, title, entryUrl, duration, i + 1)
        }
        return playlistTitle to entries
    }

    fun startExtraction(context: Context, url: String, videoFormatId: String, title: String, intervalSeconds: Int, isPlaylist: Boolean, selectedEntries: List<PlaylistEntry> = emptyList()) {
        MediaDownloadManager.frameState.value = FrameExtractionState.Initializing
        processJob?.cancel()

        processJob = MediaDownloadManager.applicationScope.launch {
            MediaDownloadManager.taskStarted(context, "Extracting Frames")
            try {
                val appCtx = context.applicationContext
                var totalFrames = 0
                val rootDir = currentDir.value ?: MediaPaths.framesDir(create = true)

                if (isPlaylist) {
                    val playlistDir = File(rootDir, MediaPaths.sanitizeFileName(title)).also { it.mkdirs() }
                    for ((index, entry) in selectedEntries.withIndex()) {
                        if (MediaDownloadManager.frameProcessId == "canceled") return@launch
                        val videoTitle = entry.title?.ifBlank { null } ?: "Video_${entry.playlistIndex}"
                        val videoUrl = entry.url ?: entry.id?.let { "https://www.youtube.com/watch?v=$it" } ?: continue
                        val safeVideoTitle = String.format(Locale.US, "%03d - %s", entry.playlistIndex, MediaPaths.sanitizeFileName(videoTitle))
                        val targetFolder = File(playlistDir, safeVideoTitle)
                        val contextInfo = "Video ${index + 1} of ${selectedEntries.size}:\n$videoTitle"

                        var shouldExtract = true
                        if (targetFolder.exists()) {
                            val existingFrames = targetFolder.list { _, name -> name.endsWith(".jpg", true) }?.size ?: 0
                            if (entry.duration != null && entry.duration > 0 && existingFrames >= (entry.duration / intervalSeconds) - 2) {
                                shouldExtract = false
                                totalFrames += existingFrames
                                FrameCountHelper.putCount(targetFolder, existingFrames)
                            } else if (existingFrames > 0) {
                                shouldExtract = false
                                totalFrames += existingFrames
                                FrameCountHelper.putCount(targetFolder, existingFrames)
                            }
                            if (shouldExtract) {
                                targetFolder.deleteRecursively()
                                FrameCountHelper.invalidate(targetFolder)
                            }
                        }

                        if (!shouldExtract) continue

                        targetFolder.mkdirs()
                        MediaDownloadManager.frameProcessId = "extract_${System.currentTimeMillis()}"
                        try {
                            val extracted = processSingleVideo(appCtx, videoUrl, videoTitle, videoFormatId, intervalSeconds, targetFolder, contextInfo, MediaDownloadManager.frameProcessId!!)
                            totalFrames += extracted
                            FrameCountHelper.putCount(targetFolder, extracted)
                        } catch (e: Exception) {
                            if (e is YoutubeDLException && e.message?.contains("canceled", ignoreCase = true) == true) throw e
                        }
                    }
                    // Playlist folder total changed — drop stale recursive count
                    FrameCountHelper.invalidate(playlistDir)
                    FrameCountHelper.putCount(playlistDir, totalFrames)
                    MediaDownloadManager.frameState.value = FrameExtractionState.Success("Extracted $totalFrames frames across ${selectedEntries.size} videos")
                } else {
                    val targetFolder = File(rootDir, MediaPaths.sanitizeFileName(title)).also { it.mkdirs() }
                    MediaDownloadManager.frameProcessId = "extract_${System.currentTimeMillis()}"
                    totalFrames = processSingleVideo(appCtx, url, title, videoFormatId, intervalSeconds, targetFolder, "", MediaDownloadManager.frameProcessId!!)
                    FrameCountHelper.putCount(targetFolder, totalFrames)
                    MediaDownloadManager.frameState.value = FrameExtractionState.Success("Extracted $totalFrames frames")
                }
                refresh()
            } catch (e: Exception) {
                if (e is YoutubeDLException && e.message?.contains("canceled", ignoreCase = true) == true) MediaDownloadManager.frameState.value = FrameExtractionState.Idle
                else MediaDownloadManager.frameState.value = FrameExtractionState.Error("Error: ${e.cause?.message ?: e.message}")
            } finally {
                MediaDownloadManager.frameProcessId = null
                MediaDownloadManager.taskFinished(context)
            }
        }
    }

    private fun processSingleVideo(appCtx: Context, url: String, title: String, videoFormatId: String, intervalSeconds: Int, targetFolder: File, contextInfo: String, processId: String): Int {
        val tempDir = File(targetFolder.parentFile, "temp_${System.currentTimeMillis()}").apply { mkdirs() }
        val request = YoutubeDLRequest(url).apply {
            addOption("-o", "${tempDir.absolutePath}/%(title)s.%(ext)s")
            addOption("-f", if (videoFormatId.isNotBlank()) "$videoFormatId/${YoutubeDlHelpers.VIDEO_ONLY_PRIORITY}" else YoutubeDlHelpers.VIDEO_ONLY_PRIORITY)
            addOption("--no-playlist")
            YoutubeDlHelpers.applyAuthAndClient(this, appCtx)
        }

        MediaDownloadManager.frameState.value = FrameExtractionState.DownloadingVideo(0f, "Calculating...", "Starting download...", contextInfo)
        YoutubeDL.getInstance().execute(request, processId) { progress, etaInSeconds, line ->
            MediaDownloadManager.frameState.value = FrameExtractionState.DownloadingVideo(progress, "${etaInSeconds}s", line.ifBlank { "Downloading video..." }, contextInfo)
        }

        val downloadedVideo = tempDir.listFiles()?.firstOrNull { it.isFile } ?: throw Exception("Video download failed")
        MediaDownloadManager.frameState.value = FrameExtractionState.ExtractingFrames(contextInfo)

        val retriever = MediaMetadataRetriever()
        var frameCount = 0
        try {
            retriever.setDataSource(downloadedVideo.absolutePath)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val intervalMs = intervalSeconds * 1000L
            var currentTimeMs = 0L

            while (currentTimeMs < durationMs) {
                if (MediaDownloadManager.frameProcessId == "canceled") throw YoutubeDLException("canceled")
                val pct = ((currentTimeMs.toFloat() / durationMs.toFloat()) * 100f).coerceIn(0f, 100f)
                MediaDownloadManager.notificationState.value = NotificationState("Slicing Frames", pct, "Estimating...", "$contextInfo\nProcessing Frame $frameCount")

                retriever.getFrameAtTime(currentTimeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)?.let { bitmap ->
                    frameCount++
                    FileOutputStream(File(targetFolder, String.format(Locale.US, "frame_%04d.jpg", frameCount))).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                    bitmap.recycle()
                }
                currentTimeMs += intervalMs
            }
        } finally {
            retriever.release()
        }

        tempDir.deleteRecursively()
        targetFolder.listFiles { f -> f.extension.equals("jpg", ignoreCase = true) }?.map { it.absolutePath }?.toTypedArray()?.let {
            MediaScannerConnection.scanFile(appCtx, it, null, null)
        }
        return frameCount
    }

    fun cancelProcess() {
        MediaDownloadManager.applicationScope.launch {
            MediaDownloadManager.frameProcessId?.let { try { YoutubeDL.getInstance().destroyProcessById(it) } catch (_: Exception) { } }
            MediaDownloadManager.frameProcessId = "canceled"
            MediaDownloadManager.frameState.value = FrameExtractionState.Idle
        }
    }
}
