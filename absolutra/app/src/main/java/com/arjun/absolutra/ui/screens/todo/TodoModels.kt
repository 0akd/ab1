package com.arjun.absolutra.ui.screens.todo

import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import java.util.UUID

val gson = Gson()
val TASKS_KEY = stringPreferencesKey("tasks")
val PRIORITY_PARAMS_KEY = stringPreferencesKey("priority_params")

data class PriorityParameter(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val min: Float,
    val max: Float
)

data class Task(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val isCompleted: Boolean = false,
    val priorityValues: Map<String, Float>? = emptyMap()
) {
    fun getSafePriorityValues(): Map<String, Float> = priorityValues ?: emptyMap()

    fun getNetPriority(globalParams: List<PriorityParameter>): Float {
        if (globalParams.isEmpty()) return 0f
        var totalScore = 0f
        var maxPossible = 0f

        val safeMap = getSafePriorityValues()
        for (param in globalParams) {
            val value = safeMap[param.id] ?: param.min
            totalScore += value
            maxPossible += param.max
        }

        return if (maxPossible == 0f) 0f else (totalScore / maxPossible) * 100f
    }
}
