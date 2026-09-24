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

val APP_ROOT_DIR_KEY = stringPreferencesKey("app_root_directory_uri")
private val CANVAS_ROOT_DIR_KEY = stringPreferencesKey("canvas_root_directory_uri")
private val LEGACY_CANVAS_DIR_KEY = stringPreferencesKey("canvas_directory_uri")
private val TASKS_KEY = stringPreferencesKey("tasks")

suspend fun Context.migrateAppRootIfNeeded() {
    val prefs = dataStore.data.first()
    if (prefs[APP_ROOT_DIR_KEY] != null) return

    val legacyDir = prefs[CANVAS_ROOT_DIR_KEY] ?: prefs[LEGACY_CANVAS_DIR_KEY] ?: return
    dataStore.edit { it[APP_ROOT_DIR_KEY] = legacyDir }
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

val KB_MARGIN_TOP_KEY = intPreferencesKey("kb_margin_top")
val KB_MARGIN_BOTTOM_KEY = intPreferencesKey("kb_margin_bottom")
val KB_MARGIN_LEFT_KEY = intPreferencesKey("kb_margin_left")
val KB_MARGIN_RIGHT_KEY = intPreferencesKey("kb_margin_right")
val KB_ROW_SPACING_KEY = intPreferencesKey("kb_row_spacing")
val KB_KEY_SPACING_HORIZONTAL_KEY = intPreferencesKey("kb_key_spacing_horizontal")
val KB_KEY_SPACING_VERTICAL_KEY = intPreferencesKey("kb_key_spacing_vertical")
val KB_ROW_HEIGHT_KEY = intPreferencesKey("kb_row_height")
val KB_WIDTH_LEFT_KEY = floatPreferencesKey("kb_width_left")
val KB_WIDTH_MIDDLE_KEY = floatPreferencesKey("kb_width_middle")
val KB_WIDTH_RIGHT_KEY = floatPreferencesKey("kb_width_right")
