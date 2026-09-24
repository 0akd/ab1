package com.arjun.absolutra.ui.screens.todo

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

@Composable
fun TodoApp(
    tasks: List<Task>,
    globalParams: List<PriorityParameter>,
    onAddTask: (String) -> Unit,
    onUpdateTask: (Task) -> Unit,
    onDeleteTask: (Task) -> Unit,
    onTaskClick: (Task) -> Unit,
    onAddGlobalParam: (PriorityParameter) -> Unit,
    onUpdateGlobalParam: (PriorityParameter) -> Unit,
    onDeleteGlobalParam: (PriorityParameter) -> Unit,
    modifier: Modifier = Modifier
) {
    var newTaskText by remember { mutableStateOf("") }
    var taskToUpdate by remember { mutableStateOf<Task?>(null) }
    var taskToDelete by remember { mutableStateOf<Task?>(null) }
    var taskWithOptions by remember { mutableStateOf<Task?>(null) }
    var taskForPriority by remember { mutableStateOf<Task?>(null) }
    var updateText by remember { mutableStateOf("") }

    val sortedTasks = remember(tasks, globalParams) {
        tasks.sortedWith(
            compareBy<Task> { it.isCompleted }
                .thenByDescending { it.getNetPriority(globalParams) }
        )
    }

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
            ) { Text("Add") }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(items = sortedTasks, key = { task -> task.id }) { task ->
                TaskRow(
                    task = task,
                    globalParams = globalParams,
                    onCheckedChange = { isChecked -> onUpdateTask(task.copy(isCompleted = isChecked)) },
                    onLongClick = { taskWithOptions = task },
                    onClick = { onTaskClick(task) },
                    onPriorityClick = { taskForPriority = task }
                )
            }
        }
    }

    if (taskForPriority != null) {
        TodoPriorityPanel(
            task = taskForPriority!!,
            globalParams = globalParams,
            onUpdateTask = { updated ->
                onUpdateTask(updated)
                taskForPriority = updated
            },
            onAddGlobalParam = onAddGlobalParam,
            onUpdateGlobalParam = onUpdateGlobalParam,
            onDeleteGlobalParam = onDeleteGlobalParam,
            onDismiss = { taskForPriority = null }
        )
    }

    if (taskWithOptions != null) {
        AlertDialog(
            onDismissRequest = { taskWithOptions = null },
            title = { Text("Task Options") },
            text = { Text("What would you like to do with '${taskWithOptions?.title}'?") },
            confirmButton = {
                TextButton(onClick = {
                    taskToUpdate = taskWithOptions
                    updateText = taskWithOptions?.title ?: ""
                    taskWithOptions = null
                }) { Text("Update") }
            },
            dismissButton = {
                TextButton(onClick = {
                    taskToDelete = taskWithOptions
                    taskWithOptions = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            }
        )
    }

    if (taskToUpdate != null) {
        AlertDialog(
            onDismissRequest = { taskToUpdate = null },
            title = { Text("Update Task") },
            text = {
                OutlinedTextField(
                    value = updateText,
                    onValueChange = { updateText = it },
                    label = { Text("Task Title") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (updateText.isNotBlank()) {
                        taskToUpdate?.let { onUpdateTask(it.copy(title = updateText.trim())) }
                    }
                    taskToUpdate = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { taskToUpdate = null }) { Text("Cancel") }
            }
        )
    }

    if (taskToDelete != null) {
        AlertDialog(
            onDismissRequest = { taskToDelete = null },
            title = { Text("Delete Task") },
            text = {
                Text("Are you sure you want to delete '${taskToDelete?.title}'? This will permanently delete all videos inside it.")
            },
            confirmButton = {
                TextButton(onClick = {
                    taskToDelete?.let { onDeleteTask(it) }
                    taskToDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { taskToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskRow(
    task: Task,
    globalParams: List<PriorityParameter>,
    onCheckedChange: (Boolean) -> Unit,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onPriorityClick: () -> Unit
) {
    val netPriority = task.getNetPriority(globalParams)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = task.isCompleted, onCheckedChange = onCheckedChange)
        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null
            )
            if (globalParams.isNotEmpty()) {
                Text(
                    text = "Priority: ${"%.1f".format(netPriority)}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (netPriority > 70f) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
        }

        IconButton(onClick = onPriorityClick) {
            Icon(Icons.Default.Settings, contentDescription = "Priority Settings")
        }
    }
}
