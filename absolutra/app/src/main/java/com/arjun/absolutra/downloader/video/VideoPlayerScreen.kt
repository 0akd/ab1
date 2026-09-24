@file:OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)

package com.arjun.absolutra.downloader.video

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.arjun.absolutra.downloader.core.explorer.GenericMediaExplorer
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class DragMode { NONE, BRIGHTNESS, VOLUME, SEEK }

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun formatTimeMs(ms: Long): String {
    if (ms < 0) return "00:00"
    val totalSeconds = ms / 1000
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%02d:%02d".format(m, s)
}

@Composable
fun VideoPlayerScreen(
    modifier: Modifier = Modifier,
    viewModel: VideoDownloaderViewModel = viewModel(),
) {
    var activeVideoPath by rememberSaveable { mutableStateOf<String?>(null) }
    val activeVideo = activeVideoPath?.let { File(it) }
    var showDownloadSheet by remember { mutableStateOf(false) }

    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) viewModel.refresh()
    }

    LaunchedEffect(Unit) {
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(permission)
        } else {
            viewModel.refresh()
        }
    }

    if (showDownloadSheet) {
        ModalBottomSheet(
            onDismissRequest = { showDownloadSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            VideoDownloaderScreen(
                viewModel = viewModel,
                onDismissRequest = { showDownloadSheet = false },
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        GenericMediaExplorer(
            title = "My Videos",
            viewModel = viewModel,
            onDownloadClick = { showDownloadSheet = true },
            onItemClick = { file ->
                if (file.isDirectory) viewModel.navigateInto(file)
                else activeVideoPath = file.absolutePath
            },
            leadingContent = { file ->
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (file.isDirectory) "📁" else "🎬", style = MaterialTheme.typography.headlineSmall)
                }
            },
            headlineContent = { file ->
                Text(file.nameWithoutExtension, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = { file ->
                if (!file.isDirectory) Text("${file.length() / (1024 * 1024)} MB", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else Text("Folder", color = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            trailingContent = { file ->
                if (!file.isDirectory) {
                    IconButton(onClick = { activeVideoPath = file.absolutePath }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                    }
                }
            }
        )

        AnimatedVisibility(
            visible = activeVideo != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            activeVideo?.let { file ->
                ImmersiveExoPlayer(
                    file = file,
                    onClose = { activeVideoPath = null },
                )
            }
        }
    }
}

@Composable
fun ImmersiveExoPlayer(
    file: File,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val lifecycleOwner = LocalLifecycleOwner.current

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
            playWhenReady = true
        }
    }

    BackHandler(onBack = onClose)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> exoPlayer.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(Unit) {
        val window = activity?.window
        val insetsController = window?.let { WindowInsetsControllerCompat(it, it.decorView) }

        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        insetsController?.apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        onDispose {
            exoPlayer.release()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    var dragMode by remember { mutableStateOf(DragMode.NONE) }
    var isLeftHalf by remember { mutableStateOf(true) }
    var accumulatedX by remember { mutableFloatStateOf(0f) }
    var accumulatedY by remember { mutableFloatStateOf(0f) }
    var indicatorValue by remember { mutableFloatStateOf(0f) }

    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val maxVolume = remember {
        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    }
    var startBrightness by remember { mutableFloatStateOf(0.5f) }
    var startVolume by remember { mutableIntStateOf(0) }

    var startPosition by remember { mutableLongStateOf(0L) }
    var targetPosition by remember { mutableLongStateOf(0L) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val screenWidth = size.width.toFloat()
                        isLeftHalf = offset.x < (screenWidth / 2f)

                        dragMode = DragMode.NONE
                        accumulatedX = 0f
                        accumulatedY = 0f

                        startBrightness = activity?.window?.attributes?.screenBrightness
                            ?.takeIf { it >= 0 }
                            ?: 0.5f
                        startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        startPosition = exoPlayer.currentPosition
                    },
                    onDragEnd = { dragMode = DragMode.NONE },
                    onDragCancel = { dragMode = DragMode.NONE },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedX += dragAmount.x
                        accumulatedY += dragAmount.y

                        if (dragMode == DragMode.NONE) {
                            val touchSlop = 30f
                            if (abs(accumulatedX) > touchSlop || abs(accumulatedY) > touchSlop) {
                                dragMode = if (abs(accumulatedX) > abs(accumulatedY)) {
                                    DragMode.SEEK
                                } else if (isLeftHalf) {
                                    DragMode.BRIGHTNESS
                                } else {
                                    DragMode.VOLUME
                                }
                            }
                        }

                        val screenHeight = size.height.toFloat()

                        when (dragMode) {
                            DragMode.SEEK -> {
                                val seekOffset = (accumulatedX * 150f).toLong()
                                val currentDuration = exoPlayer.duration.takeIf { it > 0 } ?: 0L
                                targetPosition = (startPosition + seekOffset)
                                    .coerceIn(0L, currentDuration)
                                exoPlayer.seekTo(targetPosition)
                            }
                            DragMode.BRIGHTNESS -> {
                                val fraction = -accumulatedY / screenHeight
                                val newBrightness = (startBrightness + fraction).coerceIn(0f, 1f)
                                activity?.window?.let { win ->
                                    val layoutParams = win.attributes
                                    layoutParams.screenBrightness = newBrightness
                                    win.attributes = layoutParams
                                }
                                indicatorValue = newBrightness
                            }
                            DragMode.VOLUME -> {
                                val fraction = -accumulatedY / screenHeight
                                val newVolume = (startVolume + (fraction * maxVolume))
                                    .roundToInt()
                                    .coerceIn(0, maxVolume)
                                audioManager.setStreamVolume(
                                    AudioManager.STREAM_MUSIC,
                                    newVolume,
                                    0,
                                )
                                indicatorValue = newVolume.toFloat() / maxVolume.toFloat()
                            }
                            DragMode.NONE -> Unit
                        }
                    },
                )
            },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    controllerShowTimeoutMs = 2500
                    keepScreenOn = true
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        AnimatedVisibility(
            visible = dragMode != DragMode.NONE,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(24.dp))
                    .padding(32.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (dragMode == DragMode.SEEK) {
                        val isForward = accumulatedX > 0
                        Icon(
                            imageVector = if (isForward) {
                                Icons.Default.FastForward
                            } else {
                                Icons.Default.FastRewind
                            },
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(56.dp),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "${formatTimeMs(targetPosition)} / ${formatTimeMs(exoPlayer.duration.takeIf { it > 0 } ?: 0L)}",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    } else {
                        Icon(
                            imageVector = if (dragMode == DragMode.BRIGHTNESS) {
                                Icons.Default.Brightness6
                            } else {
                                Icons.AutoMirrored.Filled.VolumeUp
                            },
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(56.dp),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { indicatorValue },
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .width(140.dp)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                        )
                    }
                }
            }
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopStart)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50)),
        ) {
            Text(
                text = "✕",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}
