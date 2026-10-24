package com.mtdetc.picturewindow.settings

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.mtdetc.picturewindow.BuildConfig
import com.mtdetc.picturewindow.PictureWindowApp
import com.mtdetc.picturewindow.R
import com.mtdetc.picturewindow.sync.SyncScheduler
import com.mtdetc.picturewindow.update.AppUpdate
import com.mtdetc.picturewindow.update.UpdateInstaller

/**
 * Feed URL, clock on or off, sync status and a "Sync now" button. Also the screensaver's settings,
 * and the one place a newer version is offered: an Update button that downloads it and opens
 * Android's install prompt.
 */
class SettingsActivity : Activity() {
    private lateinit var settings: AppSettings
    private lateinit var feedUrl: EditText
    private lateinit var showClock: CheckBox
    private lateinit var status: TextView
    private lateinit var updateRow: View
    private lateinit var updateStatus: TextView
    private lateinit var updateButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings)
        settings = AppSettings(this)
        feedUrl = findViewById(R.id.feed_url)
        showClock = findViewById(R.id.show_clock)
        status = findViewById(R.id.status)
        feedUrl.setText(settings.feedUrl)
        showClock.isChecked = settings.showClock
        findViewById<Button>(R.id.save).setOnClickListener { save() }
        findViewById<Button>(R.id.sync_now).setOnClickListener { syncNow() }
        updateRow = findViewById(R.id.update_row)
        updateStatus = findViewById(R.id.update_status)
        updateButton = findViewById(R.id.update)
        findViewById<TextView>(R.id.version).text = getString(R.string.version, BuildConfig.VERSION_NAME)
        askToNotify()
    }

    override fun onResume() {
        super.onResume()
        showStatus()
        showUpdate()
        SyncScheduler.syncIfStale(this, PictureWindowApp.from(this).store.readSyncState().lastSuccessAt)
    }

    private fun save() {
        settings.feedUrl = feedUrl.text.toString()
        settings.showClock = showClock.isChecked
        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
    }

    private fun syncNow() {
        save()
        SyncScheduler.syncSoon(this, delayMs = 0)
        Toast.makeText(this, R.string.sync_started, Toast.LENGTH_SHORT).show()
    }

    private fun showStatus() {
        val app = PictureWindowApp.from(this)
        val (cachedCount, catalogCount) = app.library.counts()
        status.text = getString(R.string.status, cachedCount, catalogCount, app.store.readSyncState().lastMessage)
    }

    private fun showUpdate() {
        val update = PictureWindowApp.from(this).updates.available()
        updateRow.visibility = if (update != null) View.VISIBLE else View.GONE
        if (update == null) return
        updateStatus.text = getString(R.string.update_available, update.versionName)
        updateButton.isEnabled = true
        updateButton.setOnClickListener { startUpdate(update) }
    }

    private fun startUpdate(update: AppUpdate) {
        // Android asks once whether this app may install updates; the answer is a switch in Settings.
        if (!packageManager.canRequestPackageInstalls()) {
            Toast.makeText(this, R.string.update_allow_installs, Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return
        }
        updateButton.isEnabled = false
        updateStatus.text = getString(R.string.update_downloading, update.versionName)
        val app = PictureWindowApp.from(this)
        Thread({
            val started = try {
                UpdateInstaller(this, app.feedClient).downloadAndInstall(update)
            } catch (error: Exception) {
                false
            }
            runOnUiThread {
                if (started) return@runOnUiThread
                updateStatus.text = getString(R.string.update_failed)
                updateButton.isEnabled = true
            }
        }, "picture-window-update").start()
    }

    /** Android 13 and newer ask before an app may notify; earlier TVs allow it already. */
    private fun askToNotify() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
    }
}
