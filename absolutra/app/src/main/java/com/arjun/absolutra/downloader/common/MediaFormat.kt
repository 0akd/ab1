package com.arjun.absolutra.downloader.common

/**
 * Shared stream format descriptor used by video download and frame extraction.
 */
data class MediaFormat(
    val formatId: String,
    val ext: String,
    val resolution: String,
    val note: String,
    val sizeStr: String,
)
