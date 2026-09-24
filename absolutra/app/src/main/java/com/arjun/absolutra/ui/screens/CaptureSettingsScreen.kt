package com.arjun.absolutra.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.arjun.absolutra.data.BUTTON_OPACITY_KEY
import com.arjun.absolutra.data.BUTTON_SHAPE_ROUNDED_KEY
import com.arjun.absolutra.data.BUTTON_SIZE_KEY
import com.arjun.absolutra.data.dataStore
import com.arjun.absolutra.service.AbsolutraAccessibilityService
import com.arjun.absolutra.service.ScreenshotService
import com.arjun.absolutra.service.isAbsolutraAccessibilityEnabled
import kotlinx.coroutines.launch

@Composable
fun CaptureSettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var isEnabled by remember { mutableStateOf(ScreenshotService.isRunning) }
    var accessibilityOn by remember { mutableStateOf(context.isAbsolutraAccessibilityEnabled()) }
    var pendingEnable by remember { mutableStateOf(false) }

    val prefs by context.dataStore.data.collectAsState(initial = null)
    val currentSize = prefs?.get(BUTTON_SIZE_KEY) ?: 56
    val currentOpacity = prefs?.get(BUTTON_OPACITY_KEY) ?: 0.5f
    val isRounded = prefs?.get(BUTTON_SHAPE_ROUNDED_KEY) ?: true

    fun startCaptureService() {
        val serviceIntent = Intent(context, ScreenshotService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
        isEnabled = true
        pendingEnable = false
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            accessibilityOn = context.isAbsolutraAccessibilityEnabled()
            val overlayReady = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
            if (pendingEnable && accessibilityOn && overlayReady) {
                startCaptureService()
            } else if (!ScreenshotService.isRunning) {
                isEnabled = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Smart Screen Capture", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Enable once, then use the floating button to drag a vertical selection.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Floating Button Toggle", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = if (isEnabled) "Active" else "Inactive",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { checked ->
                        if (!checked) {
                            pendingEnable = false
                            context.startService(
                                Intent(context, ScreenshotService::class.java).apply { action = "STOP" }
                            )
                            isEnabled = false
                            return@Switch
                        }
                        val overlayReady = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                            Settings.canDrawOverlays(context)
                        if (!overlayReady) {
                            pendingEnable = true
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                            return@Switch
                        }
                        if (!context.isAbsolutraAccessibilityEnabled()) {
                            pendingEnable = true
                            accessibilityOn = false
                            context.openCaptureAccessibilitySettings()
                            return@Switch
                        }
                        startCaptureService()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Button Appearance", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(16.dp))

                Text("Size: ${currentSize}dp", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = currentSize.toFloat(),
                    onValueChange = { newValue ->
                        scope.launch { context.dataStore.edit { it[BUTTON_SIZE_KEY] = newValue.toInt() } }
                    },
                    valueRange = 40f..100f
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Idle Opacity: ${(currentOpacity * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall
                )
                Slider(
                    value = currentOpacity,
                    onValueChange = { newValue ->
                        scope.launch { context.dataStore.edit { it[BUTTON_OPACITY_KEY] = newValue } }
                    },
                    valueRange = 0.1f..1.0f
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Use Rounded Square Shape", style = MaterialTheme.typography.bodySmall)
                    Switch(
                        checked = isRounded,
                        onCheckedChange = { checked ->
                            scope.launch {
                                context.dataStore.edit { it[BUTTON_SHAPE_ROUNDED_KEY] = checked }
                            }
                        }
                    )
                }
            }
        }
    }
}

private fun Context.openCaptureAccessibilitySettings() {
    val component = ComponentName(this, AbsolutraAccessibilityService::class.java)
    val details = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").apply {
        putExtra(Intent.EXTRA_COMPONENT_NAME, component)
    }
    runCatching { startActivity(details) }
        .onFailure { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
}
