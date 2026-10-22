package com.mtdetc.picturewindow.sync

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import com.mtdetc.picturewindow.Config

/** A daily sync, plus a one-off "sync now". Unfinished syncs retry through the job's backoff. */
object SyncScheduler {
    private const val DAILY_JOB_ID = 1
    private const val SYNC_NOW_JOB_ID = 2
    private const val RETRY_BACKOFF_MS = 10 * 60 * 1000L
    private const val STALE_GRACE_MS = 2 * 60 * 60 * 1000L

    fun ensureScheduled(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        if (scheduler.getPendingJob(DAILY_JOB_ID) == null) {
            scheduler.schedule(
                baseJob(context, DAILY_JOB_ID).setPeriodic(Config.SYNC_INTERVAL_MS).build(),
            )
            syncSoon(context, delayMs = 0)
        }
    }

    /**
     * Some TVs (TCL's "auto-start" gate) refuse to start background jobs for apps the owner hasn't
     * allowed, so the screensaver and settings screen also sync whenever the last success is stale.
     */
    fun syncIfStale(context: Context, lastSuccessAt: Long, now: Long = System.currentTimeMillis()) {
        if (now - lastSuccessAt < Config.SYNC_INTERVAL_MS + STALE_GRACE_MS) return
        // Rescheduling a job that's waiting or running would restart it, so leave it be.
        if (context.getSystemService(JobScheduler::class.java).getPendingJob(SYNC_NOW_JOB_ID) != null) return
        syncSoon(context, delayMs = 0)
    }

    fun syncSoon(context: Context, delayMs: Long) {
        context.getSystemService(JobScheduler::class.java).schedule(
            baseJob(context, SYNC_NOW_JOB_ID).setMinimumLatency(delayMs).build(),
        )
    }

    private fun baseJob(context: Context, jobId: Int) =
        JobInfo.Builder(jobId, ComponentName(context, SyncJobService::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .setBackoffCriteria(RETRY_BACKOFF_MS, JobInfo.BACKOFF_POLICY_LINEAR)
}
