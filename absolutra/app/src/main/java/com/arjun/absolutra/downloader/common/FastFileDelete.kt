package com.arjun.absolutra.downloader.common

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.arjun.absolutra.AbsolutraApp
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.RecursiveAction
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Industrial-grade bulk deletion for large media trees (thousands of frame JPGs, playlists, etc.).
 *
 * Guarantees (best-effort on modern Android):
 * 1. Instant UI removal via rename-aside (hidden .deleting_ trash).
 * 2. Thorough MediaStore purge (Images + Files collections) so Gallery / MediaStore never resurrects entries.
 * 3. Parallel filesystem wipe (NIO on API 26+, ForkJoin otherwise).
 * 4. Deepest-first directory cleanup + final root force-delete.
 * 5. Progress callbacks for UI (current/total + live message).
 * 6. Cancellation support.
 *
 * Why folders "reappear":
 * - MediaStore still holds DATA / RELATIVE_PATH rows → scanner or other apps re-index.
 * - Partial FS delete leaves residual files → next listFiles sees them.
 * - Trash rename never completed or background job killed → .deleting_ visible or original restored by scan.
 * This class attacks all three.
 */
object FastFileDelete {

    const val DELETING_PREFIX = ".deleting_"
    private const val TAG = "FastFileDelete"

    fun isTrashName(name: String): Boolean =
        name.startsWith(DELETING_PREFIX) || name.startsWith("temp_")

    /**
     * Move [src] aside so the UI can drop it immediately, then wipe contents in parallel.
     * Returns the trash path actually deleted (or null if nothing was done).
     */
    fun renameAside(src: File): File? {
        if (!src.exists()) return null
        val parent = src.parentFile ?: return src
        val trash = File(parent, "$DELETING_PREFIX${System.nanoTime()}_${src.name.hashCode()}")
        return if (src.renameTo(trash)) trash else src
    }

