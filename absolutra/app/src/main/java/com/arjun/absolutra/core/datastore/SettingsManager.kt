package com.arjun.absolutra.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "downloader_settings")

data class FrameState(
    val folder: String?,
    val index: Int,
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
    val taskId: Int? = null,
    val viewedFrames: Set<String> = emptySet(),
    val viewedCount: Int = 0
)

class SettingsManager(private val context: Context) {
    companion object {
        val UI_SCALE_KEY = floatPreferencesKey("ui_scale")
        val THEME_MODE_KEY = stringPreferencesKey("theme_mode")

        const val DEFAULT_UI_SCALE = 1.0f
        const val DEFAULT_THEME_MODE = "system"

        val LAST_FRAME_FOLDER_KEY = stringPreferencesKey("last_frame_folder")
        val LAST_FRAME_INDEX_KEY = intPreferencesKey("last_frame_index")
        val LAST_FRAME_SCALE_KEY = floatPreferencesKey("last_frame_scale")
        val LAST_FRAME_OFFSET_X_KEY = floatPreferencesKey("last_frame_offset_x")
        val LAST_FRAME_OFFSET_Y_KEY = floatPreferencesKey("last_frame_offset_y")
        val TASK_ID_KEY = intPreferencesKey("last_frame_task_id")
        val VIEWED_FRAMES_KEY = stringSetPreferencesKey("last_frame_viewed_frames")
        val VIEWED_COUNT_KEY = intPreferencesKey("last_frame_viewed_count")
    }

    val uiScaleFlow: Flow<Float> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { preferences -> preferences[UI_SCALE_KEY] ?: DEFAULT_UI_SCALE }

    val themeModeFlow: Flow<String> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { preferences -> preferences[THEME_MODE_KEY] ?: DEFAULT_THEME_MODE }

    suspend fun saveUiScale(scale: Float) {
        context.dataStore.edit { preferences -> preferences[UI_SCALE_KEY] = scale }
    }

    suspend fun saveThemeMode(mode: String) {
        context.dataStore.edit { preferences -> preferences[THEME_MODE_KEY] = mode }
    }

    val frameStateFlow: Flow<FrameState> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { pref ->
            FrameState(
                folder = pref[LAST_FRAME_FOLDER_KEY],
                index = pref[LAST_FRAME_INDEX_KEY] ?: 0,
                scale = pref[LAST_FRAME_SCALE_KEY] ?: 1f,
                offsetX = pref[LAST_FRAME_OFFSET_X_KEY] ?: 0f,
                offsetY = pref[LAST_FRAME_OFFSET_Y_KEY] ?: 0f,
                taskId = pref[TASK_ID_KEY],
                viewedFrames = pref[VIEWED_FRAMES_KEY] ?: emptySet(),
                viewedCount = pref[VIEWED_COUNT_KEY] ?: 0
            )
        }

    suspend fun saveFrameState(
        folder: String?,
        index: Int,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
        taskId: Int? = null,
        viewedFrames: Set<String> = emptySet(),
        viewedCount: Int = 0
    ) {
        context.dataStore.edit { pref ->
            if (folder == null) {
                pref.remove(LAST_FRAME_FOLDER_KEY)
                pref.remove(LAST_FRAME_INDEX_KEY)
                pref.remove(LAST_FRAME_SCALE_KEY)
                pref.remove(LAST_FRAME_OFFSET_X_KEY)
                pref.remove(LAST_FRAME_OFFSET_Y_KEY)
                pref.remove(TASK_ID_KEY)
                pref.remove(VIEWED_FRAMES_KEY)
                pref.remove(VIEWED_COUNT_KEY)
            } else {
                pref[LAST_FRAME_FOLDER_KEY] = folder
                pref[LAST_FRAME_INDEX_KEY] = index
                pref[LAST_FRAME_SCALE_KEY] = scale
                pref[LAST_FRAME_OFFSET_X_KEY] = offsetX
                pref[LAST_FRAME_OFFSET_Y_KEY] = offsetY
                if (taskId != null) pref[TASK_ID_KEY] = taskId else pref.remove(TASK_ID_KEY)
                if (viewedFrames.isNotEmpty()) {
                    pref[VIEWED_FRAMES_KEY] = if (viewedFrames.size > 500) {
                        viewedFrames.toList().takeLast(500).toSet()
                    } else {
                        viewedFrames
                    }
                } else {
                    pref.remove(VIEWED_FRAMES_KEY)
                }
                pref[VIEWED_COUNT_KEY] = viewedCount.coerceAtLeast(0)
            }
        }
    }
}
