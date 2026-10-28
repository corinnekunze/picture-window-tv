package com.mtdetc.picturewindow

import android.app.Application
import android.content.Context
import com.mtdetc.picturewindow.feed.FeedClient
import com.mtdetc.picturewindow.rotation.PhotoLibrary
import com.mtdetc.picturewindow.rotation.Rotation
import com.mtdetc.picturewindow.settings.AppSettings
import com.mtdetc.picturewindow.store.PhotoStore
import com.mtdetc.picturewindow.sync.SyncScheduler
import com.mtdetc.picturewindow.sync.Syncer
import com.mtdetc.picturewindow.update.UpdateChecker
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class PictureWindowApp : Application() {
    val store by lazy { PhotoStore(filesDir) }
    val library by lazy { PhotoLibrary(store, Rotation(), intervalMs = { AppSettings(this).rotationIntervalMs }) }
    val feedClient by lazy { FeedClient(httpClient()) }
    val syncer by lazy {
        Syncer(
            store,
            feedClient,
            ::cacheBudgetBytes,
            wantVideos = { VideoSupport.canPlay4k },
            keepCurrentPhotos = { AppSettings(this).keepCurrentPhotos },
        )
    }
    val updates by lazy { UpdateChecker(this, feedClient) }

    override fun onCreate() {
        super.onCreate()
        SyncScheduler.ensureScheduled(this)
    }

    /** The configured budget, shrunk if the TV's storage is running low. */
    private fun cacheBudgetBytes(): Long {
        val ourBytes = store.bytesOnDisk()
        val spareBytes = filesDir.usableSpace - Config.MIN_FREE_BYTES
        return minOf(Config.CACHE_BUDGET_BYTES, ourBytes + spareBytes).coerceAtLeast(0)
    }

    private fun httpClient() = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    companion object {
        fun from(context: Context) = context.applicationContext as PictureWindowApp
    }
}