    /**
     * Completely erase one or more roots with progress + cancel.
     * Call from IO dispatcher / applicationScope.
     *
     * @param roots absolute paths that must disappear (files or directories)
     * @param onProgress (deletedSoFar, totalFiles, message) – called on calling thread (usually IO)
     * @param isCancelled checked frequently; when true, stop ASAP (partial cleanup is still performed)
     */
    fun deleteCompletely(
        context: Context,
        roots: Collection<File>,
        onProgress: (current: Int, total: Int, message: String) -> Unit = { _, _, _ -> },
        isCancelled: () -> Boolean = { false }
    ) {
        if (roots.isEmpty()) return

        val appCtx = context.applicationContext
        val allFiles = ArrayList<File>(4096)
        val allDirs = ArrayList<File>(256)
        val originalPaths = ArrayList<String>(roots.size)

        // Phase 0 – collect everything (fast sequential walk, hide trash)
        onProgress(0, 0, "Scanning files…")
        for (root in roots) {
            if (!root.exists()) continue
            originalPaths += root.absolutePath
            if (root.isFile) {
                allFiles += root
            } else {
                collectTree(root, allFiles, allDirs)
            }
        }

        val total = allFiles.size
        if (total == 0 && allDirs.isEmpty()) {
            // Still purge MediaStore for the original paths (ghosts)
            for (p in originalPaths) purgeMediaStoreForPath(appCtx, p)
            return
        }

        onProgress(0, total, "Purging MediaStore index…")

        // Phase 1 – MediaStore first (prevents re-index / Gallery resurrection)
        // Bulk LIKE deletes are cheap; also individual for safety on some OEMs
        for (p in originalPaths) {
            if (isCancelled()) break
            purgeMediaStoreForPath(appCtx, p)
        }
        // Extra pass for every file we are about to unlink (covers partial trees)
        val batchSize = 200
        for (i in allFiles.indices step batchSize) {
            if (isCancelled()) break
            val end = minOf(i + batchSize, allFiles.size)
            for (j in i until end) {
                purgeMediaStoreForPath(appCtx, allFiles[j].absolutePath)
            }
            onProgress(0, total, "Purging MediaStore… ${end.coerceAtMost(total)}/$total")
        }

        if (isCancelled()) {
            onProgress(0, total, "Cancelled – cleaning residual…")
            // Still try to finish what we can
        }

        // Phase 2 – parallel file unlinks (the expensive part)
        val deleted = AtomicInteger(0)
        val cancelled = AtomicBoolean(false)

        fun report() {
            val cur = deleted.get()
            onProgress(cur, total, "Deleting files… $cur / $total")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            allFiles.parallelStream().forEach { file ->
                if (isCancelled() || cancelled.get()) {
                    cancelled.set(true)
                    return@forEach
                }
                try {
                    Files.deleteIfExists(file.toPath())
                } catch (_: Exception) {
                    file.delete()
                }
                val cur = deleted.incrementAndGet()
                if (cur % 25 == 0 || cur == total) report()
            }
        } else {
            // ForkJoin for older devices
            val pool = ForkJoinPool.commonPool()
            val tasks = allFiles.map { f ->
                object : RecursiveAction() {
                    override fun compute() {
                        if (isCancelled() || cancelled.get()) {
                            cancelled.set(true)
                            return
                        }
                        f.delete()
                        val cur = deleted.incrementAndGet()
                        if (cur % 25 == 0 || cur == total) report()
                    }
                }
            }
            for (i in 1 until tasks.size) tasks[i].fork()
            if (tasks.isNotEmpty()) tasks[0].invoke()
            for (i in 1 until tasks.size) tasks[i].join()
        }

        // Phase 3 – directories deepest-first (must be ordered)
        onProgress(total, total, "Removing folders…")
        allDirs.sortedByDescending { it.absolutePath.length }.forEach { dir ->
            if (isCancelled()) return@forEach
            try {
                dir.delete()
            } catch (_: Exception) {
                dir.deleteRecursively()
            }
        }

        // Phase 4 – force original roots + any leftover trash
        for (root in roots) {
            if (root.exists()) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        deleteWithNio(root)
                    } else {
                        root.deleteRecursively()
                    }
                } catch (_: Exception) {
                    root.deleteRecursively()
                }
            }
        }

        // Final MediaStore sweep (catches anything the parallel phase missed)
        for (p in originalPaths) {
            purgeMediaStoreForPath(appCtx, p)
        }

        onProgress(total, total, if (isCancelled()) "Cancelled" else "Done")
        Log.d(TAG, "deleteCompletely finished roots=${roots.size} files=$total cancelled=${isCancelled()}")
    }

    /** Classic fast recursive for callers that still want the old API. */
    fun deleteRecursivelyFast(root: File) {
        if (!root.exists()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                deleteWithNio(root)
            } else {
                ForkJoinPool.commonPool().invoke(DeleteTask(root))
            }
        } catch (_: Exception) {
            root.deleteRecursively()
        }
        if (root.exists()) root.deleteRecursively()
    }

    private fun collectTree(dir: File, files: MutableList<File>, dirs: MutableList<File>) {
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (isTrashName(child.name)) continue // never touch other trash
            if (child.isDirectory) {
                collectTree(child, files, dirs)
                dirs += child
            } else {
                files += child
            }
        }
        dirs += dir
    }

    private fun deleteWithNio(root: File) {
        val rootPath = root.toPath()
        val files = ArrayList<java.nio.file.Path>(1024)
        val dirs = ArrayList<java.nio.file.Path>(64)
        try {
            Files.walk(rootPath).use { stream ->
                stream.forEach { path ->
                    if (Files.isDirectory(path)) dirs.add(path) else files.add(path)
                }
            }
        } catch (_: Exception) {
            root.deleteRecursively()
            return
        }
        files.parallelStream().forEach { path ->
            try {
                Files.deleteIfExists(path)
            } catch (_: Exception) {
                path.toFile().delete()
            }
        }
        dirs.sortedByDescending { it.nameCount }.forEach { path ->
            try {
                Files.deleteIfExists(path)
            } catch (_: Exception) {
                path.toFile().delete()
            }
        }
    }

    /** ForkJoin delete for API < 26. */
    private class DeleteTask(private val dir: File) : RecursiveAction() {
        override fun compute() {
            if (!dir.exists()) return
            if (dir.isFile) {
                dir.delete()
                return
            }
            val children = dir.listFiles() ?: emptyArray()
            val subdirs = ArrayList<File>()
            for (child in children) {
                if (child.isDirectory) subdirs.add(child)
                else child.delete()
            }
            when {
                subdirs.isEmpty() -> Unit
                subdirs.size == 1 -> DeleteTask(subdirs[0]).invoke()
                else -> {
                    val tasks = subdirs.map { DeleteTask(it) }
                    for (i in 1 until tasks.size) tasks[i].fork()
                    tasks[0].invoke()
                    for (i in 1 until tasks.size) tasks[i].join()
                }
            }
            dir.delete()
        }
    }

    /**
     * Remove MediaStore rows for images / files under [absolutePath] (file or directory prefix).
     * Uses both Images and Files collections + DATA + RELATIVE_PATH for maximum coverage across OEMs.
     * Best-effort — failures are ignored.
     */
    fun purgeMediaStoreForPath(absolutePath: String) {
        if (!AbsolutraApp.isInitialized) return
        purgeMediaStoreForPath(AbsolutraApp.appContext, absolutePath)
    }

    fun purgeMediaStoreForPath(context: Context, absolutePath: String) {
        val cr = context.contentResolver
        val path = absolutePath.trimEnd('/')
        try {
            // 1. Images collection (frames are JPGs)
            @Suppress("DEPRECATION")
            val dataCol = MediaStore.Images.Media.DATA
            val selection = "$dataCol = ? OR $dataCol LIKE ?"
            val args = arrayOf(path, "$path/%")
            cr.delete(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, selection, args)

            // Also try RELATIVE_PATH style (Android 10+) for folders under Downloads etc.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val relSelection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
                    // Convert absolute to relative-ish (best effort)
                    val downloads = android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOWNLOADS
                    ).absolutePath
                    if (path.startsWith(downloads)) {
                        val relative = path.removePrefix(downloads).trimStart('/') + "/"
                        cr.delete(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            relSelection,
                            arrayOf("%$relative%")
                        )
                    }
                } catch (_: Exception) { /* ignore */ }
            }

            // 2. General Files collection (covers non-image leftovers, empty dirs sometimes)
            try {
                val filesUri = MediaStore.Files.getContentUri("external")
                @Suppress("DEPRECATION")
                val fileData = MediaStore.Files.FileColumns.DATA
                cr.delete(filesUri, "$fileData = ? OR $fileData LIKE ?", args)
            } catch (_: Exception) { /* some devices restrict Files */ }

        } catch (_: SecurityException) {
            // Missing permission — ignore
        } catch (e: Exception) {
            Log.w(TAG, "purgeMediaStoreForPath failed for $path: ${e.message}")
        }
    }
}
