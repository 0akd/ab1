package com.arjun.absolutra.downloader.audio

import android.Manifest
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arjun.absolutra.downloader.core.explorer.GenericMediaExplorer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

private data class TrackMetadata(
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long = 0L
)

@Composable
fun AlbumArt(file: File, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    var bitmap by remember(file.absolutePath) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(file.absolutePath) {
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                val data = retriever.embeddedPicture
                if (data != null) bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
            } catch (_: Exception) {
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {
                }
            }
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            modifier = modifier.clip(RoundedCornerShape(12.dp)).shadow(6.dp, RoundedCornerShape(12.dp)),
            contentScale = contentScale
        )
    } else {
        Box(
            modifier = modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Mp3PlayerScreen(
    modifier: Modifier = Modifier,
    viewModel: Mp3DownloaderViewModel = viewModel()
) {
    val context = LocalContext.current
    val items by viewModel.items.collectAsState()

    val activity = context as? Activity
    LaunchedEffect(Unit) { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
    DisposableEffect(Unit) { onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED } }

    var currentTrack by remember { mutableStateOf<File?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var currentPlayingIndex by remember { mutableIntStateOf(-1) }

    var showDownloadSheet by remember { mutableStateOf(false) }
    var showFullPlayer by remember { mutableStateOf(false) }

    val mediaPlayer = remember { MediaPlayer() }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) viewModel.refresh()
    }

    LaunchedEffect(Unit) {
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) permissionLauncher.launch(permission)
        else viewModel.refresh()
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = mediaPlayer.currentPosition.toLong()
            delay(400)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (mediaPlayer.isPlaying) mediaPlayer.stop()
            mediaPlayer.release()
        }
    }

    fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }

    val playableFiles = items.filter { !it.isDirectory }

    fun playTrack(file: File) {
        try {
            mediaPlayer.reset()
            mediaPlayer.setDataSource(file.absolutePath)
            mediaPlayer.prepare()
            mediaPlayer.start()
            currentTrack = file
            isPlaying = true
            duration = mediaPlayer.duration.toLong()
            currentPosition = 0L
            currentPlayingIndex = playableFiles.indexOfFirst { it.absolutePath == file.absolutePath }
        } catch (_: Exception) {
            isPlaying = false
        }
    }

    fun togglePlayPause() {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
            isPlaying = false
        } else {
            mediaPlayer.start()
            isPlaying = true
        }
    }

    fun stopPlayback() {
        mediaPlayer.stop()
        mediaPlayer.reset()
        currentTrack = null
        isPlaying = false
        showFullPlayer = false
        currentPosition = 0L
        duration = 0L
        currentPlayingIndex = -1
    }

    fun playNext() {
        if (currentPlayingIndex in 0 until playableFiles.lastIndex) playTrack(playableFiles[currentPlayingIndex + 1])
    }

    fun playPrevious() {
        if (currentPlayingIndex > 0) playTrack(playableFiles[currentPlayingIndex - 1])
    }

    if (showDownloadSheet) {
        ModalBottomSheet(
            onDismissRequest = { showDownloadSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Mp3DownloaderScreen(viewModel = viewModel, onDismissRequest = { showDownloadSheet = false })
        }
    }

    if (showFullPlayer && currentTrack != null) {
        val file = currentTrack!!
        var trackInfo by remember(file.absolutePath) {
            mutableStateOf(TrackMetadata(file.nameWithoutExtension, "Unknown Artist", ""))
        }

        LaunchedEffect(file.absolutePath) {
            withContext(Dispatchers.IO) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(file.absolutePath)
                    val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: file.nameWithoutExtension
                    val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
                    val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: ""
                    trackInfo = TrackMetadata(title, artist, album)
                } catch (_: Exception) {
                } finally {
                    try {
                        retriever.release()
                    } catch (_: Exception) {
                    }
                }
            }
        }

        ModalBottomSheet(
            onDismissRequest = { showFullPlayer = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            dragHandle = null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = { showFullPlayer = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close player")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                AlbumArt(
                    file = file,
                    modifier = Modifier
                        .size(280.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
                Spacer(modifier = Modifier.height(28.dp))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = trackInfo.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = trackInfo.artist,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    if (trackInfo.album.isNotBlank()) {
                        Text(
                            text = trackInfo.album,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                        onValueChange = { newValue ->
                            val seekPos = (newValue * duration).toLong()
                            mediaPlayer.seekTo(seekPos.toInt())
                            currentPosition = seekPos
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(currentPosition),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatTime(duration),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { playPrevious() },
                        modifier = Modifier.size(64.dp),
                        enabled = currentPlayingIndex > 0
                    ) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous track", modifier = Modifier.size(36.dp))
                    }

                    FilledIconButton(
                        onClick = { togglePlayPause() },
                        modifier = Modifier.size(80.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(48.dp)
                        )
                    }

                    IconButton(
                        onClick = { playNext() },
                        modifier = Modifier.size(64.dp),
                        enabled = currentPlayingIndex >= 0 && currentPlayingIndex < playableFiles.lastIndex
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next track", modifier = Modifier.size(36.dp))
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))

                val audioManager = remember {
                    context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                }
                val maxVolume = remember { audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC) }
                var sliderPosition by remember {
                    mutableFloatStateOf(
                        audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat() / maxVolume
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("🔈", fontSize = 20.sp)
                    Slider(
                        value = sliderPosition,
                        onValueChange = { newValue ->
                            sliderPosition = newValue
                            val vol = (newValue * maxVolume).toInt()
                            audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, vol, 0)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Text("🔊", fontSize = 20.sp)
                }

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedButton(
                    onClick = { stopPlayback() },
                    modifier = Modifier.fillMaxWidth(0.6f)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Stop")
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        GenericMediaExplorer(
            title = "My Audio",
            viewModel = viewModel,
            onDownloadClick = { showDownloadSheet = true },
            onItemClick = { file ->
                if (file.isDirectory) viewModel.navigateInto(file)
                else {
                    if (currentTrack == file && isPlaying) togglePlayPause() else playTrack(file)
                }
            },
            leadingContent = { file ->
                if (file.isDirectory) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📁", style = MaterialTheme.typography.headlineSmall)
                    }
                } else {
                    AlbumArt(file = file, modifier = Modifier.size(56.dp))
                }
            },
            headlineContent = { file ->
                var title by remember(file.absolutePath) { mutableStateOf(file.name) }
                if (!file.isDirectory) {
                    LaunchedEffect(file.absolutePath) {
                        withContext(Dispatchers.IO) {
                            val retriever = MediaMetadataRetriever()
                            try {
                                retriever.setDataSource(file.absolutePath)
                                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: file.nameWithoutExtension
                            } catch (_: Exception) {
                            } finally {
                                try {
                                    retriever.release()
                                } catch (_: Exception) {
                                }
                            }
                        }
                    }
                }
                Text(title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = { file ->
                var subtitle by remember(file.absolutePath) { mutableStateOf(if (file.isDirectory) "Folder" else "Loading...") }
                if (!file.isDirectory) {
                    LaunchedEffect(file.absolutePath) {
                        withContext(Dispatchers.IO) {
                            val retriever = MediaMetadataRetriever()
                            try {
                                retriever.setDataSource(file.absolutePath)
                                val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
                                val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                                val dur = durStr?.toLongOrNull() ?: 0L
                                subtitle = "$artist • ${formatTime(dur)}"
                            } catch (_: Exception) {
                                subtitle = "Audio File"
                            } finally {
                                try {
                                    retriever.release()
                                } catch (_: Exception) {
                                }
                            }
                        }
                    }
                }
                Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            trailingContent = { file ->
                if (!file.isDirectory) {
                    IconButton(onClick = { if (currentTrack == file && isPlaying) togglePlayPause() else playTrack(file) }) {
                        Icon(if (currentTrack == file && isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null)
                    }
                }
            },
            bottomBar = {
                if (currentTrack != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showFullPlayer = true },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AlbumArt(file = currentTrack!!, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    currentTrack!!.nameWithoutExtension,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${formatTime(currentPosition)} / ${formatTime(duration)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { togglePlayPause() }) {
                                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                            }
                            IconButton(onClick = { stopPlayback() }) {
                                Icon(Icons.Default.Stop, "Stop")
                            }
                        }
                    }
                }
            }
        )
    }
}
