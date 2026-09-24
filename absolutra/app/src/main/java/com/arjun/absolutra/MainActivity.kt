package com.arjun.absolutra

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.arjun.absolutra.ui.MainScreen
import com.arjun.absolutra.ui.theme.AbsolutraTheme

class MainActivity : ComponentActivity() {
    private var askedForOverlay = false

    companion object {
        var wasInForegroundWhenScreenTurnedOff = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Allow the activity to show even if the device is locked
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        enableEdgeToEdge()
        setContent {
            AbsolutraTheme {
                MainScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        wasInForegroundWhenScreenTurnedOff = false
        checkOverlayPermissionAndStartService()
    }

    override fun onPause() {
        super.onPause()
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wasInForegroundWhenScreenTurnedOff = !powerManager.isInteractive
    }

    private fun checkOverlayPermissionAndStartService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                if (!askedForOverlay) {
                    askedForOverlay = true
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
            } else {
                startScreenService()
            }
        } else {
            startScreenService()
        }
    }

    private fun startScreenService() {
        val serviceIntent = Intent(this, ScreenService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}
