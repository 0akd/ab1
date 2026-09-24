package com.arjun.absolutra.ui.screens.todo

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TodoPriorityPanel(
    task: Task,
    globalParams: List<PriorityParameter>,
    onUpdateTask: (Task) -> Unit,
    onAddGlobalParam: (PriorityParameter) -> Unit,
    onUpdateGlobalParam: (PriorityParameter) -> Unit,
    onDeleteGlobalParam: (PriorityParameter) -> Unit,
    onDismiss: () -> Unit
) {
    var newParamName by remember { mutableStateOf("") }
    var newParamMin by remember { mutableStateOf("0") }
    var newParamMax by remember { mutableStateOf("10") }

    var paramWithOptions by remember { mutableStateOf<PriorityParameter?>(null) }
    var paramToUpdate by remember { mutableStateOf<PriorityParameter?>(null) }
    var paramToDelete by remember { mutableStateOf<PriorityParameter?>(null) }

    var updateParamName by remember { mutableStateOf("") }
    var updateParamMin by remember { mutableStateOf("") }
    var updateParamMax by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Priority Settings",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )

            val netPriority = task.getNetPriority(globalParams)
            Text(
                text = "Net Priority: ${"%.1f".format(netPriority)}%",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            HorizontalDivider()

            LazyColumn(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .padding(vertical = 16.dp)
            ) {
                if (globalParams.isEmpty()) {
                    item {
                        Text(
                            "No parameters defined yet. Create one below!",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                items(globalParams) { param ->
                    val safeValues = task.getSafePriorityValues()
                    val currentValue = (safeValues[param.id] ?: param.min).coerceIn(param.min, param.max)
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {},
                                    onLongClick = { paramWithOptions = param }
                                )
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = param.name, style = MaterialTheme.typography.bodyLarge)
                            Text(text = "%.1f".format(currentValue))
                        }
                        Slider(
                            value = currentValue,
                            onValueChange = { newVal ->
                                val newValues = safeValues.toMutableMap()
                                newValues[param.id] = newVal
                                onUpdateTask(task.copy(priorityValues = newValues))
                            },
                            valueRange = param.min..param.max
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = param.min.toString(), style = MaterialTheme.typography.labelSmall)
                            Text(text = param.max.toString(), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            HorizontalDivider()

            Text(
                text = "Create New Parameter",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newParamName,
                    onValueChange = { newParamName = it },
                    label = { Text("Name") },
                    modifier = Modifier.weight(2f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = newParamMin,
                    onValueChange = { newParamMin = it },
                    label = { Text("Min") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = newParamMax,
                    onValueChange = { newParamMax = it },
                    label = { Text("Max") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            Button(
                onClick = {
                    val min = newParamMin.toFloatOrNull() ?: 0f
                    val max = newParamMax.toFloatOrNull() ?: 10f
                    if (newParamName.isNotBlank() && max > min) {
                        onAddGlobalParam(PriorityParameter(name = newParamName.trim(), min = min, max = max))
                        newParamName = ""
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text("Add Parameter")
            }
        }
    }

    if (paramWithOptions != null) {
        AlertDialog(
            onDismissRequest = { paramWithOptions = null },
            title = { Text("Parameter Options") },
            text = { Text("What would you like to do with '${paramWithOptions?.name}'?") },
            confirmButton = {
                TextButton(onClick = {
                    paramToUpdate = paramWithOptions
                    updateParamName = paramWithOptions?.name ?: ""
                    updateParamMin = paramWithOptions?.min?.toString() ?: ""
                    updateParamMax = paramWithOptions?.max?.toString() ?: ""
                    paramWithOptions = null
                }) { Text("Update") }
            },
            dismissButton = {
                TextButton(onClick = {
                    paramToDelete = paramWithOptions
                    paramWithOptions = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            }
        )
    }

    if (paramToUpdate != null) {
        AlertDialog(
            onDismissRequest = { paramToUpdate = null },
            title = { Text("Update Parameter") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = updateParamName,
                        onValueChange = { updateParamName = it },
                        label = { Text("Name") },
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = updateParamMin,
                            onValueChange = { updateParamMin = it },
                            label = { Text("Min") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = updateParamMax,
                            onValueChange = { updateParamMax = it },
                            label = { Text("Max") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val min = updateParamMin.toFloatOrNull() ?: 0f
                    val max = updateParamMax.toFloatOrNull() ?: 10f
                    if (updateParamName.isNotBlank() && max > min) {
                        paramToUpdate?.let {
                            onUpdateGlobalParam(it.copy(name = updateParamName.trim(), min = min, max = max))
                        }
                    }
                    paramToUpdate = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { paramToUpdate = null }) { Text("Cancel") }
            }
        )
    }

    if (paramToDelete != null) {
        AlertDialog(
            onDismissRequest = { paramToDelete = null },
            title = { Text("Delete Parameter") },
            text = { Text("Are you sure you want to delete '${paramToDelete?.name}'?") },
            confirmButton = {
                TextButton(onClick = {
                    paramToDelete?.let { onDeleteGlobalParam(it) }
                    paramToDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { paramToDelete = null }) { Text("Cancel") }
            }
        )
    }
}
