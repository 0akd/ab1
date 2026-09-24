package com.arjun.absolutra.downloader.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoDownloaderScreen(
    modifier: Modifier = Modifier,
    viewModel: VideoDownloaderViewModel = viewModel(),
    onDismissRequest: (() -> Unit)? = null
) {
    var urlInput by remember { mutableStateOf("") }
    val downloadState by viewModel.downloadState.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (onDismissRequest != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onDismissRequest) {
                    Icon(Icons.Default.Close, contentDescription = "Close download screen")
                }
            }
        }

        Text(
            text = "Video Downloader",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "Download MP4 videos or full playlists to your device gallery.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // URL Input
        OutlinedTextField(
            value = urlInput,
            onValueChange = { urlInput = it },
            label = { Text("Video or Playlist URL") },
            placeholder = { Text("https://www.youtube.com/playlist?list=...") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(
                onGo = { viewModel.fetchFormats(context, urlInput) }
            ),
            enabled = downloadState is VideoDownloadState.Idle || downloadState is VideoDownloadState.Success || downloadState is VideoDownloadState.Error
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Initial Fetch Button
        AnimatedVisibility(visible = downloadState is VideoDownloadState.Idle || downloadState is VideoDownloadState.Success || downloadState is VideoDownloadState.Error) {
            Button(
                onClick = { viewModel.fetchFormats(context, urlInput) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = urlInput.isNotBlank()
            ) {
                Text("Fetch Qualities", style = MaterialTheme.typography.titleMedium)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Status / Progress / Selection UI
        when (val state = downloadState) {
            is VideoDownloadState.FetchingFormats -> {
                CircularProgressIndicator()
                Text("Fetching available qualities...", modifier = Modifier.padding(top = 8.dp))
            }
            
            is VideoDownloadState.FormatSelection -> {
                var selectedVideoFormat by remember(state.url, state.isPlaylist) {
                    mutableStateOf(state.videoFormats.firstOrNull())
                }
                var selectedAudioFormat by remember(state.url) {
                    mutableStateOf(state.audioFormats.firstOrNull())
                }

                var expandedVideo by remember { mutableStateOf(false) }
                var expandedAudio by remember { mutableStateOf(false) }

                Column(modifier = Modifier.fillMaxWidth()) {
                    if (state.isPlaylist) {
                        Text(
                            text = "Playlist detected – all videos will be downloaded",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text = "Pick a max quality below. Large playlists can take a long time and use significant storage.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }

                    Text(
                        text = if (state.isPlaylist) "Select Max Quality" else "Select Video Quality",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    ExposedDropdownMenuBox(
                        expanded = expandedVideo,
                        onExpandedChange = { expandedVideo = !expandedVideo }
                    ) {
                        OutlinedTextField(
                            readOnly = true,
                            value = selectedVideoFormat?.let {
                                if (state.isPlaylist) {
                                    "${it.resolution} — ${it.note}"
                                } else {
                                    "${it.resolution} - ${it.ext} (${it.sizeStr})"
                                }
                            } ?: "No Video Found",
                            onValueChange = { },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedVideo) }
                        )
                        ExposedDropdownMenu(
                            expanded = expandedVideo,
                            onDismissRequest = { expandedVideo = false }
                        ) {
                            state.videoFormats.forEach { format ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (state.isPlaylist) {
                                                "${format.resolution} — ${format.note}"
                                            } else {
                                                "${format.resolution} - ${format.ext} (${format.sizeStr})"
                                            }
                                        )
                                    },
                                    onClick = {
                                        selectedVideoFormat = format
                                        expandedVideo = false
                                    }
                                )
                            }
                        }
                    }

                    // Hide audio selector for playlists (format selector already includes audio)
                    if (!state.isPlaylist && state.audioFormats.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Select Audio Quality", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                        ExposedDropdownMenuBox(
                            expanded = expandedAudio,
                            onExpandedChange = { expandedAudio = !expandedAudio }
                        ) {
                            OutlinedTextField(
                                readOnly = true,
                                value = selectedAudioFormat?.let { "${it.note} - ${it.ext} (${it.sizeStr})" } ?: "None",
                                onValueChange = { },
                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedAudio) }
                            )
                            ExposedDropdownMenu(
                                expanded = expandedAudio,
                                onDismissRequest = { expandedAudio = false }
                            ) {
                                state.audioFormats.forEach { format ->
                                    DropdownMenuItem(
                                        text = { Text("${format.note} - ${format.ext} (${format.sizeStr})") },
                                        onClick = {
                                            selectedAudioFormat = format
                                            expandedAudio = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            if (state.isPlaylist) {
                                viewModel.startPlaylistDownload(
                                    context = context,
                                    url = state.url,
                                    formatSelector = selectedVideoFormat?.formatId ?: "best",
                                    title = state.title
                                )
                            } else {
                                viewModel.startDownloadWithFormats(
                                    context = context,
                                    url = state.url,
                                    videoFormatId = selectedVideoFormat?.formatId ?: "",
                                    audioFormatId = if (state.audioFormats.isNotEmpty()) {
                                        selectedAudioFormat?.formatId ?: ""
                                    } else {
                                        ""
                                    },
                                    title = state.title
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text(
                            if (state.isPlaylist) "Download Entire Playlist" else "Download & Merge",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }

            is VideoDownloadState.Initializing -> {
                CircularProgressIndicator()
                Text("Initializing Engine...", modifier = Modifier.padding(top = 8.dp))
            }
            
            is VideoDownloadState.Downloading -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier.fillMaxWidth().height(8.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "${state.progress.toInt()}% • ETA: ${state.eta}", fontWeight = FontWeight.Bold)
                    Text(text = state.currentLine, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    OutlinedButton(onClick = { viewModel.cancelDownload() }, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Cancel", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            
            is VideoDownloadState.Success -> {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("✅ Download Completed & Merged!", color = MaterialTheme.colorScheme.onSecondaryContainer)
                        if (onDismissRequest != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = onDismissRequest, modifier = Modifier.fillMaxWidth()) {
                                Text("Done")
                            }
                        }
                    }
                }
            }
            
            is VideoDownloadState.Error -> {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text("❌ ${state.message}", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            else -> {}
        }
    }
}
