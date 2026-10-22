package com.mtdetc.picturewindow

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mtdetc.picturewindow.settings.AppSettings

/**
 * Debug builds only. Sets how often the photo changes, or which feed to read, for evaluating photos
 * on a real TV:
 *
 *     adb shell am broadcast -n com.mtdetc.picturewindow/.DebugReceiver --el seconds 10
 *     adb shell am broadcast -n com.mtdetc.picturewindow/.DebugReceiver --el seconds 0   # back to the default (3 minutes)
 *     adb shell am broadcast -n com.mtdetc.picturewindow/.DebugReceiver --es feed http://…/index.json
 *     adb shell am broadcast -n com.mtdetc.picturewindow/.DebugReceiver --es feed default
 *
 * Only the shell (which holds DUMP) can send it.
 */
class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val settings = AppSettings(context)
        intent.getStringExtra("feed")?.let { feed -> settings.feedUrl = if (feed == "default") Config.DEFAULT_FEED_URL else feed }
        if (intent.hasExtra("seconds")) {
            val seconds = intent.getLongExtra("seconds", 0)
            if (seconds <= 0) settings.resetRotationInterval() else settings.rotationIntervalMs = seconds * 1000
        }
        Log.i("PictureWindowDebug", "Rotation ${settings.rotationIntervalMs} ms, feed ${settings.feedUrl}")
    }
}
