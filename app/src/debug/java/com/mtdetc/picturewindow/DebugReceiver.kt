package com.mtdetc.picturewindow

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mtdetc.picturewindow.settings.AppSettings

/**
 * Debug builds only. Sets how often the photo changes, for evaluating photos on a real TV:
 *
 *     adb shell am broadcast -n com.mtdetc.picturewindow/.DebugReceiver --el seconds 10
 *     adb shell am broadcast -n com.mtdetc.picturewindow/.DebugReceiver --el seconds 0   # back to 5 minutes
 *     adb shell am broadcast -n com.mtdetc.picturewindow/.DebugReceiver --ez split true   # 1080p | 4K
 *
 * Only the shell (which holds DUMP) can send it.
 */
class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val settings = AppSettings(context)
        if (intent.hasExtra("split")) settings.splitCompare = intent.getBooleanExtra("split", false)
        if (intent.hasExtra("seconds")) {
            val seconds = intent.getLongExtra("seconds", 0)
            if (seconds <= 0) settings.resetRotationInterval() else settings.rotationIntervalMs = seconds * 1000
        }
        Log.i("PictureWindowDebug", "Rotation ${settings.rotationIntervalMs} ms, split ${settings.splitCompare}")
    }
}
