package com.arjun.absolutra.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.datastore.preferences.core.edit
import androidx.documentfile.provider.DocumentFile
import com.arjun.absolutra.data.APP_ROOT_DIR_KEY
import com.arjun.absolutra.data.dataStore
import com.arjun.absolutra.data.migrateAppRootIfNeeded
import com.arjun.absolutra.data.migrateLegacyTasksIfNeeded
import com.arjun.absolutra.ui.screens.todo.PRIORITY_PARAMS_KEY
import com.arjun.absolutra.ui.screens.todo.PriorityParameter
import com.arjun.absolutra.ui.screens.todo.TASKS_KEY
import com.arjun.absolutra.ui.screens.todo.Task
import com.arjun.absolutra.ui.screens.todo.TodoApp
import com.arjun.absolutra.ui.screens.todo.VideoBoardDetail
import com.arjun.absolutra.ui.screens.todo.getOrCreateTaskDir
import com.arjun.absolutra.ui.screens.todo.gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun TodoScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val rootDirUriStr by context.dataStore.data.map { it[APP_ROOT_DIR_KEY] }.collectAsState(initial = null)

    LaunchedEffect(Unit) {
        context.migrateAppRootIfNeeded()
        context.migrateLegacyTasksIfNeeded()
    }

    val tasksJson by context.dataStore.data.map { prefs -> prefs[TASKS_KEY] ?: "[]" }.collectAsState(initial = "[]")
    val tasks = remember(tasksJson) {
        val type = object : TypeToken<List<Task>>() {}.type
        gson.fromJson<List<Task>>(tasksJson, type) ?: emptyList()
    }

    val paramsJson by context.dataStore.data.map { prefs -> prefs[PRIORITY_PARAMS_KEY] ?: "[]" }.collectAsState(initial = "[]")
    val globalParams = remember(paramsJson) {
        val type = object : TypeToken<List<PriorityParameter>>() {}.type
        gson.fromJson<List<PriorityParameter>>(paramsJson, type) ?: emptyList()
    }

    fun saveTasks(newTasks: List<Task>) {
        scope.launch { context.dataStore.edit { prefs -> prefs[TASKS_KEY] = gson.toJson(newTasks) } }
    }

    fun saveParams(newParams: List<PriorityParameter>) {
        scope.launch { context.dataStore.edit { prefs -> prefs[PRIORITY_PARAMS_KEY] = gson.toJson(newParams) } }
    }

    var selectedTask by remember { mutableStateOf<Task?>(null) }

    BackHandler(enabled = selectedTask != null) { selectedTask = null }

    val rootDir = rootDirUriStr
    Box(modifier = modifier.fillMaxSize()) {
        if (rootDir.isNullOrEmpty()) {
            NoRootFolderUI()
        } else if (selectedTask == null) {
            TodoApp(
                tasks = tasks,
                globalParams = globalParams,
                onAddTask = { title -> saveTasks(tasks + Task(title = title)) },
                onUpdateTask = { updatedTask ->
                    saveTasks(tasks.map { if (it.id == updatedTask.id) updatedTask else it })
                },
                onDeleteTask = { task ->
                    saveTasks(tasks.filter { it.id != task.id })
                    scope.launch(Dispatchers.IO) {
                        val rootUri = Uri.parse(rootDir)
                        val baseDir = DocumentFile.fromTreeUri(context, rootUri)
                        baseDir?.findFile("absolutra")?.findFile("todo")?.findFile(task.id)?.delete()
                    }
                },
                onTaskClick = { task -> selectedTask = task },
                onAddGlobalParam = { newParam -> saveParams(globalParams + newParam) },
                onUpdateGlobalParam = { updatedParam ->
                    saveParams(globalParams.map { if (it.id == updatedParam.id) updatedParam else it })
                },
                onDeleteGlobalParam = { paramToDelete ->
                    saveParams(globalParams.filter { it.id != paramToDelete.id })
                }
            )
        } else {
            val task = selectedTask
            var taskDir by remember(task?.id) { mutableStateOf<DocumentFile?>(null) }

            LaunchedEffect(task?.id) {
                if (task != null) {
                    taskDir = getOrCreateTaskDir(context, rootDir, task.id)
                }
            }

            if (task != null && taskDir != null) {
                VideoBoardDetail(
                    task = task,
                    board = taskDir!!,
                    onBack = { selectedTask = null }
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
