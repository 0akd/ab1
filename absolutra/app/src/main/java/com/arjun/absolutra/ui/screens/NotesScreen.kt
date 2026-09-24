package com.arjun.absolutra.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay

@Composable
fun NotesScreen(modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf("") }
    var lastSyncedText by remember { mutableStateOf("") }

    val db = remember { FirebaseFirestore.getInstance() }
    val clipboardManager = LocalClipboardManager.current

    DisposableEffect(Unit) {
        val listener = db.collection("notes").document("shared_note")
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener

                if (snapshot != null && snapshot.exists()) {
                    val remoteText = snapshot.getString("text") ?: ""
                    // Only apply a cloud update when there is no unsynced local typing.
                    if (text == lastSyncedText && text != remoteText) {
                        text = remoteText
                        lastSyncedText = remoteText
                    }
                }
            }
        onDispose { listener.remove() }
    }

    LaunchedEffect(text) {
        if (text == lastSyncedText) return@LaunchedEffect

        delay(1000)

        val textToSync = text
        // Mark synced before the write so our own snapshot does not replace newer keystrokes.
        lastSyncedText = textToSync

        val noteData = hashMapOf(
            "text" to textToSync,
            "timestamp" to FieldValue.serverTimestamp()
        )
        db.collection("notes").document("shared_note").set(noteData)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Cloud Clipboard",
                style = MaterialTheme.typography.titleLarge
            )
            Button(
                onClick = {
                    clipboardManager.setText(AnnotatedString(text))
                }
            ) {
                Text("Copy")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxSize(),
            label = { Text("Start typing...") },
            textStyle = MaterialTheme.typography.bodyLarge
        )
    }
}
