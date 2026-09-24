package com.arjun.absolutra

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ScreenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_SCREEN_ON) {
            // If the app was the one on the screen when the phone went to sleep,
            // Android will automatically resume it. We do NOT need to launch it again.
            if (MainActivity.wasInForegroundWhenScreenTurnedOff) {
                return
            }

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                // Bring the existing task to the front without destroying its current state.
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(launchIntent)
        }
    }
}
