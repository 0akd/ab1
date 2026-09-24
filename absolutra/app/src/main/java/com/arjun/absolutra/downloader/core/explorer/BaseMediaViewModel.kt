package com.arjun.absolutra.downloader.core.explorer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arjun.absolutra.AbsolutraApp
import com.arjun.absolutra.downloader.common.FastFileDelete
import com.arjun.absolutra.downloader.common.MediaDownloadManager
import com.arjun.absolutra.downloader.common.MediaPaths
import com.arjun.absolutra.downloader.common.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Shared deletion progress exposed to any GenericMediaExplorer.
 * UI observes this and shows a non-dismissible progress dialog.
 */
data class DeletionProgress(
    val isActive: Boolean = false,
    val current: Int = 0,
    val total: Int = 0,
    val message: String = "",
    val canCancel: Boolean = true
)

open class BaseMediaViewModel(val mediaType: MediaType) : ViewModel() {
    private val _currentDir = MutableStateFlow<File?>(null)
    val currentDir: StateFlow<File?> = _currentDir.asStateFlow()

    private val _items = MutableStateFlow<List<File>>(emptyList())
    val items: StateFlow<List<File>> = _items.asStateFlow()

    private val _deletionProgress = MutableStateFlow(DeletionProgress())
    val deletionProgress: StateFlow<DeletionProgress> = _deletionProgress.asStateFlow()

    private var deleteJob: Job? = null
    private val cancelRequested = AtomicBoolean(false)

    // Scroll position per directory path (index to offset) — survives viewer open/close
    private val scrollPositions = mutableMapOf<String, Pair<Int, Int>>()

    fun saveScrollPosition(path: String, index: Int, offset: Int) {
        scrollPositions[path] = index to offset
    }

    fun getScrollPosition(path: String): Pair<Int, Int> {
        return scrollPositions[path] ?: (0 to 0)
    }

    init {
        refresh()
    }

    private fun isVisibleListing(file: File): Boolean = !FastFileDelete.isTrashName(file.name)

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val isRoot = _currentDir.value == null
            val targetDir = _currentDir.value ?: MediaPaths.getMediaRoot(mediaType)
            val list = mutableListOf<File>()

            // Load items from current directory (hide temp_ / .deleting_ trash)
            targetDir.listFiles()?.filter { isVisibleListing(it) }?.forEach { list.add(it) }

            // If at root, intelligently merge legacy directories
            if (isRoot) {
                val legacyDir = MediaPaths.getLegacyRoot(mediaType)
                legacyDir.listFiles()?.filter { isVisibleListing(it) }?.forEach { legacyFile ->
                    if (!list.any { it.absolutePath == legacyFile.absolutePath }) {
                        list.add(legacyFile)
                    }
                }
            }

