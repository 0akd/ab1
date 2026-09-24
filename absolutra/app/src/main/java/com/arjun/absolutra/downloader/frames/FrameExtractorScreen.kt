package com.arjun.absolutra.downloader.frames

import android.Manifest
import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import androidx.compose.runtime.DisposableEffect
import com.arjun.absolutra.core.datastore.SettingsManager
import com.arjun.absolutra.downloader.common.FastFileDelete
import com.arjun.absolutra.downloader.core.explorer.GenericMediaExplorer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrameExtractorScreen(
    modifier: Modifier = Modifier,
    viewModel: FrameExtractorViewModel = viewModel()
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val scope = rememberCoroutineScope()

    var showExtractSheet by remember { mutableStateOf(false) }
    var selectedFolders by remember { mutableStateOf<List<File>>(emptyList()) }
    var isFullScreenActive by remember { mutableStateOf(false) }
    var activeTaskId by remember { mutableStateOf<Int?>(null) }
    var activeBaseline by remember { mutableIntStateOf(0) }
    var activeTarget by remember { mutableIntStateOf(0) }
    var activeSessionPaths by remember { mutableStateOf<Set<String>>(emptySet()) }

    val currentDir by viewModel.currentDir.collectAsState()
    val items by viewModel.items.collectAsState()
    val frameCounts by viewModel.frameCounts.collectAsState()

    LaunchedEffect(items) {
        viewModel.loadFrameCounts(context, items)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) viewModel.refresh()
    }

    LaunchedEffect(Unit) {
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) permissionLauncher.launch(permission)
        else viewModel.refresh()
    }

    /** Open folders and auto-bind matching todo via linked_folder_paths (works from explorer or Todo). */
    fun openFolders(files: List<File>, preferFullScreen: Boolean = true) {
        if (files.isEmpty()) return
        selectedFolders = files
        isFullScreenActive = preferFullScreen
        scope.launch(Dispatchers.IO) {
            val binding = FrameProgressTracker.resolve(context, files)
            withContext(Dispatchers.Main) {
                if (binding != null) {
                    activeTaskId = binding.taskId
                    activeBaseline = binding.baseline
                    activeTarget = binding.targetValue
                    activeSessionPaths = FrameProgressTracker.sessionSet(binding.taskId)
                } else {
                    activeTaskId = null
                    activeBaseline = 0
                    activeTarget = 0
                    activeSessionPaths = emptySet()
                }
            }
        }
    }

    fun closeFolders() {
        selectedFolders = emptyList()
        activeTaskId = null
        activeBaseline = 0
        activeTarget = 0
        activeSessionPaths = emptySet()
        isFullScreenActive = false
        scope.launch {
            settingsManager.saveFrameState(
                folder = null, index = 0, scale = 1f, offsetX = 0f, offsetY = 0f
            )
        }
        viewModel.refresh()
    }

    BackHandler(enabled = selectedFolders.isNotEmpty()) {
        closeFolders()
    }

    if (showExtractSheet) {
        ModalBottomSheet(onDismissRequest = { showExtractSheet = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            FrameExtractionSheetContent(viewModel = viewModel, onDismissRequest = { showExtractSheet = false })
        }
    }

    if (selectedFolders.isNotEmpty()) {
        // Recompose viewer when async resolve fills in taskId
        key(activeTaskId, activeTarget, selectedFolders.map { it.absolutePath }.joinToString("|")) {
            FrameViewerScreen(
                folders = selectedFolders,
                isFullScreenActive = isFullScreenActive,
                onFullScreenChange = { isFullScreenActive = it },
                onBack = { closeFolders() },
                taskId = activeTaskId,
                initialViewedFrames = activeSessionPaths,
                initialViewedCount = activeBaseline,
                initialTarget = activeTarget
            )
        }
    } else {
        GenericMediaExplorer(
            title = "Extracted Frames",
            viewModel = viewModel,
            onDownloadClick = { showExtractSheet = true },
            topBarActions = {
                if (currentDir != null) {
                    TextButton(onClick = { openFolders(listOf(currentDir!!), preferFullScreen = true) }) {
                        Text("View Frames")
                    }
                }
            },
            onItemClick = { file ->
                if (file.isDirectory) {
                    val isPlaylist = file.listFiles { f ->
                        f.isDirectory && !FastFileDelete.isTrashName(f.name)
                    }?.isNotEmpty() == true
                    if (isPlaylist) {
                        viewModel.navigateInto(file)
                    } else {
                        openFolders(listOf(file), preferFullScreen = true)
                    }
                }
            },
            leadingContent = { file ->
                var isPlaylist by remember(file) { mutableStateOf(false) }
                LaunchedEffect(file) {
                    withContext(Dispatchers.IO) {
                        isPlaylist = file.listFiles { f ->
                            f.isDirectory && !FastFileDelete.isTrashName(f.name)
                        }?.isNotEmpty() == true
                    }
                }
                Box(modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    Text(if (isPlaylist) "🗂️" else "📂", style = MaterialTheme.typography.headlineSmall)
                }
            },
            headlineContent = { file ->
                Text(file.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = { file ->
                val count = frameCounts[file.absolutePath]
                if (count == null) Text("Scanning...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else Text("$count frames", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrameViewerScreen(
    folders: List<File>,
    isFullScreenActive: Boolean,
    onFullScreenChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    taskId: Int? = null,
    initialViewedFrames: Set<String> = emptySet(),
    initialViewedCount: Int = 0,
    initialTarget: Int = 0
) {
    var isLoading by remember { mutableStateOf(true) }
    var framesBySection by remember { mutableStateOf<Map<String, List<File>>>(emptyMap()) }
    var allFrames by remember { mutableStateOf<List<FlatFrame>>(emptyList()) }
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }

    // Survives full-screen ↔ grid toggles (not recreated when isFullScreenActive flips)
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()

    var fullScreenIndex by remember { mutableIntStateOf(0) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var expandedSections by remember { mutableStateOf(setOf<String>()) }
    var showUi by remember { mutableStateOf(true) }

    // Bound task — may arrive from parent or self-resolve via linked_folder_paths
    var boundTaskId by remember { mutableStateOf(taskId) }
    var baselineCount by remember { mutableIntStateOf(initialViewedCount.coerceAtLeast(0)) }
    var taskTarget by remember { mutableIntStateOf(initialTarget.coerceAtLeast(0)) }
    var viewedFrames by remember {
        mutableStateOf(
            taskId?.let { FrameProgressTracker.sessionSet(it) }?.ifEmpty { initialViewedFrames }
                ?: initialViewedFrames
        )
    }
    var showCompletedDialog by remember { mutableStateOf(false) }
    var completionShown by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val folderKey = folders.joinToString("|") { it.absolutePath }

    LaunchedEffect(taskId, folders, initialTarget, initialViewedCount) {
        val binding = if (taskId == null) {
            withContext(Dispatchers.IO) { FrameProgressTracker.resolve(context, folders) }
        } else {
            null
        }

        val resolvedTaskId = taskId ?: binding?.taskId
        if (resolvedTaskId == null) return@LaunchedEffect

        boundTaskId = resolvedTaskId
        baselineCount = initialViewedCount.coerceAtLeast(binding?.baseline ?: 0)
        val session = FrameProgressTracker.sessionSet(resolvedTaskId)
        if (session.isNotEmpty()) viewedFrames = session

        if (initialTarget > 0) {
            taskTarget = initialTarget
        } else if (binding != null && binding.targetValue > 0) {
            taskTarget = binding.targetValue
        }
    }

    fun markViewed(path: String) {
        val tid = boundTaskId ?: return
        val isNew = FrameProgressTracker.markViewed(
            taskId = tid,
            path = path,
            baseline = baselineCount
        )
        if (isNew) {
            viewedFrames = FrameProgressTracker.sessionSet(tid)
            scope.launch(Dispatchers.IO) {
                FrameProgressTracker.flush(context, tid, baselineCount)
            }
        }
    }

    fun forceFlush() {
        val tid = boundTaskId ?: return
        val viewed = FrameProgressTracker.currentCount(tid, baselineCount)
        if (viewed > baselineCount) baselineCount = viewed
        scope.launch(Dispatchers.IO) {
            FrameProgressTracker.flush(context, tid, baselineCount)
        }
    }

    fun handleBack() {
        forceFlush()
        onBack()
    }

    DisposableEffect(boundTaskId, folderKey) {
        onDispose { forceFlush() }
    }

    BackHandler { handleBack() }

    val viewedNow = boundTaskId?.let {
        FrameProgressTracker.currentCount(it, baselineCount)
    } ?: baselineCount
    // Recompose when session set grows
    @Suppress("UNUSED_VARIABLE")
    val viewedRecomposeKey = viewedFrames.size
    val targetNow = taskTarget
    val leftNow = if (targetNow > 0) (targetNow - viewedNow).coerceAtLeast(0) else 0
    val progressFrac = if (targetNow > 0) {
        (viewedNow.toFloat() / targetNow).coerceIn(0f, 1f)
    } else {
        0f
    }

    // Completion once when threshold crossed
    LaunchedEffect(viewedNow, taskTarget, boundTaskId) {
        if (boundTaskId != null && taskTarget > 0 && viewedNow >= taskTarget && !completionShown) {
            completionShown = true
            forceFlush()
            showCompletedDialog = true
        }
    }

    if (showCompletedDialog) {
        AlertDialog(
            onDismissRequest = { /* force explicit action */ },
            title = { Text("Task complete") },
            text = {
                Text("You've viewed $viewedNow / $taskTarget frames.\nThis linked task is finished.")
            },
            confirmButton = {
                Button(onClick = {
                    showCompletedDialog = false
                    forceFlush()
                    onBack()
                }) { Text("Done") }
            }
        )
    }

    LaunchedEffect(folders) {
        isLoading = true
        withContext(Dispatchers.IO) {
            val map = linkedMapOf<String, List<File>>()
            
            for (f in folders) {
                // 1. Root-level frames
                val directFramesArray = f.listFiles { child -> child.isFile && child.extension.equals("jpg", true) }
                if (directFramesArray != null && directFramesArray.isNotEmpty()) {
                    directFramesArray.sortBy { it.name }
                    map[f.name] = directFramesArray.toList()
                }

                // 2. Subfolder frames (playlist layout)
                val subFolders = f.listFiles { child -> child.isDirectory && !FastFileDelete.isTrashName(child.name) }
                if (subFolders != null) {
                    subFolders.sortBy { it.name }
                    for (subFolder in subFolders) {
                        val subFramesArray = subFolder.listFiles { child -> child.isFile && child.extension.equals("jpg", true) }
                        if (subFramesArray != null && subFramesArray.isNotEmpty()) {
                            subFramesArray.sortBy { it.name }
                            map["${f.name}/${subFolder.name}"] = subFramesArray.toList()
                        }
                    }
                }
            }

            // 3. Flatten frames 
            var gIndex = 0
            val gTotal = map.values.sumOf { it.size }
            val flatList = ArrayList<FlatFrame>(gTotal)
            
            for ((section, frames) in map) {
                for ((lIndex, file) in frames.withIndex()) {
                    gIndex++
                    flatList.add(FlatFrame(file, section, lIndex + 1, frames.size, gIndex, gTotal))
                }
            }

            withContext(Dispatchers.Main) {
                framesBySection = map
                allFrames = flatList
                isLoading = false

                val state = settingsManager.frameStateFlow.first()
                if (state.folder == folderKey) {
                    fullScreenIndex = state.index.coerceIn(0, maxOf(0, flatList.lastIndex))
                    scale = state.scale
                    offset = Offset(state.offsetX, state.offsetY)
                }
            }
        }
    }

    // Full-screen index / mode change marks current frame
    LaunchedEffect(fullScreenIndex, isFullScreenActive, allFrames.size, boundTaskId) {
        if (isFullScreenActive && allFrames.isNotEmpty() && boundTaskId != null &&
            fullScreenIndex in allFrames.indices
        ) {
            markViewed(allFrames[fullScreenIndex].file.absolutePath)
        }
    }

    // Debounced flush while swiping (frames_viewed only — never target_value)
    LaunchedEffect(viewedFrames.size, boundTaskId, baselineCount) {
        val tid = boundTaskId ?: return@LaunchedEffect
        delay(350)
        withContext(Dispatchers.IO) {
            FrameProgressTracker.flush(context, tid, baselineCount)
        }
    }

    // Resume position only — progress lives in tracker + todo cache (integer)
    LaunchedEffect(isFullScreenActive, fullScreenIndex, scale, offset, boundTaskId, baselineCount, viewedFrames.size) {
        if (allFrames.isNotEmpty()) {
            delay(500)
            val tid = boundTaskId
            val viewed = if (tid != null) {
                FrameProgressTracker.currentCount(tid, baselineCount)
            } else {
                baselineCount
            }
            settingsManager.saveFrameState(
                folder = folderKey,
                index = fullScreenIndex,
                scale = scale,
                offsetX = offset.x,
                offsetY = offset.y,
                taskId = tid,
                viewedFrames = emptySet(),
                viewedCount = viewed
            )
        }
    }

    if (isFullScreenActive && allFrames.isNotEmpty()) {
        val currentFrameData = allFrames[fullScreenIndex]
        val coroutineScope = rememberCoroutineScope()
        
        var showBrightnessIndicator by remember { mutableStateOf(false) }
        var indicatorValue by remember { mutableFloatStateOf(0f) }

        val activity = remember(context) {
            generateSequence(context) { if (it is ContextWrapper) it.baseContext else null }
                .filterIsInstance<Activity>()
                .firstOrNull()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AsyncImage(
                model = currentFrameData.file,
                contentDescription = "Full screen frame",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
                contentScale = ContentScale.Fit
            )
            
            Spacer(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(allFrames.size) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val startX = down.position.x
                            val startY = down.position.y
                            val screenWidth = size.width.toFloat()
                            val screenHeight = size.height.toFloat()
                            
                            val isLeftThird = startX < screenWidth / 3f
                            val isCenterWidth = startX >= screenWidth / 3f && startX <= screenWidth * 2f / 3f
                            val isCenterHeight = startY >= screenHeight / 3f && startY <= screenHeight * 2f / 3f
                            val isCenterTap = isCenterWidth && isCenterHeight
                            
                            var isBrightnessMode = false
                            var isPanZoomMode = false
                            var accumulatedY = 0f
                            var startBrightness = activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.5f

                            var framesAdvanced = 0
                            
                            val playbackJob = coroutineScope.launch {
                                if (isCenterTap || isLeftThird) return@launch
                                delay(300)
                                var delayMs = 200L
                                val minDelayMs = 16L
                                while (isActive && !isBrightnessMode && !isPanZoomMode) {
                                    if (allFrames.isEmpty()) break
                                    fullScreenIndex = (fullScreenIndex + 1) % allFrames.size
                                    framesAdvanced++
                                    delay(delayMs)
                                    delayMs = (delayMs * 0.8).toLong().coerceAtLeast(minDelayMs)
                                }
                            }

                            do {
                                val event = awaitPointerEvent()
                                val pointers = event.changes
                                
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                
                                if (!isPanZoomMode && !isBrightnessMode) {
                                    val dx = pointers.first().position.x - startX
                                    val dy = pointers.first().position.y - startY
                                    if (Math.abs(dx) > 20f || Math.abs(dy) > 20f || pointers.size > 1) {
                                        playbackJob.cancel()
                                    }
                                }

                                if (pointers.size >= 2) {
                                    isPanZoomMode = true
                                    isBrightnessMode = false
                                    playbackJob.cancel()
                                    
                                    scale = (scale * zoomChange).coerceIn(1f, 10f)
                                    val maxX = (screenWidth * scale - screenWidth) / 2f
                                    val maxY = (screenHeight * scale - screenHeight) / 2f
                                    
                                    val newOffsetX = offset.x + panChange.x * scale
                                    val newOffsetY = offset.y + panChange.y * scale
                                    
                                    offset = Offset(
                                        newOffsetX.coerceIn(-maxX, maxX),
                                        newOffsetY.coerceIn(-maxY, maxY)
                                    )
                                    if (scale == 1f) offset = Offset.Zero
                                    
                                    if (zoomChange != 1f || panChange != Offset.Zero) {
                                        pointers.forEach { if (it.positionChanged()) it.consume() }
                                    }
                                } else if (scale > 1f) {
                                    val dx = pointers.first().position.x - startX
                                    val dy = pointers.first().position.y - startY
                                    
                                    if (!isPanZoomMode && (Math.abs(dx) > 10f || Math.abs(dy) > 10f)) {
                                        isPanZoomMode = true
                                        playbackJob.cancel()
                                    }
                                    
                                    if (isPanZoomMode) {
                                        val maxX = (screenWidth * scale - screenWidth) / 2f
                                        val maxY = (screenHeight * scale - screenHeight) / 2f
                                        
                                        val newOffsetX = offset.x + panChange.x * scale
                                        val newOffsetY = offset.y + panChange.y * scale
                                        
                                        offset = Offset(
                                            newOffsetX.coerceIn(-maxX, maxX),
                                            newOffsetY.coerceIn(-maxY, maxY)
                                        )
                                        if (panChange != Offset.Zero) {
                                            pointers.forEach { if (it.positionChanged()) it.consume() }
                                        }
                                    }
                                } else if (scale == 1f && !isPanZoomMode) {
                                    accumulatedY += panChange.y
                                    if (isLeftThird && Math.abs(accumulatedY) > 30f) {
                                        isBrightnessMode = true
                                        playbackJob.cancel()
                                    }
                                    
                                    if (isBrightnessMode) {
                                        val fraction = -panChange.y / screenHeight
                                        startBrightness = (startBrightness + fraction).coerceIn(0f, 1f)
                                        
                                        activity?.window?.let { win ->
                                            val layoutParams = win.attributes
                                            layoutParams.screenBrightness = startBrightness
                                            win.attributes = layoutParams
                                        }
                                        indicatorValue = startBrightness
                                        showBrightnessIndicator = true
                                        
                                        pointers.first().consume()
                                    }
                                }
                            } while (pointers.any { it.pressed })
                            
                            showBrightnessIndicator = false
                            playbackJob.cancel()

                            if (!isBrightnessMode && !isPanZoomMode && framesAdvanced == 0 && !down.isConsumed) {
                                if (isCenterTap) {
                                    showUi = !showUi
                                } else if (isLeftThird) {
                                    if (allFrames.isNotEmpty()) {
                                        fullScreenIndex = if (fullScreenIndex > 0) fullScreenIndex - 1 else allFrames.lastIndex
                                    }
                                } else {
                                    if (allFrames.isNotEmpty()) {
                                        fullScreenIndex = (fullScreenIndex + 1) % allFrames.size
                                    }
                                }
                            }
                        }
                    }
            )

            AnimatedVisibility(
                visible = showBrightnessIndicator,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(24.dp))
                        .padding(32.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Brightness6,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { indicatorValue },
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .width(140.dp)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
            
            AnimatedVisibility(
                visible = showUi,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp, start = 16.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { handleBack() },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }

                    if (boundTaskId != null && targetNow > 0) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        ) {
                            Text(
                                "$viewedNow / $targetNow  ·  $leftNow left",
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { progressFrac },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color.White.copy(alpha = 0.25f)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    IconButton(
                        onClick = { onFullScreenChange(false) },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.GridView, contentDescription = "Gallery", tint = Color.White)
                    }
                }
            }
        }
    } else {
        val titleText = if (folders.size == 1) folders.first().name else "${folders.size} Folders"
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(titleText, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { handleBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )

            if (boundTaskId != null && targetNow > 0) {
                LinearProgressIndicator(
                    progress = { progressFrac },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                )
                Text(
                    "$viewedNow / $targetNow viewed · $leftNow left",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (framesBySection.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No frames found in this folder.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(minSize = 100.dp),
                    contentPadding = PaddingValues(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    framesBySection.forEach { (sectionName, sectionFrames) ->
                        val isExpanded = expandedSections.contains(sectionName)
                        
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expandedSections = if (isExpanded) {
                                            expandedSections - sectionName
                                        } else {
                                            expandedSections + sectionName
                                        }
                                    }
                                    .padding(horizontal = 4.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "$sectionName (${sectionFrames.size} frames)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        
                        if (isExpanded) {
                            items(
                                count = sectionFrames.size,
                                key = { i -> sectionFrames[i].absolutePath }
                            ) { index ->
                                val frame = sectionFrames[index]
                                AsyncImage(
                                    model = frame,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            // Grid open also counts as viewed (not only full-screen)
                                            markViewed(frame.absolutePath)
                                            val idx = allFrames.indexOfFirst { it.file == frame }
                                            if (idx != -1) {
                                                fullScreenIndex = idx
                                                scale = 1f
                                                offset = Offset.Zero
                                                onFullScreenChange(true)
                                            }
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrameExtractionSheetContent(
    modifier: Modifier = Modifier,
    viewModel: FrameExtractorViewModel,
    onDismissRequest: () -> Unit
) {
    var urlInput by remember { mutableStateOf("") }
    val extractionState by viewModel.extractionState.collectAsState()
    val context = LocalContext.current

    val selectionState = extractionState as? FrameExtractionState.FormatSelection
    var selectedVideoFormat by remember(selectionState) {
        mutableStateOf(selectionState?.videoFormats?.firstOrNull())
    }
    var expandedVideo by remember { mutableStateOf(false) }
    var intervalInput by remember { mutableStateOf("10") }
    var selectedEntries by remember(selectionState) {
        mutableStateOf(selectionState?.playlistEntries?.toSet() ?: emptySet())
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            var name = "Extracted_PDF"
            cursor?.use {
                if (it.moveToFirst()) {
                    val displayNameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (displayNameIndex >= 0) {
                        val str = it.getString(displayNameIndex)
                        if (str != null) name = str.substringBeforeLast(".pdf").ifBlank { "Extracted_PDF" }
                    }
                }
            }
            viewModel.extractFromPdf(context, uri, name)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onDismissRequest) {
                    Icon(Icons.Default.Close, contentDescription = "Close extraction screen")
                }
            }

            Text(
                text = "Extract New Media",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "Extract pages from a PDF document or frames from a YouTube video.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            AnimatedVisibility(visible = extractionState is FrameExtractionState.Idle || extractionState is FrameExtractionState.Success || extractionState is FrameExtractionState.Error) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { pdfPickerLauncher.launch("application/pdf") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("📄", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(8.dp))
                        Text("Select PDF Document", style = MaterialTheme.typography.titleMedium)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(Modifier.weight(1f))
                        Text("  OR YOUTUBE LINK  ", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                        HorizontalDivider(Modifier.weight(1f))
                    }

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("Video or Playlist URL") },
                        placeholder = { Text("https://www.youtube.com/watch?v=...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(
                            onGo = { viewModel.fetchFormats(context, urlInput) }
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { viewModel.fetchFormats(context, urlInput) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        enabled = urlInput.isNotBlank()
                    ) {
                        Text("Fetch Available Qualities", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        when (val state = extractionState) {
            is FrameExtractionState.FetchingFormats -> {
                item {
                    CircularProgressIndicator()
                    Text("Fetching metadata...", modifier = Modifier.padding(top = 8.dp))
                }
            }

            is FrameExtractionState.FormatSelection -> {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {

                        if (state.isPlaylist) {
                            Text(
                                text = "Playlist Detected",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }

                        if (state.hasCookies) {
                            Text(
                                text = "✅ Using cookies.txt (higher quality / fewer blocks)",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        Text("Select Max Video Quality", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                        ExposedDropdownMenuBox(
                            expanded = expandedVideo,
                            onExpandedChange = { expandedVideo = !expandedVideo }
                        ) {
                            OutlinedTextField(
                                readOnly = true,
                                value = selectedVideoFormat?.let { "${it.resolution} - ${it.ext} (${it.sizeStr})" } ?: "No Video Found",
                                onValueChange = { },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth(),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedVideo) }
                            )
                            ExposedDropdownMenu(
                                expanded = expandedVideo,
                                onDismissRequest = { expandedVideo = false }
                            ) {
                                state.videoFormats.forEach { format ->
                                    DropdownMenuItem(
                                        text = { Text("${format.resolution} - ${format.ext} (${format.sizeStr})") },
                                        onClick = {
                                            selectedVideoFormat = format
                                            expandedVideo = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text("Extraction Interval", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                        OutlinedTextField(
                            value = intervalInput,
                            onValueChange = { if (it.isEmpty() || it.all { char -> char.isDigit() }) intervalInput = it },
                            label = { Text("Extract 1 frame every X seconds") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = { Text("sec", modifier = Modifier.padding(end = 12.dp)) }
                        )

                        if (state.isPlaylist) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Select Videos to Extract (${selectedEntries.size} / ${state.playlistEntries.size})",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                TextButton(onClick = { selectedEntries = state.playlistEntries.toSet() }) {
                                    Text("Select All")
                                }
                                TextButton(onClick = { selectedEntries = emptySet() }) {
                                    Text("Deselect All")
                                }
                            }
                        }
                    }
                }

                if (state.isPlaylist) {
                    items(state.playlistEntries) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedEntries = if (selectedEntries.contains(entry)) {
                                        selectedEntries - entry
                                    } else {
                                        selectedEntries + entry
                                    }
                                }
                                .padding(vertical = 4.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedEntries.contains(entry),
                                onCheckedChange = null 
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = entry.title ?: "Unknown Video",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            val interval = intervalInput.toIntOrNull() ?: 10
                            viewModel.startExtraction(
                                context = context,
                                url = state.url,
                                videoFormatId = selectedVideoFormat?.formatId ?: "",
                                title = state.title,
                                intervalSeconds = interval,
                                isPlaylist = state.isPlaylist,
                                selectedEntries = selectedEntries.toList() 
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        enabled = !state.isPlaylist || selectedEntries.isNotEmpty()
                    ) {
                        Text(if (state.isPlaylist) "Extract Selected Videos" else "Start Extraction", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            is FrameExtractionState.Initializing -> {
                item {
                    CircularProgressIndicator()
                    Text("Preparing...", modifier = Modifier.padding(top = 8.dp))
                }
            }

            is FrameExtractionState.DownloadingVideo -> {
                item {
                    val isPdf = state.contextInfo.startsWith("PDF:")
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text(if (isPdf) "Extracting PDF Pages" else "Phase 1: Downloading Video", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        if (state.contextInfo.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(state.contextInfo, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, textAlign = TextAlign.Center)
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { state.progress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = if (isPdf) "${state.progress.toInt()}%" else "${state.progress.toInt()}% • ETA: ${state.eta}", fontWeight = FontWeight.Bold)
                        Text(text = state.currentLine, style = MaterialTheme.typography.bodySmall, maxLines = 1)

                        OutlinedButton(onClick = { viewModel.cancelProcess() }, modifier = Modifier.padding(top = 16.dp)) {
                            Text("Cancel", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            is FrameExtractionState.ExtractingFrames -> {
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text("Phase 2: Extracting Frames", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        if (state.contextInfo.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(state.contextInfo, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, textAlign = TextAlign.Center)
                        }
                        Spacer(Modifier.height(16.dp))
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Slicing video into frames. Please wait...\n(Video will be automatically deleted afterward)",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        OutlinedButton(onClick = { viewModel.cancelProcess() }, modifier = Modifier.padding(top = 16.dp)) {
                            Text("Cancel", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            is FrameExtractionState.Success -> {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("✅ Done!", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text(state.folderPath, color = MaterialTheme.colorScheme.onSecondaryContainer, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = onDismissRequest, modifier = Modifier.fillMaxWidth()) {
                                Text("Done")
                            }
                        }
                    }
                }
            }

            is FrameExtractionState.Error -> {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text("❌ ${state.message}", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
            else -> {}
        }
    }
}

private data class FlatFrame(
    val file: File,
    val sectionName: String,
    val localIndex: Int,
    val sectionTotal: Int,
    val globalIndex: Int,
    val globalTotal: Int
)

