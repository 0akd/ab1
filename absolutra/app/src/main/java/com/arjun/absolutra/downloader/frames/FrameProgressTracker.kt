package com.arjun.absolutra.downloader.frames

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * In-session "frames viewed" tracker. Standalone — no todo backend.
 */
object FrameProgressTracker {

    data class Binding(
        val taskId: Int,
        val baseline: Int,
        val targetValue: Int,
        val linkedPaths: List<String>
    )

    private val sessionViewed = ConcurrentHashMap<Int, MutableSet<String>>()
    private val sessionIncrements = ConcurrentHashMap<Int, Int>()
    private val _live = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val liveProgress: StateFlow<Map<Int, Int>> = _live.asStateFlow()

    fun sessionSet(taskId: Int): Set<String> =
        sessionViewed[taskId]?.toSet() ?: emptySet()

    fun currentCount(taskId: Int, baseline: Int): Int =
        baseline + (sessionIncrements[taskId] ?: 0)

    suspend fun resolve(context: Context, folders: List<File>): Binding? = null

    fun countFramesUnder(paths: List<String>): Int {
        var n = 0
        for (p in paths) {
            val root = File(p)
            if (!root.exists()) continue
            n += FrameCountHelper.countFramesParallel(root)
        }
        return n
    }

    suspend fun loadTargetFromCache(context: Context, taskId: Int): Int = 0

    fun markViewed(
        taskId: Int,
        path: String,
        baseline: Int,
        onUpdated: (viewed: Int) -> Unit = {}
    ): Boolean {
        val set = sessionViewed.getOrPut(taskId) { ConcurrentHashMap.newKeySet() }
        val isNew = set.add(path)
        if (isNew) {
            sessionIncrements[taskId] = (sessionIncrements[taskId] ?: 0) + 1
        }
        val viewed = currentCount(taskId, baseline)
        _live.value = _live.value + (taskId to viewed)
        onUpdated(viewed)
        return isNew
    }

    suspend fun flush(context: Context, taskId: Int, baseline: Int, totalFrames: Int = 0) {
        // Local-only: progress lives in memory for this process.
    }

    fun clearSession(taskId: Int) {
        sessionViewed.remove(taskId)
        sessionIncrements.remove(taskId)
        _live.value = _live.value - taskId
    }

    fun clearAllSessions() {
        sessionViewed.clear()
        sessionIncrements.clear()
        _live.value = emptyMap()
    }
}
