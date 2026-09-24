package com.arjun.absolutra.service

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle

class RecordPermissionActivity : Activity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mpm = getSystemService(MediaProjectionManager::class.java)
        startActivityForResult(mpm.createScreenCaptureIntent(), REQUEST_CAPTURE)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CAPTURE) {
            val duration = intent.getIntExtra("duration", 5)
            val serviceIntent = if (resultCode == RESULT_OK && data != null) {
                Intent(this, ScreenshotService::class.java).apply {
                    action = "START_RECORD_AREA"
                    putExtra("duration", duration)
                    putExtra("resultCode", resultCode)
                    putExtra("data", data)
                }
            } else {
                Intent(this, ScreenshotService::class.java).apply {
                    action = "CANCEL_RECORD"
                }
            }
            startService(serviceIntent)
        }
        finish()
    }

    companion object {
        private const val REQUEST_CAPTURE = 1001
    }
}
