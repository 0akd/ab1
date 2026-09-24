package com.arjun.absolutra.downloader.audio

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
fun Mp3DownloaderScreen(
    modifier: Modifier = Modifier,
    viewModel: Mp3DownloaderViewModel = viewModel(),
    onDismissRequest: (() -> Unit)? = null
) {
    var urlInput by remember { mutableStateOf("") }
    val downloadState by viewModel.downloadState.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Close button when shown inside ModalBottomSheet
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
            text = "YouTube to MP3",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "Paste a YouTube or YT Music link below to extract the audio track.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        OutlinedTextField(
            value = urlInput,
            onValueChange = { urlInput = it },
            label = { Text("Video URL") },
            placeholder = { Text("https://www.youtube.com/watch?v=...") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(
                onGo = { viewModel.startDownload(context, urlInput) }
            ),
            enabled = downloadState is Mp3DownloadState.Idle || downloadState is Mp3DownloadState.Success || downloadState is Mp3DownloadState.Error
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Status & Progress Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.TopCenter
        ) {
            when (val state = downloadState) {
                is Mp3DownloadState.Idle -> { /* Empty */ }
                is Mp3DownloadState.Initializing -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Initializing extractors...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                is Mp3DownloadState.Downloading -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        LinearProgressIndicator(
                            progress = { state.progress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "${state.progress.toInt()}% • ETA: ${state.eta}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = state.currentLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = { viewModel.cancelDownload() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Cancel Download")
                        }
                    }
                }
                is Mp3DownloadState.Success -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "✅ ${state.filePath}",
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            if (onDismissRequest != null) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(onClick = onDismissRequest, modifier = Modifier.fillMaxWidth()) {
                                    Text("Done")
                                }
                            }
                        }
                    }
                }
                is Mp3DownloadState.Error -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "❌ ${state.message}",
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = downloadState is Mp3DownloadState.Idle || downloadState is Mp3DownloadState.Success || downloadState is Mp3DownloadState.Error
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { viewModel.startDownload(context, urlInput) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = urlInput.isNotBlank()
                ) {
                    Text("Extract Audio", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