            // Sort logic: Folders on top, alphabetical order
            _items.value = list.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        }
    }

    fun navigateInto(dir: File) {
        _currentDir.value = dir
        refresh()
    }

    fun navigateUp() {
        val current = _currentDir.value ?: return
        val root = MediaPaths.getMediaRoot(mediaType)

        if (current.absolutePath == root.absolutePath || current.absolutePath == MediaPaths.getLegacyRoot(mediaType).absolutePath) {
            _currentDir.value = null
        } else {
            val parent = current.parentFile
            if (parent != null && parent.absolutePath.startsWith(root.absolutePath)) {
                _currentDir.value = parent
            } else {
                _currentDir.value = null
            }
        }
        refresh()
    }

    /**
     * Robust, progressive, completely-erasing delete.
     *
     * 1. Instantly remove from UI list (optimistic).
     * 2. Rename large folders aside → UI never sees them again.
     * 3. Launch industrial deleteCompletely on applicationScope (survives navigation / process death better).
     * 4. Live progress → DeletionProgress StateFlow → popup in GenericMediaExplorer.
     * 5. Final MediaStore purge + FrameCountHelper invalidation (overridden in frames VM).
     */
    open fun deleteItems(files: Set<File>) {
        if (files.isEmpty()) return
        if (_deletionProgress.value.isActive) return // already deleting

        cancelRequested.set(false)
        deleteJob?.cancel()

        // Instant UI: remove from the current listing before any heavy I/O
        val removedPaths = files.map { it.absolutePath }.toSet()
        _items.value = _items.value.filter { it.absolutePath !in removedPaths }

        _deletionProgress.value = DeletionProgress(
            isActive = true,
            current = 0,
            total = 0,
            message = "Preparing…",
            canCancel = true
        )

        // Heavy work on applicationScope so it continues even if user leaves the screen
        deleteJob = MediaDownloadManager.applicationScope.launch(Dispatchers.IO) {
            val heavyTargets = ArrayList<File>(files.size)
            val lightFiles = ArrayList<File>()

            for (src in files) {
                if (!src.exists()) continue
                if (src.isFile) {
                    lightFiles += src
                } else {
                    // Rename aside first → original name disappears from any concurrent listFiles
                    val trash = FastFileDelete.renameAside(src) ?: src
                    heavyTargets += trash
                }
            }

            // Purge MediaStore against ORIGINAL paths first (index still points here after rename)
            for (orig in removedPaths) {
                FastFileDelete.purgeMediaStoreForPath(orig)
            }

            // Light single-file deletes are instant
            for (f in lightFiles) {
                f.delete()
                FastFileDelete.purgeMediaStoreForPath(f.absolutePath)
            }

            // Confirm listing is clean
            refresh()

            if (heavyTargets.isEmpty()) {
                // Only light files (or nothing left) — already wiped + purged above
                _deletionProgress.value = DeletionProgress()
                return@launch
            }

            // Industrial wipe of renamed trash trees with live progress
            FastFileDelete.deleteCompletely(
                context = MyApplicationSafeContext(),
                roots = heavyTargets,
                onProgress = { current, total, message ->
                    if (!cancelRequested.get()) {
                        _deletionProgress.value = DeletionProgress(
                            isActive = true,
                            current = current,
                            total = maxOf(total, 1),
                            message = message,
                            canCancel = true
                        )
                    }
                },
                isCancelled = { cancelRequested.get() }
            )

            // Final safety
            for (t in heavyTargets) {
                if (t.exists()) {
                    FastFileDelete.deleteRecursivelyFast(t)
                    FastFileDelete.purgeMediaStoreForPath(t.absolutePath)
                }
            }
            for (orig in removedPaths) {
                FastFileDelete.purgeMediaStoreForPath(orig)
            }

            refresh()
            _deletionProgress.value = DeletionProgress() // hide dialog
        }
    }

    fun cancelDeletion() {
        cancelRequested.set(true)
        _deletionProgress.value = _deletionProgress.value.copy(
            message = "Cancelling…",
            canCancel = false
        )
    }

    fun createFolder(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val parent = _currentDir.value ?: MediaPaths.getMediaRoot(mediaType)
            File(parent, MediaPaths.sanitizeFileName(name)).mkdirs()
            refresh()
        }
    }

    fun copyItems(files: Set<File>, dest: File) {
        viewModelScope.launch(Dispatchers.IO) {
            files.forEach { src ->
                var target = File(dest, src.name)
                // Avoid collision
                if (target.exists()) {
                    target = File(dest, "${src.nameWithoutExtension}_copy${if (src.extension.isNotEmpty()) ".${src.extension}" else ""}")
                }
                src.copyRecursively(target, overwrite = true)
            }
            refresh()
        }
    }

    fun moveItems(files: Set<File>, dest: File) {
        viewModelScope.launch(Dispatchers.IO) {
            files.forEach { src ->
                val target = File(dest, src.name)
                // Skip if moving to identical location
                if (target.absolutePath == src.absolutePath) return@forEach

                if (!src.renameTo(target)) {
                    src.copyRecursively(target, overwrite = true)
                    src.deleteRecursively()
                }
            }
            refresh()
        }
    }

    fun getAllFolders(): List<File> {
        val root = MediaPaths.getMediaRoot(mediaType)
        val result = mutableListOf(root)
        fun walk(dir: File) {
            dir.listFiles()?.forEach {
                if (it.isDirectory && isVisibleListing(it)) {
                    result.add(it)
                    walk(it)
                }
            }
        }
        walk(root)
        return result
    }

    /**
     * Safe context helper – MediaDownloadManager.applicationScope may outlive Activity,
     * so we never capture a destroyed Context.
     */
    private fun MyApplicationSafeContext(): android.content.Context {
        return try {
            AbsolutraApp.appContext
        } catch (_: Exception) {
            // Fallback – should never happen once Application is created
            throw IllegalStateException("Application context unavailable during deletion")
        }
    }
}
