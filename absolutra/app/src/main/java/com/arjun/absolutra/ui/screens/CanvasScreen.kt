package com.arjun.absolutra.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.arjun.absolutra.data.CANVAS_ROOT_DIR_KEY
import com.arjun.absolutra.data.dataStore
import com.arjun.absolutra.data.migrateCanvasRootIfNeeded
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class PickerAction {
    object Add : PickerAction()
    data class Replace(val target: DocumentFile) : PickerAction()
    data class InsertAt(val index: Int) : PickerAction()
}

fun getFileValue(name: String): Double {
    val numStr = name.removePrefix("canvas_").removeSuffix(".jpg")
    return numStr.toDoubleOrNull() ?: 0.0
}

private fun hasImageReadAccess(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val fullAccess = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_IMAGES
        ) == PackageManager.PERMISSION_GRANTED
        val selectedAccess = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            ) == PackageManager.PERMISSION_GRANTED
        fullAccess || selectedAccess
    } else {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }
}

@Composable
fun CanvasScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val rootDirUriStr by context.dataStore.data.map { it[CANVAS_ROOT_DIR_KEY] }.collectAsState(initial = null)

    var selectedBoard by remember { mutableStateOf<DocumentFile?>(null) }

    LaunchedEffect(Unit) {
        context.migrateCanvasRootIfNeeded()
    }

    BackHandler(enabled = selectedBoard != null) {
        selectedBoard = null
    }

    Box(modifier = modifier.fillMaxSize()) {
        val rootDir = rootDirUriStr
        if (rootDir.isNullOrEmpty()) {
            NoRootFolderUI()
        } else if (selectedBoard == null) {
            CanvasBoardList(
                rootDirUriStr = rootDir,
                onBoardSelected = { selectedBoard = it }
            )
        } else {
            CanvasBoardDetail(
                board = selectedBoard!!,
                onBack = { selectedBoard = null }
            )
        }
    }
}

