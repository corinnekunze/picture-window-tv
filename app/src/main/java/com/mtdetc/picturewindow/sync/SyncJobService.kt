package com.mtdetc.picturewindow.sync

import android.app.job.JobParameters
import android.app.job.JobService
import android.util.Log
import com.mtdetc.picturewindow.PictureWindowApp
import com.mtdetc.picturewindow.settings.AppSettings
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs a sync off the main thread. One service instance can host the daily job and a "sync now" job
 * at once, so each job gets its own stop flag. An unfinished sync asks JobScheduler to retry with its
 * backoff rather than rescheduling itself, because rescheduling a running job stops it.
 */
class SyncJobService : JobService() {
    private val stopFlags = ConcurrentHashMap<Int, AtomicBoolean>()

    override fun onStartJob(params: JobParameters): Boolean {
        val stopRequested = AtomicBoolean(false)
        stopFlags[params.jobId] = stopRequested
        Thread({ runSync(params, stopRequested) }, "picture-window-sync-${params.jobId}").start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        stopFlags.remove(params.jobId)?.set(true)
        return true
    }

    private fun runSync(params: JobParameters, stopRequested: AtomicBoolean) {
        val app = PictureWindowApp.from(this)
        val result = app.syncer.sync(AppSettings(this).feedUrl) { !stopRequested.get() }
        Log.i(TAG, "Job ${params.jobId}: ${result.message}")
        if (!stopRequested.get()) app.updates.check()
        if (stopRequested.get()) return
        stopFlags.remove(params.jobId)
        jobFinished(params, !result.complete)
    }

    private companion object {
        const val TAG = "PictureWindowSync"
    }
}
