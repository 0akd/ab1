package com.arjun.absolutra.ui.screens.todo

import android.Manifest
import android.content.ClipData
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.arjun.absolutra.ui.screens.InsertBetweenButton
import com.arjun.absolutra.ui.screens.deleteImage
import com.arjun.absolutra.ui.screens.replaceImageInBoard
import com.arjun.absolutra.ui.screens.swapImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class VideoPickerAction {
    object Add : VideoPickerAction()
    data class Replace(val target: DocumentFile) : VideoPickerAction()
    data class InsertAt(val index: Int) : VideoPickerAction()
}

fun getVideoFileValue(name: String): Double {
    val numStr = name.removePrefix("video_").removeSuffix(".mp4")
    return numStr.toDoubleOrNull() ?: 0.0
}

private fun hasVideoReadAccess(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val fullAccess = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_MEDIA_VIDEO
        ) == PackageManager.PERMISSION_GRANTED
        val selectedAccess = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            ) == PackageManager.PERMISSION_GRANTED
        fullAccess || selectedAccess
    } else {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }
}

suspend fun getOrCreateTaskDir(context: Context, rootDirUriStr: String, taskId: String): DocumentFile? {
    return withContext(Dispatchers.IO) {
        try {
            val rootUri = Uri.parse(rootDirUriStr)
            val baseDir = DocumentFile.fromTreeUri(context, rootUri) ?: return@withContext null
            val absolutraDir = baseDir.findFile("absolutra") ?: baseDir.createDirectory("absolutra")
            val todoDir = absolutraDir?.findFile("todo") ?: absolutraDir?.createDirectory("todo")
            todoDir?.findFile(taskId) ?: todoDir?.createDirectory(taskId)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoBoardDetail(task: Task, board: DocumentFile, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var videos by remember { mutableStateOf(emptyList<DocumentFile>()) }
    var isLoading by remember { mutableStateOf(false) }
    var currentPickerAction by remember { mutableStateOf<VideoPickerAction?>(null) }
    var hasVideoAccess by remember { mutableStateOf(hasVideoReadAccess(context)) }
    var insertMode by remember { mutableStateOf(false) }

    LaunchedEffect(board) {
        isLoading = true
        videos = loadVideosFromBoard(board)
        isLoading = false
    }

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        hasVideoAccess = hasVideoReadAccess(context)
        if (!hasVideoAccess) currentPickerAction = null
    }

    fun launchPicker(action: VideoPickerAction) {
        currentPickerAction = action
        if (!hasVideoReadAccess(context)) {
            permissionLauncher.launch(permission)
        } else {
            hasVideoAccess = true
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = task.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .weight(1f)
            )
            TextButton(onClick = { insertMode = !insertMode }) {
                Text(if (insertMode) "Done" else "Edit")
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            if (videos.isEmpty() && !isLoading) {
                if (insertMode) {
                    Box(modifier = Modifier.align(Alignment.Center)) {
                        InsertBetweenButton(onClick = { launchPicker(VideoPickerAction.Add) })
                    }
                } else {
                    Text(
                        "No videos in this task. Click 'Edit' to add.",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (insertMode && videos.isNotEmpty()) {
                        item { InsertBetweenButton(onClick = { launchPicker(VideoPickerAction.InsertAt(0)) }) }
                    }

                    itemsIndexed(videos, key = { _, file -> file.uri.toString() }) { index, file ->
                        CanvasVideoItem(
                            file = file,
                            isFirst = index == 0,
                            isLast = index == videos.lastIndex,
                            onDelete = {
                                scope.launch {
                                    isLoading = true
                                    deleteImage(file)
                                    videos = loadVideosFromBoard(board)
                                    isLoading = false
                                }
                            },
                            onMoveUp = {
                                scope.launch {
                                    isLoading = true
                                    swapImages(file, videos[index - 1])
                                    videos = loadVideosFromBoard(board)
                                    isLoading = false
                                }
                            },
                            onMoveDown = {
                                scope.launch {
                                    isLoading = true
                                    swapImages(file, videos[index + 1])
                                    videos = loadVideosFromBoard(board)
                                    isLoading = false
                                }
                            },
                            onReplace = { launchPicker(VideoPickerAction.Replace(file)) }
                        )

                        if (insertMode) {
                            InsertBetweenButton(onClick = { launchPicker(VideoPickerAction.InsertAt(index + 1)) })
                        }
                    }
                }
            }
            if (isLoading) CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }

    if (currentPickerAction != null && hasVideoAccess) {
        VideoPickerSheet(
            onDismiss = { currentPickerAction = null },
            onVideosSelected = { uris ->
                val action = currentPickerAction
                currentPickerAction = null
                scope.launch {
                    isLoading = true
                    if (uris.isNotEmpty()) {
                        when (action) {
                            is VideoPickerAction.Add -> {
                                val baseTime = System.currentTimeMillis().toDouble()
                                uris.forEachIndexed { i, uri ->
                                    saveVideoToBoard(context, uri, board, baseTime + (i * 1000.0))
                                }
                            }
                            is VideoPickerAction.Replace -> {
                                replaceImageInBoard(context, uris.first(), action.target)
                            }
                            is VideoPickerAction.InsertAt -> {
                                val index = action.index
                                val prevValue = if (index > 0) getVideoFileValue(videos[index - 1].name ?: "") else null
                                val nextValue = if (index < videos.size) getVideoFileValue(videos[index].name ?: "") else null

                                uris.forEachIndexed { i, uri ->
                                    val newValue = when {
                                        prevValue != null && nextValue != null -> {
                                            val step = (nextValue - prevValue) / (uris.size + 1)
                                            prevValue + step * (i + 1)
                                        }
                                        prevValue != null -> prevValue + (1000.0 * (i + 1))
                                        nextValue != null -> nextValue - (1000.0 * (uris.size - i))
                                        else -> System.currentTimeMillis().toDouble() + (i * 1000.0)
                                    }
                                    saveVideoToBoard(context, uri, board, newValue)
                                }
                            }
                            null -> {}
                        }
                    }
                    videos = loadVideosFromBoard(board)
                    isLoading = false
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPickerSheet(
    onDismiss: () -> Unit,
    onVideosSelected: (List<Uri>) -> Unit
) {
    val context = LocalContext.current
    var videos by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val selectedUris = remember { mutableStateListOf<Uri>() }

    val imageLoader = remember {
        ImageLoader.Builder(context).components { add(VideoFrameDecoder.Factory()) }.build()
    }

    LaunchedEffect(Unit) { videos = loadDeviceVideos(context) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = if (selectedUris.isEmpty()) "Select Video(s)" else "${selectedUris.size} selected",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (selectedUris.isNotEmpty()) {
                    TextButton(onClick = { onVideosSelected(selectedUris.toList()) }) { Text("Done") }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(4.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(videos) { uri ->
                    val index = selectedUris.indexOf(uri)
                    val isSelected = index != -1

                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .aspectRatio(1f)
                            .pointerInput(uri) {
                                detectTapGestures(
                                    onLongPress = { if (uri !in selectedUris) selectedUris.add(uri) },
                                    onTap = {
                                        if (selectedUris.isNotEmpty()) {
                                            if (uri in selectedUris) selectedUris.remove(uri)
                                            else selectedUris.add(uri)
                                        } else {
                                            onVideosSelected(listOf(uri))
                                        }
                                    }
                                )
                            }
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(uri).crossfade(true).build(),
                            imageLoader = imageLoader,
                            contentDescription = "Picker Video Thumbnail",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(4.dp)
                                .size(16.dp)
                        )

                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(Color.Black.copy(alpha = 0.5f))
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = (index + 1).toString(),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

suspend fun loadDeviceVideos(context: Context): List<Uri> = withContext(Dispatchers.IO) {
    val uris = mutableListOf<Uri>()
    val projection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DATE_ADDED)
    val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

    context.contentResolver.query(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection, null, null, sortOrder
    )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
            uris.add(contentUri)
        }
    }
    uris
}

@OptIn(UnstableApi::class)
@Composable
fun CanvasVideoItem(
    file: DocumentFile,
    isFirst: Boolean,
    isLast: Boolean,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onReplace: () -> Unit
) {
    val context = LocalContext.current
    var showOverlay by remember { mutableStateOf(false) }
    var aspectRatio by remember(file.uri) { mutableStateOf(16f / 9f) }

    val exoPlayer = remember(file.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(file.uri))
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
            volume = 0f
            prepare()
        }
    }

    DisposableEffect(file.uri) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.height > 0) {
                    val ratio = videoSize.width.toFloat() / videoSize.height
                    if (ratio > 0f) aspectRatio = ratio
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clickable { showOverlay = !showOverlay }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
        )

        if (showOverlay) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = { showOverlay = false; onDelete() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
                    }
                    if (!isFirst) {
                        IconButton(onClick = { showOverlay = false; onMoveUp() }) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move Up", tint = Color.White)
                        }
                    }
                    if (!isLast) {
                        IconButton(onClick = { showOverlay = false; onMoveDown() }) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move Down", tint = Color.White)
                        }
                    }
                    IconButton(onClick = { showOverlay = false; onReplace() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Replace", tint = Color.White)
                    }
                    IconButton(onClick = {
                        showOverlay = false
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = file.type ?: "video/*"
                            putExtra(Intent.EXTRA_STREAM, file.uri)
                            clipData = ClipData.newRawUri("Canvas Video", file.uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Video"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                    }
                }
            }
        }
    }
}

suspend fun loadVideosFromBoard(boardDir: DocumentFile): List<DocumentFile> {
    return withContext(Dispatchers.IO) {
        try {
            boardDir.listFiles()
                .filter { file ->
                    file.isFile && (
                        file.type?.startsWith("video/") == true ||
                            file.name?.endsWith(".mp4", ignoreCase = true) == true
                        )
                }
                .sortedBy { getVideoFileValue(it.name ?: "") }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}

suspend fun saveVideoToBoard(
    context: Context,
    sourceUri: Uri,
    boardDir: DocumentFile,
    newValue: Double? = null
) {
    withContext(Dispatchers.IO) {
        try {
            val value = newValue ?: System.currentTimeMillis().toDouble()
            val valueStr = java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
            val fileName = "video_${valueStr}.mp4"
            val newFile = boardDir.createFile("video/mp4", fileName) ?: return@withContext

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
