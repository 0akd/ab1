package com.arjun.absolutra.downloader.common

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File

/**
 * Shared yt-dlp helpers used by audio, video, and frame tools:
 * optional cookies.txt + smarter client selection.
 *
 * Place a Netscape-format cookies.txt at:
 *   Android/data/<package>/files/cookies.txt
 * (export via "Get cookies.txt LOCALLY" from a logged-in YouTube browser session)
 */
object YoutubeDlHelpers {

    /** Prefer MP4 video-only at 1080 → 720 → 480 → best video. */
    const val VIDEO_ONLY_PRIORITY =
        "bestvideo[ext=mp4][height=1080]/bestvideo[ext=mp4][height=720]/bestvideo[ext=mp4][height=480]/bestvideo"

    fun cookieFile(context: Context): File? {
        val file = File(context.applicationContext.getExternalFilesDir(null), "cookies.txt")
        return file.takeIf { it.isFile && it.length() > 0L }
    }

    /**
     * Applies optional cookies. When cookies exist, skip forced player_client
     * (defaults work better with a logged-in session). Without cookies, use
     * android+mweb as a lighter anti-bot fallback.
     */
    fun applyAuthAndClient(request: YoutubeDLRequest, context: Context) {
        val cookies = cookieFile(context)
        if (cookies != null) {
            request.addOption("--cookies", cookies.absolutePath)
        } else {
            request.addOption("--extractor-args", "youtube:player_client=android,mweb")
        }
    }
}
