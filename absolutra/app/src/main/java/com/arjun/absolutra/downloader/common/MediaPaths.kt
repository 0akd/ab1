package com.arjun.absolutra.downloader.common

import android.content.Context
import android.os.Environment
import com.arjun.absolutra.AbsolutraApp
import java.io.File

enum class MediaType { AUDIO, VIDEO, FRAMES }

object MediaPaths {

    const val ROOT_NAME = "Media Downloads"
    const val AUDIO = "Audio"
    const val VIDEO = "Video"
    const val FRAMES = "Frames"
    const val PREFS_NAME = "settings_prefs"
    const val PREF_CUSTOM_MEDIA_ROOT = "custom_media_root"

    private fun getCustomRoot(): File? {
        if (!AbsolutraApp.isInitialized) return null
        val prefs = AbsolutraApp.appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val path = prefs.getString(PREF_CUSTOM_MEDIA_ROOT, null)
        return path?.let { File(it) }
    }

    fun downloadsRoot(): File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    fun mediaRoot(): File = getCustomRoot() ?: File(downloadsRoot(), ROOT_NAME)

    fun audioDir(create: Boolean = false): File = File(mediaRoot(), AUDIO).also { if (create) it.mkdirs() }
    fun videoDir(create: Boolean = false): File = File(mediaRoot(), VIDEO).also { if (create) it.mkdirs() }
    fun framesDir(create: Boolean = false): File = File(mediaRoot(), FRAMES).also { if (create) it.mkdirs() }

    fun framesFolderForTitle(title: String, create: Boolean = false): File {
        val safe = sanitizeFileName(title)
        return File(framesDir(create), safe).also { if (create) it.mkdirs() }
    }

    fun getMediaRoot(type: MediaType): File = when (type) {
        MediaType.AUDIO -> audioDir(true)
        MediaType.VIDEO -> videoDir(true)
        MediaType.FRAMES -> framesDir(true)
    }

    fun getLegacyRoot(type: MediaType): File = when (type) {
        MediaType.AUDIO -> File(downloadsRoot(), "MP3 Downloads")
        MediaType.VIDEO -> File(downloadsRoot(), "Video Downloads")
        MediaType.FRAMES -> File(downloadsRoot(), "Frame Extracts")
    }

    fun sanitizeFileName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "untitled" }

    fun displayPath(vararg segments: String): String {
        val custom = getCustomRoot()
        val rootStr = custom?.absolutePath ?: "Downloads/$ROOT_NAME"
        return listOf(rootStr, *segments).joinToString("/")
    }
}
