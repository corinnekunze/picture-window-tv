package com.mtdetc.picturewindow.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mtdetc.picturewindow.BuildConfig
import com.mtdetc.picturewindow.Config
import com.mtdetc.picturewindow.R
import com.mtdetc.picturewindow.feed.FeedClient
import com.mtdetc.picturewindow.settings.SettingsActivity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Checks once a day, with the sync, whether a newer release is out. A new version shows on the
 * settings screen and as one Android notification (on Google TV, under Settings → Notifications);
 * never on the screensaver. Debug builds don't check: they're signed with another key, so Android
 * would refuse the release as an update.
 */
class UpdateChecker(private val context: Context, private val client: FeedClient) {
    private val preferences = context.getSharedPreferences("updates", Context.MODE_PRIVATE)

    /** The newer version to offer, as of the last check, or null. */
    fun available(): AppUpdate? {
        val saved = preferences.getString(KEY_LATEST, null) ?: return null
        return updateToOffer(AppUpdateParser.parse(saved), BuildConfig.VERSION_CODE)
    }

    /** Best effort: a failed check keeps what the last one found and tries again tomorrow. */
    fun check() {
        if (BuildConfig.DEBUG) return
        val text = try {
            client.fetchSmallText(Config.UPDATE_URL) ?: return
        } catch (error: Exception) {
            Log.i(TAG, "Update check failed: ${error.message}")
            return
        }
        val latest = AppUpdateParser.parse(text) ?: return
        preferences.edit().putString(KEY_LATEST, Json.encodeToString(latest)).apply()
        updateToOffer(latest, BuildConfig.VERSION_CODE)?.let(::notifyOnce)
    }

    private fun notifyOnce(update: AppUpdate) {
        if (preferences.getInt(KEY_NOTIFIED_VERSION, 0) >= update.versionCode) return
        val notifications = context.getSystemService(NotificationManager::class.java)
        if (!notifications.areNotificationsEnabled()) return
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.update_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val openSettings = PendingIntent.getActivity(
            context, 0, Intent(context, SettingsActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_update)
            .setContentTitle(context.getString(R.string.update_notification_title, update.versionName))
            .setContentText(context.getString(R.string.update_notification_text))
            .setContentIntent(openSettings)
            .setAutoCancel(true)
            .build()
        notifications.notify(NOTIFICATION_ID, notification)
        preferences.edit().putInt(KEY_NOTIFIED_VERSION, update.versionCode).apply()
    }

    private companion object {
        const val TAG = "PictureWindowUpdate"
        const val KEY_LATEST = "latest"
        const val KEY_NOTIFIED_VERSION = "notified_version_code"
        const val CHANNEL_ID = "updates"
        const val NOTIFICATION_ID = 1
    }
}
