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

    private companion object {
        const val KEY_FEED_URL = "feed_url"
        const val KEY_SHOW_CLOCK = "show_clock"
    }
}
