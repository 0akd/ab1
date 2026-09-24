package com.arjun.absolutra.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

val Context.dataStore by preferencesDataStore(name = "app_prefs")
private val Context.legacyTasksStore by preferencesDataStore(name = "tasks_prefs")

val CANVAS_ROOT_DIR_KEY = stringPreferencesKey("canvas_root_directory_uri")
private val LEGACY_CANVAS_DIR_KEY = stringPreferencesKey("canvas_directory_uri")
private val TASKS_KEY = stringPreferencesKey("tasks")

suspend fun Context.migrateCanvasRootIfNeeded() {
    val prefs = dataStore.data.first()
    if (prefs[CANVAS_ROOT_DIR_KEY] != null) return
    val legacyDir = prefs[LEGACY_CANVAS_DIR_KEY] ?: return
    dataStore.edit { it[CANVAS_ROOT_DIR_KEY] = legacyDir }
}

suspend fun Context.migrateLegacyTasksIfNeeded() {
    if (dataStore.data.first()[TASKS_KEY] != null) return
    val legacyTasks = legacyTasksStore.data.first()[TASKS_KEY] ?: return
    dataStore.edit { prefs ->
        prefs[TASKS_KEY] = legacyTasks
    }
}

val BUTTON_SIZE_KEY = intPreferencesKey("button_size")
val BUTTON_OPACITY_KEY = floatPreferencesKey("button_opacity")
val BUTTON_SHAPE_ROUNDED_KEY = booleanPreferencesKey("button_shape_rounded")
