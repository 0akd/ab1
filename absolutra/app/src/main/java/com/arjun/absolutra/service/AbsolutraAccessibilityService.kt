package com.arjun.absolutra.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.Settings
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi

class AbsolutraAccessibilityService : AccessibilityService() {

    companion object {
        var instance: AbsolutraAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun captureScreen(onCaptured: (Bitmap?) -> Unit) {
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshotResult: ScreenshotResult) {
                    val buffer = screenshotResult.hardwareBuffer
                    val hardware = Bitmap.wrapHardwareBuffer(buffer, screenshotResult.colorSpace)
                    val software = hardware?.copy(Bitmap.Config.ARGB_8888, false)
                    hardware?.recycle()
                    buffer.close()
                    onCaptured(software)
                }

                override fun onFailure(errorCode: Int) {
                    onCaptured(null)
                }
            }
        )
    }
}

fun Context.isAbsolutraAccessibilityEnabled(): Boolean {
    val expected = ComponentName(this, AbsolutraAccessibilityService::class.java).flattenToString()
    val enabled = Settings.Secure.getString(
        contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
}
