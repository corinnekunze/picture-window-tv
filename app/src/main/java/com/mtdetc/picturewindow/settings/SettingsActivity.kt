package com.mtdetc.picturewindow.settings

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.mtdetc.picturewindow.PictureWindowApp
import com.mtdetc.picturewindow.R
import com.mtdetc.picturewindow.sync.SyncScheduler

/** Feed URL, clock on or off, sync status and a "Sync now" button. Also the screensaver's settings. */
class SettingsActivity : Activity() {
    private lateinit var settings: AppSettings
    private lateinit var feedUrl: EditText
    private lateinit var showClock: CheckBox
    private lateinit var status: TextView

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
    }

    override fun onResume() {
        super.onResume()
        showStatus()
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
}
