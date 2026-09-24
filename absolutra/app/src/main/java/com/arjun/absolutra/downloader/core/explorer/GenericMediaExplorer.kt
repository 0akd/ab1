package com.arjun.absolutra.downloader.core.explorer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arjun.absolutra.downloader.common.MediaPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenericMediaExplorer(
    title: String,
    viewModel: BaseMediaViewModel,
    onDownloadClick: () -> Unit,
    onItemClick: (File) -> Unit,
    topBarActions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    leadingContent: @Composable (File) -> Unit,
    headlineContent: @Composable (File) -> Unit,
    supportingContent: @Composable (File) -> Unit,
    trailingContent: @Composable (File) -> Unit = {}
) {
    val context = LocalContext.current
    var editMode by remember { mutableStateOf(false) }
    var selectedFiles by remember { mutableStateOf(setOf<File>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var destDialogType by remember { mutableStateOf<Boolean?>(null) } // true=Copy, false=Move
    var showNewFolderDialog by remember { mutableStateOf(false) }

    val currentDir by viewModel.currentDir.collectAsState()
    val items by viewModel.items.collectAsState()
    val deletionProgress by viewModel.deletionProgress.collectAsState()

    // Restore and track scroll state mapped to the current folder's path.
    // LazyListState ctor uses firstVisibleItemIndex/Offset (not the rememberLazyListState names).
    val currentPath = currentDir?.absolutePath ?: "root"
    val listState = remember(currentPath) {
        val saved = viewModel.getScrollPosition(currentPath)
        LazyListState(
            firstVisibleItemIndex = saved.first.coerceAtLeast(0),
            firstVisibleItemScrollOffset = saved.second.coerceAtLeast(0),
        )
    }

    DisposableEffect(currentPath, listState) {
        onDispose {
            viewModel.saveScrollPosition(
                currentPath,
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
            )
        }
    }

    BackHandler(enabled = editMode || currentDir != null || deletionProgress.isActive) {
        when {
            deletionProgress.isActive -> { /* swallow – force user to wait or cancel via dialog */ }
            editMode -> {
                editMode = false
                selectedFiles = emptySet()
            }
            else -> viewModel.navigateUp()
        }
    }

    if (showNewFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("Create New Folder") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (folderName.isNotBlank()) viewModel.createFolder(folderName)
                        showNewFolderDialog = false
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Selected Items") },
            text = {
                Text(
                    "Permanently erase ${selectedFiles.size} item(s) from this device?\n\n" +
                    "This removes the files from storage and from the system MediaStore index so they cannot reappear in Gallery or this app."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteItems(selectedFiles)
                    showDeleteConfirm = false
                    editMode = false
                    selectedFiles = emptySet()
                }) { Text("Delete forever", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (deletionProgress.isActive) {
        AlertDialog(
            onDismissRequest = { /* locked until finished or cancelled */ },
            title = {
                Text(
                    "Deleting…",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (deletionProgress.total > 0) {
                        LinearProgressIndicator(
                            progress = { deletionProgress.current.toFloat() / deletionProgress.total.coerceAtLeast(1) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "${deletionProgress.current} / ${deletionProgress.total}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        deletionProgress.message,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Files are being permanently removed from device storage and MediaStore. Do not force-close the app.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                if (deletionProgress.canCancel) {
                    TextButton(
                        onClick = { viewModel.cancelDeletion() }
                    ) {
                        Text("Cancel", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = {}
        )
    }

    var availableFolders by remember { mutableStateOf(emptyList<File>()) }
    LaunchedEffect(destDialogType) {
        if (destDialogType != null) {
            withContext(Dispatchers.IO) {
                val all = viewModel.getAllFolders()
                availableFolders = all.filter { target ->
                    selectedFiles.none { sel -> target.absolutePath.startsWith(sel.absolutePath) }
                }
            }
        }
    }

    if (destDialogType != null) {
        DestinationFolderDialog(
            root = MediaPaths.getMediaRoot(viewModel.mediaType),
            folders = availableFolders,
            onDismiss = { destDialogType = null },
            onConfirm = { destFile ->
                if (destDialogType == true) {
                    viewModel.copyItems(selectedFiles, destFile)
                } else {
                    viewModel.moveItems(selectedFiles, destFile)
                }
                destDialogType = null
                editMode = false
                selectedFiles = emptySet()
            }
        )
    }

    Scaffold(
        bottomBar = bottomBar,
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (editMode) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { editMode = false; selectedFiles = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = "Close Edit Mode")
                        }
                        Text("${selectedFiles.size} selected", style = MaterialTheme.typography.titleLarge)
                    }
                    Row {
                        TextButton(
                            onClick = {
                                val pathsJson = JSONArray(selectedFiles.map { it.absolutePath }).toString()
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Folder Paths", pathsJson))
                                Toast.makeText(context, "Copied paths to clipboard for task linking", Toast.LENGTH_SHORT).show()
                                editMode = false
                                selectedFiles = emptySet()
                            },
                            enabled = selectedFiles.isNotEmpty() && !deletionProgress.isActive
                        ) { Text("Link Task") }
                        
                        TextButton(onClick = { destDialogType = true }, enabled = selectedFiles.isNotEmpty() && !deletionProgress.isActive) { Text("Copy") }
                        TextButton(onClick = { destDialogType = false }, enabled = selectedFiles.isNotEmpty() && !deletionProgress.isActive) { Text("Move") }
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            enabled = selectedFiles.isNotEmpty() && !deletionProgress.isActive
                        ) {
                            Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                } else {
                    Text(text = title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        topBarActions()
                        IconButton(onClick = { showNewFolderDialog = true }) {
                            Icon(Icons.Default.CreateNewFolder, "New Folder")
                        }
                        if (items.isNotEmpty() || currentDir != null) {
                            IconButton(onClick = { editMode = true }) { Icon(Icons.Default.Edit, "Edit") }
                        }
                        Button(onClick = onDownloadClick, modifier = Modifier.padding(start = 8.dp)) {
                            Text("Download")
                        }
                    }
                }
            }

            if (currentDir != null && !editMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        text = currentDir!!.name,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No media found here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(items, key = { it.absolutePath }) { file ->
                        ListItem(
                            modifier = Modifier.clickable {
                                if (editMode) {
                                    if (selectedFiles.contains(file)) selectedFiles -= file else selectedFiles += file
                                } else {
                                    onItemClick(file)
                                }
                            },
                            headlineContent = { headlineContent(file) },
                            supportingContent = { supportingContent(file) },
                            leadingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (editMode) {
                                        Checkbox(checked = selectedFiles.contains(file), onCheckedChange = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    leadingContent(file)
                                }
                            },
                            trailingContent = { trailingContent(file) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationFolderDialog(
    root: File,
    folders: List<File>,
    onDismiss: () -> Unit,
    onConfirm: (File) -> Unit
) {
    var selectedDest by remember { mutableStateOf<File?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Destination") },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                items(folders) { folder ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedDest = folder }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedDest == folder, onClick = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        val displayName = if (folder.absolutePath == root.absolutePath) "Root Directory"
                        else folder.absolutePath.removePrefix(root.absolutePath + "/")
                        Text(displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selectedDest?.let { onConfirm(it) } },
                enabled = selectedDest != null
            ) { Text("Confirm") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

