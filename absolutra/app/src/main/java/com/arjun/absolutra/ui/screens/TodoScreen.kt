package com.arjun.absolutra.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.arjun.absolutra.data.dataStore
import com.arjun.absolutra.data.migrateLegacyTasksIfNeeded
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

data class Task(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    var isCompleted: Boolean = false
)

val gson = Gson()
val TASKS_KEY = stringPreferencesKey("tasks")

@Composable
fun TodoScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        context.migrateLegacyTasksIfNeeded()
    }

    val tasksJson by context.dataStore.data.map { prefs ->
        prefs[TASKS_KEY] ?: "[]"
    }.collectAsState(initial = "[]")

    val tasks = remember(tasksJson) {
        val type = object : TypeToken<List<Task>>() {}.type
        gson.fromJson<List<Task>>(tasksJson, type) ?: emptyList()
    }

    fun saveTasks(newTasks: List<Task>) {
        scope.launch {
            context.dataStore.edit { prefs ->
                prefs[TASKS_KEY] = gson.toJson(newTasks)
            }
        }
    }

    TodoApp(
        tasks = tasks,
        onAddTask = { title -> saveTasks(tasks + Task(title = title)) },
        onToggleTask = { task, isChecked ->
            saveTasks(tasks.map { if (it.id == task.id) it.copy(isCompleted = isChecked) else it })
        },
        onDeleteTask = { task -> saveTasks(tasks.filter { it.id != task.id }) },
        modifier = modifier
    )
}

@Composable
fun TodoApp(
    tasks: List<Task>,
    onAddTask: (String) -> Unit,
    onToggleTask: (Task, Boolean) -> Unit,
    onDeleteTask: (Task) -> Unit,
    modifier: Modifier = Modifier
) {
    var newTaskText by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newTaskText,
                onValueChange = { newTaskText = it },
                label = { Text("Add a new task") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (newTaskText.isNotBlank()) {
                        onAddTask(newTaskText.trim())
                        newTaskText = ""
                    }
                }
            ) {
                Text("Add")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(items = tasks, key = { task -> task.id }) { task ->
                TaskRow(
                    task = task,
                    onCheckedChange = { isChecked -> onToggleTask(task, isChecked) },
                    onDelete = { onDeleteTask(task) }
                )
            }
        }
    }
}

@Composable
fun TaskRow(
    task: Task,
    onCheckedChange: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = task.isCompleted, onCheckedChange = onCheckedChange)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = task.title,
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete Task",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}
