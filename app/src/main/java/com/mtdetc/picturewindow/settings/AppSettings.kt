package com.mtdetc.picturewindow.settings

import android.content.Context
import com.mtdetc.picturewindow.Config

/** What a viewer can change on the settings screen. */
class AppSettings(context: Context) {
    private val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var feedUrl: String
        get() = preferences.getString(KEY_FEED_URL, null) ?: Config.DEFAULT_FEED_URL
        set(value) = preferences.edit().putString(KEY_FEED_URL, value.trim()).apply()

    var showClock: Boolean
        get() = preferences.getBoolean(KEY_SHOW_CLOCK, true)
        set(value) = preferences.edit().putBoolean(KEY_SHOW_CLOCK, value).apply()

    /** Off by default. On, the TV keeps the photos it has and stops getting new ones (see Syncer). */
    var keepCurrentPhotos: Boolean
        get() = preferences.getBoolean(KEY_KEEP_CURRENT_PHOTOS, false)
        set(value) = preferences.edit().putBoolean(KEY_KEEP_CURRENT_PHOTOS, value).apply()

    /** Not on the settings screen; debug builds can shorten it for testing (see DebugReceiver). */
    var rotationIntervalMs: Long
        get() = preferences.getLong(KEY_ROTATION_INTERVAL_MS, Config.ROTATION_INTERVAL_MS)
        set(value) = preferences.edit().putLong(KEY_ROTATION_INTERVAL_MS, value).apply()

    fun resetRotationInterval() = preferences.edit().remove(KEY_ROTATION_INTERVAL_MS).apply()

    private companion object {
        const val KEY_FEED_URL = "feed_url"
        const val KEY_SHOW_CLOCK = "show_clock"
        const val KEY_KEEP_CURRENT_PHOTOS = "keep_current_photos"
        const val KEY_ROTATION_INTERVAL_MS = "rotation_interval_ms"
    }
}
