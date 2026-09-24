package com.arjun.absolutra.downloader.frames

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.arjun.absolutra.downloader.common.FastFileDelete
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.RecursiveTask

/**
 * Fast frame counting for extracted JPG folders.
 *
 * Strategy:
 * 1. In-memory cache keyed by absolute path + directory mtime
 * 2. MediaStore index query (near-instant when scanned)
 * 3. Parallel filesystem walk fallback when MediaStore returns 0
 */
object FrameCountHelper {

    private val countCache = ConcurrentHashMap<String, Pair<Long, Int>>() // path → (mtime, count)
    /** Paths that must use FS walk next time (e.g. after local delete; MediaStore may be stale). */
    private val forceFsPaths = ConcurrentHashMap.newKeySet<String>()

    fun getFrameCount(context: Context, dir: File): Int {
        if (!dir.isDirectory) return 0
        val key = dir.absolutePath
        val mtime = dir.lastModified()
        countCache[key]?.let { (cachedMtime, count) ->
            if (cachedMtime == mtime && !forceFsPaths.contains(key)) return count
        }
        val forceFs = forceFsPaths.remove(key)
        val count = if (forceFs) {
            // Local mutation — trust disk over MediaStore index
            countFramesParallel(dir)
        } else {
            // MediaStore first (index is far faster than walking the tree)
            var c = countFramesMediaStore(context, dir)
            // Fall back to FS when MediaStore has not indexed yet (or folder is empty — FS is cheap then)
            if (c == 0) c = countFramesParallel(dir)
            c
        }
        countCache[key] = dir.lastModified() to count
        return count
    }

    /** Seed / update the cache after extraction when the count is already known. */
    fun putCount(dir: File, count: Int) {
        if (!dir.exists()) return
        val key = dir.absolutePath
        forceFsPaths.remove(key)
        countCache[key] = dir.lastModified() to count
    }

    fun invalidate(path: String) {
        countCache.remove(path)
        forceFsPaths.add(path)
    }

    fun invalidate(dir: File) {
        invalidate(dir.absolutePath)
    }

    /**
     * Drop cache for deleted items and up to two ancestor folders
     * (video folder → playlist folder → frames root listing).
     */
    fun invalidateTree(files: Collection<File>) {
        for (file in files) {
            invalidate(file.absolutePath)
            file.parentFile?.let { parent ->
                invalidate(parent.absolutePath)
                parent.parentFile?.let { invalidate(it.absolutePath) }
            }
        }
    }

    fun invalidateAll() {
        countCache.clear()
        forceFsPaths.clear()
    }

    /**
     * Recursive MediaStore query under [dir] (includes all subfolders).
     * Relies on prior MediaScannerConnection.scanFile after extraction.
     */
    fun countFramesMediaStore(context: Context, dir: File): Int {
        val path = dir.absolutePath
        // DATA is deprecated for writes on Q+ but remains valid for querying existing media
        @Suppress("DEPRECATION")
        val selection = "${MediaStore.Images.Media.DATA} LIKE ?"
        val args = arrayOf("$path/%")
        return try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID),
                selection,
                args,
                null
            )?.use { it.count } ?: 0
        } catch (_: SecurityException) {
            0
        } catch (_: Exception) {
            0
        }
    }

    /**
     * Parallel directory walk across available cores.
     * Used when MediaStore is empty / not yet scanned.
     */
    fun countFramesParallel(dir: File): Int {
        if (!dir.isDirectory) return 0
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                countFramesNio(dir)
            } else {
                ForkJoinPool.commonPool().invoke(CountFramesTask(dir))
            }
        } catch (_: Exception) {
            countFramesRecursive(dir)
        }
    }

    private fun countFramesNio(dir: File): Int {
        Files.walk(dir.toPath()).use { stream ->
            return stream
                .parallel()
                .filter { path ->
                    val name = path.fileName?.toString() ?: return@filter false
                    if (!name.endsWith(".jpg", ignoreCase = true)) return@filter false
                    // Skip trash / temp trees left by rename-first delete or in-progress extraction
                    val pathStr = path.toString()
                    if (pathStr.contains("${File.separator}temp_") ||
                        pathStr.contains("${File.separator}${FastFileDelete.DELETING_PREFIX}")
                    ) return@filter false
                    Files.isRegularFile(path)
                }
                .count()
                .toInt()
        }
    }

    private fun countFramesRecursive(dir: File): Int {
        var c = 0
        dir.listFiles()?.forEach { f ->
            if (f.isDirectory && !FastFileDelete.isTrashName(f.name)) c += countFramesRecursive(f)
            else if (f.isFile && f.extension.equals("jpg", true)) c++
        }
        return c
    }

    /** ForkJoin recursive task for API < 26. */
    private class CountFramesTask(private val dir: File) : RecursiveTask<Int>() {
        override fun compute(): Int {
            val files = dir.listFiles() ?: return 0
            val subdirs = ArrayList<File>()
            var local = 0
            for (f in files) {
                if (f.isDirectory && !FastFileDelete.isTrashName(f.name)) {
                    subdirs.add(f)
                } else if (f.isFile && f.extension.equals("jpg", true)) {
                    local++
                }
            }
            if (subdirs.isEmpty()) return local
            if (subdirs.size == 1) return local + CountFramesTask(subdirs[0]).invoke()

            val tasks = subdirs.map { CountFramesTask(it) }
            for (i in 1 until tasks.size) tasks[i].fork()
            var sum = local + tasks[0].invoke()
            for (i in 1 until tasks.size) sum += tasks[i].join()
            return sum
        }
    }
}