@Composable
fun NoRootFolderUI() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "No Storage Location Selected",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Please navigate to Settings and select a root folder to start creating Canvas Boards.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun CanvasBoardList(rootDirUriStr: String, onBoardSelected: (DocumentFile) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var boards by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var boardName by remember { mutableStateOf("") }

    LaunchedEffect(rootDirUriStr) {
        isLoading = true
        boards = loadBoards(context, rootDirUriStr)
        isLoading = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (boards.isEmpty() && !isLoading) {
            Text(
                "No boards yet. Click '+' to create one.",
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(boards, key = { it.uri.toString() }) { board ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clickable { onBoardSelected(board) },
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = "Folder",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(board.name ?: "Unnamed Board", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        FloatingActionButton(
            onClick = {
                boardName = ""
                showAddDialog = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Board")
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("New Canvas Board") },
            text = {
                OutlinedTextField(
                    value = boardName,
                    onValueChange = { boardName = it },
                    label = { Text("Board Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (boardName.isNotBlank()) {
                            val name = boardName.trim()
                            scope.launch {
                                isLoading = true
                                showAddDialog = false
                                createBoard(context, rootDirUriStr, name)
                                boards = loadBoards(context, rootDirUriStr)
                                isLoading = false
                            }
                        }
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasBoardDetail(board: DocumentFile, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var images by remember { mutableStateOf(emptyList<DocumentFile>()) }
    var isLoading by remember { mutableStateOf(false) }

    var currentPickerAction by remember { mutableStateOf<PickerAction?>(null) }
    var hasImageAccess by remember { mutableStateOf(hasImageReadAccess(context)) }

    var insertMode by remember { mutableStateOf(false) }

    LaunchedEffect(board) {
        isLoading = true
        images = loadImagesFromBoard(board)
        isLoading = false
    }

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        // A partial photo grant denies READ_MEDIA_IMAGES while still allowing the selected images.
        hasImageAccess = hasImageReadAccess(context)
        if (!hasImageAccess) {
            currentPickerAction = null
        }
    }

    fun launchPicker(action: PickerAction) {
        currentPickerAction = action
        if (!hasImageReadAccess(context)) {
            permissionLauncher.launch(permission)
        } else {
            hasImageAccess = true
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
                text = board.name ?: "Board",
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
            if (images.isEmpty() && !isLoading) {
                if (insertMode) {
                    Box(modifier = Modifier.align(Alignment.Center)) {
                        InsertBetweenButton(onClick = { launchPicker(PickerAction.Add) })
                    }
                } else {
                    Text(
                        "No images in this board. Click 'Edit' to add.",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (insertMode && images.isNotEmpty()) {
                        item {
                            InsertBetweenButton(onClick = { launchPicker(PickerAction.InsertAt(0)) })
                        }
                    }

                    itemsIndexed(images, key = { _, file -> file.uri.toString() }) { index, file ->
                        CanvasImageItem(
                            file = file,
                            isFirst = index == 0,
                            isLast = index == images.lastIndex,
                            onDelete = {
                                scope.launch {
                                    isLoading = true
                                    deleteImage(file)
                                    images = loadImagesFromBoard(board)
                                    isLoading = false
                                }
                            },
                            onMoveUp = {
                                scope.launch {
                                    isLoading = true
                                    swapImages(file, images[index - 1])
                                    images = loadImagesFromBoard(board)
                                    isLoading = false
                                }
                            },
                            onMoveDown = {
                                scope.launch {
                                    isLoading = true
                                    swapImages(file, images[index + 1])
                                    images = loadImagesFromBoard(board)
                                    isLoading = false
                                }
                            },
                            onReplace = { launchPicker(PickerAction.Replace(file)) }
                        )

                        if (insertMode) {
                            InsertBetweenButton(onClick = { launchPicker(PickerAction.InsertAt(index + 1)) })
                        }
                    }
                }
            }

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }

    if (currentPickerAction != null && hasImageAccess) {
        ImagePickerSheet(
            onDismiss = { currentPickerAction = null },
            onImagesSelected = { uris ->
                val action = currentPickerAction
                currentPickerAction = null
                scope.launch {
                    isLoading = true
                    if (uris.isNotEmpty()) {
                        when (action) {
                            is PickerAction.Add -> {
                                val baseTime = System.currentTimeMillis().toDouble()
                                uris.forEachIndexed { i, uri ->
                                    saveImageToBoard(context, uri, board, baseTime + (i * 1000.0))
                                }
                            }
                            is PickerAction.Replace -> {
                                replaceImageInBoard(context, uris.first(), action.target)
                            }
                            is PickerAction.InsertAt -> {
                                val index = action.index
                                val prevValue = if (index > 0) getFileValue(images[index - 1].name ?: "") else null
                                val nextValue = if (index < images.size) getFileValue(images[index].name ?: "") else null

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
                                    saveImageToBoard(context, uri, board, newValue)
                                }
                            }
                            null -> {}
                        }
                    }
                    images = loadImagesFromBoard(board)
                    isLoading = false
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagePickerSheet(
    onDismiss: () -> Unit,
    onImagesSelected: (List<Uri>) -> Unit
) {
    val context = LocalContext.current
    var images by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val selectedUris = remember { mutableStateListOf<Uri>() }

    LaunchedEffect(Unit) {
        images = loadDeviceImages(context)
    }

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
                    text = if (selectedUris.isEmpty()) {
                        "Tap for one, long press for multiple"
                    } else {
                        "${selectedUris.size} selected"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (selectedUris.isNotEmpty()) {
                    TextButton(onClick = { onImagesSelected(selectedUris.toList()) }) {
                        Text("Done")
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(4.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(images) { uri ->
                    val index = selectedUris.indexOf(uri)
                    val isSelected = index != -1

                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .aspectRatio(1f)
                            .pointerInput(uri) {
                                detectTapGestures(
                                    onLongPress = {
                                        if (uri !in selectedUris) selectedUris.add(uri)
                                    },
                                    onTap = {
                                        if (selectedUris.isNotEmpty()) {
                                            if (uri in selectedUris) selectedUris.remove(uri)
                                            else selectedUris.add(uri)
                                        } else {
                                            onImagesSelected(listOf(uri))
                                        }
                                    }
                                )
                            }
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(uri)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Picker Image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
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

suspend fun loadDeviceImages(context: Context): List<Uri> = withContext(Dispatchers.IO) {
    val uris = mutableListOf<Uri>()
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DATE_ADDED
    )
    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

    context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projection,
        null,
        null,
        sortOrder
    )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            uris.add(contentUri)
        }
    }
    uris
}

@Composable
fun CanvasImageItem(
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clickable { showOverlay = !showOverlay }
    ) {
        val request = remember(file.uri, file.lastModified()) {
            ImageRequest.Builder(context)
                .data(file.uri)
                .memoryCacheKey("${file.uri}_${file.lastModified()}")
                .diskCacheKey("${file.uri}_${file.lastModified()}")
                .build()
        }

        AsyncImage(
            model = request,
            contentDescription = "Canvas Image",
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.FillWidth
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
                    IconButton(onClick = {
                        showOverlay = false
                        onDelete()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
                    }
                    if (!isFirst) {
                        IconButton(onClick = {
                            showOverlay = false
                            onMoveUp()
                        }) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move Up", tint = Color.White)
                        }
                    }
                    if (!isLast) {
                        IconButton(onClick = {
                            showOverlay = false
                            onMoveDown()
                        }) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move Down", tint = Color.White)
                        }
                    }
                    IconButton(onClick = {
                        showOverlay = false
                        onReplace()
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Replace", tint = Color.White)
                    }
                    IconButton(onClick = {
                        showOverlay = false
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = file.type ?: "image/*"
                            putExtra(Intent.EXTRA_STREAM, file.uri)
                            clipData = ClipData.newRawUri("Canvas Image", file.uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        val chooser = Intent.createChooser(shareIntent, "Share Image").apply {
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(chooser)
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                    }
                }
            }
        }
    }
}

suspend fun loadBoards(context: Context, rootDirUriStr: String): List<DocumentFile> {
    return withContext(Dispatchers.IO) {
        try {
            val rootUri = Uri.parse(rootDirUriStr)
            val rootDir = DocumentFile.fromTreeUri(context, rootUri) ?: return@withContext emptyList()
            rootDir.listFiles()
                .filter { it.isDirectory }
                .sortedBy { it.name?.lowercase() ?: "" }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}

suspend fun createBoard(context: Context, rootDirUriStr: String, name: String) {
    withContext(Dispatchers.IO) {
        try {
            val rootUri = Uri.parse(rootDirUriStr)
            val rootDir = DocumentFile.fromTreeUri(context, rootUri) ?: return@withContext
            rootDir.createDirectory(name)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
fun InsertBetweenButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            )
            Icon(
                Icons.Default.Add,
                contentDescription = "Insert Image",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            )
        }
    }
}

suspend fun loadImagesFromBoard(boardDir: DocumentFile): List<DocumentFile> {
    return withContext(Dispatchers.IO) {
        try {
            boardDir.listFiles()
                .filter { it.isFile && (it.type?.startsWith("image/") == true) }
                .sortedBy { getFileValue(it.name ?: "") }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}

suspend fun saveImageToBoard(
    context: Context,
    sourceUri: Uri,
    boardDir: DocumentFile,
    newValue: Double? = null
) {
    withContext(Dispatchers.IO) {
        try {
            val value = newValue ?: System.currentTimeMillis().toDouble()
            val valueStr = java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
            val fileName = "canvas_${valueStr}.jpg"
            val newFile = boardDir.createFile("image/jpeg", fileName) ?: return@withContext

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

suspend fun deleteImage(file: DocumentFile) {
    withContext(Dispatchers.IO) {
        try {
            file.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

suspend fun swapImages(file1: DocumentFile, file2: DocumentFile) {
    withContext(Dispatchers.IO) {
        try {
            val name1 = file1.name ?: return@withContext
            val name2 = file2.name ?: return@withContext

            val tempName = "temp_swap_${System.currentTimeMillis()}.jpg"
            if (file1.renameTo(tempName)) {
                if (file2.renameTo(name1)) {
                    file1.renameTo(name2)
                } else {
                    file1.renameTo(name1)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

suspend fun replaceImageInBoard(context: Context, sourceUri: Uri, targetFile: DocumentFile) {
    withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                context.contentResolver.openOutputStream(targetFile.uri, "wt")?.use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
