package com.arjun.absolutra.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.arjun.absolutra.data.KB_KEY_SPACING_HORIZONTAL_KEY
import com.arjun.absolutra.data.KB_KEY_SPACING_VERTICAL_KEY
import com.arjun.absolutra.data.KB_MARGIN_BOTTOM_KEY
import com.arjun.absolutra.data.KB_MARGIN_LEFT_KEY
import com.arjun.absolutra.data.KB_MARGIN_RIGHT_KEY
import com.arjun.absolutra.data.KB_MARGIN_TOP_KEY
import com.arjun.absolutra.data.KB_ROW_HEIGHT_KEY
import com.arjun.absolutra.data.KB_ROW_SPACING_KEY
import com.arjun.absolutra.data.KB_WIDTH_LEFT_KEY
import com.arjun.absolutra.data.KB_WIDTH_MIDDLE_KEY
import com.arjun.absolutra.data.KB_WIDTH_RIGHT_KEY
import com.arjun.absolutra.data.dataStore
import kotlinx.coroutines.launch

@Composable
fun KeyboardSettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val imm = remember { context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager }
    var testText by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val prefs by context.dataStore.data.collectAsState(initial = emptyPreferences())

    val marginTop = prefs[KB_MARGIN_TOP_KEY] ?: 8
    val marginBottom = prefs[KB_MARGIN_BOTTOM_KEY] ?: 8
    val marginLeft = prefs[KB_MARGIN_LEFT_KEY] ?: 0
    val marginRight = prefs[KB_MARGIN_RIGHT_KEY] ?: 0
    val rowSpacing = prefs[KB_ROW_SPACING_KEY] ?: 0
    val horizontalSpacing = prefs[KB_KEY_SPACING_HORIZONTAL_KEY] ?: 8
    val verticalSpacing = prefs[KB_KEY_SPACING_VERTICAL_KEY] ?: 8
    val rowHeight = prefs[KB_ROW_HEIGHT_KEY] ?: 50
    val widthLeft = prefs[KB_WIDTH_LEFT_KEY] ?: 1f
    val widthMiddle = prefs[KB_WIDTH_MIDDLE_KEY] ?: 1f
    val widthRight = prefs[KB_WIDTH_RIGHT_KEY] ?: 1f

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Custom Keyboard Setup", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Your custom Compose-powered Android keyboard needs to be enabled in your system settings before it can be used.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Step 1: Enable the Keyboard", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = {
                    val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                    context.startActivity(intent)
                }) {
                    Text("Open Keyboard Settings")
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(text = "Step 2: Set as Default", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { imm.showInputMethodPicker() }) {
                    Text("Choose Default Keyboard")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Dimensions & Styling", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))

                SettingSlider("Top Margin", marginTop.toFloat(), 0f..300f) { v ->
                    scope.launch { context.dataStore.edit { it[KB_MARGIN_TOP_KEY] = v.toInt() } }
                }
                SettingSlider("Bottom Margin", marginBottom.toFloat(), 0f..300f) { v ->
                    scope.launch { context.dataStore.edit { it[KB_MARGIN_BOTTOM_KEY] = v.toInt() } }
                }
                SettingSlider("Left Margin", marginLeft.toFloat(), 0f..300f) { v ->
                    scope.launch { context.dataStore.edit { it[KB_MARGIN_LEFT_KEY] = v.toInt() } }
                }
                SettingSlider("Right Margin", marginRight.toFloat(), 0f..300f) { v ->
                    scope.launch { context.dataStore.edit { it[KB_MARGIN_RIGHT_KEY] = v.toInt() } }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Key Spacing (0 = touching)", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))

                SettingSlider("Key Horizontal Spacing", horizontalSpacing.toFloat(), 0f..200f) { v ->
                    scope.launch { context.dataStore.edit { it[KB_KEY_SPACING_HORIZONTAL_KEY] = v.toInt() } }
                }
                SettingSlider("Key Vertical Spacing", verticalSpacing.toFloat(), 0f..200f) { v ->
                    scope.launch { context.dataStore.edit { it[KB_KEY_SPACING_VERTICAL_KEY] = v.toInt() } }
                }
                SettingSlider("Between Row Padding", rowSpacing.toFloat(), 0f..200f) { v ->
                    scope.launch { context.dataStore.edit { it[KB_ROW_SPACING_KEY] = v.toInt() } }
                }

                Spacer(modifier = Modifier.height(8.dp))
                SettingSlider("Row Height", rowHeight.toFloat(), 10f..300f) { v ->
                    scope.launch { context.dataStore.edit { it[KB_ROW_HEIGHT_KEY] = v.toInt() } }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Key Width Groupings", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))

                SettingSlider("Left Keys Width", widthLeft, 0.1f..10f) { v ->
                    scope.launch { context.dataStore.edit { it[KB_WIDTH_LEFT_KEY] = v } }
                }
                SettingSlider("Middle Keys Width", widthMiddle, 0.1f..10f) { v ->
                    scope.launch { context.dataStore.edit { it[KB_WIDTH_MIDDLE_KEY] = v } }
                }
                SettingSlider("Right Keys Width", widthRight, 0.1f..10f) { v ->
                    scope.launch { context.dataStore.edit { it[KB_WIDTH_RIGHT_KEY] = v } }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(text = "Step 3: Test it", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = testText,
            onValueChange = { testText = it },
            label = { Text("Tap here to test the keyboard") },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun SettingSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(String.format("%.1f", value), style = MaterialTheme.typography.bodyMedium)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}
