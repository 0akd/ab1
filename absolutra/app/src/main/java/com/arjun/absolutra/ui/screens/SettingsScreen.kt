package com.arjun.absolutra.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import com.arjun.absolutra.data.APP_ROOT_DIR_KEY
import com.arjun.absolutra.data.dataStore
import com.arjun.absolutra.data.migrateAppRootIfNeeded
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val rootDirUriStr by context.dataStore.data.map { it[APP_ROOT_DIR_KEY] }.collectAsState(initial = null)

    LaunchedEffect(Unit) {
        context.migrateAppRootIfNeeded()
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(selectedUri, takeFlags)

            scope.launch {
                context.dataStore.edit { prefs ->
                    prefs[APP_ROOT_DIR_KEY] = selectedUri.toString()
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Absolutra Root Storage", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Select a root folder to store your Absolutra data. A master folder named 'absolutra' will be created inside to hold both Canvas Boards and Todo videos safely isolated.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Current Root Location:",
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = rootDirUriStr ?: "No location selected",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (rootDirUriStr == null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { folderPickerLauncher.launch(null) }) {
                    Text(if (rootDirUriStr == null) "Select Root Folder" else "Change Root Folder")
                }
            }
        }
    }
}
