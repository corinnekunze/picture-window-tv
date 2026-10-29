package com.mtdetc.picturewindow.sync

import com.mtdetc.picturewindow.feed.BatchRef
import com.mtdetc.picturewindow.feed.DownloadOutcome
import com.mtdetc.picturewindow.feed.FeedClient
import com.mtdetc.picturewindow.feed.FeedIndex
import com.mtdetc.picturewindow.feed.FeedItem
import com.mtdetc.picturewindow.feed.IndexResult
import com.mtdetc.picturewindow.feed.file
import com.mtdetc.picturewindow.feed.image
import com.mtdetc.picturewindow.store.CacheStep
import com.mtdetc.picturewindow.store.CachePlanner
import com.mtdetc.picturewindow.store.Catalog
import com.mtdetc.picturewindow.store.CatalogEntry
import com.mtdetc.picturewindow.store.PhotoStore
import java.io.IOException
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean

/** [complete] false means "try again later". */
data class SyncResult(val complete: Boolean, val downloaded: Int, val corrupt: Int, val message: String)

/**
 * One sync: refresh the catalog from the feed (skipped on 304), drop revoked and stale photos, then
 * download within the budget. A problem with the index keeps the old catalog: fail closed. A batch
 * whose manifest can't be read or doesn't match keeps the photos it had (they were checked when they
 * arrived), and revocations apply regardless, so one bad batch never holds back the others or a
 * removal. The sync then counts as unfinished and tries again. A single bad photo is skipped, not fatal.
 *
 * When [wantVideos] (the TV can decode 4K HEVC), each photo's video comes with it, budgeted with the
 * image. A missing or bad video only costs the 4K version; the screensaver falls back to the image.
 *
 * When [keepCurrentPhotos] is on (new photos turned off in settings), the TV keeps the photos it has:
 * the daily check still reads the index, so revoked photos still leave, but the catalog is cut down to
 * what's on the TV and nothing is downloaded. Photos whose batch has left the feed's six-month window
 * stay. A TV with no photos yet ignores the setting, so it never ends up with an empty screensaver.
 * The ETag isn't kept while frozen, so turning new photos back on rebuilds the catalog from the feed.
 */
class Syncer(
    private val store: PhotoStore,
    private val client: FeedClient,
    private val budgetBytes: () -> Long,
    private val wantVideos: () -> Boolean = { false },
    private val keepCurrentPhotos: () -> Boolean = { false },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val running = AtomicBoolean(false)

    fun sync(feedUrl: String, shouldContinue: () -> Boolean = { true }): SyncResult {
        // The sync already running does the work, so this one has nothing to retry.
        if (!running.compareAndSet(false, true)) return SyncResult(true, 0, 0, "A sync is already running")
        try {
            return syncOnce(feedUrl, shouldContinue)
        } finally {
            running.set(false)
        }
    }

    private fun syncOnce(feedUrl: String, shouldContinue: () -> Boolean): SyncResult {
        val previous = store.readSyncState()
        val attemptAt = clock()
        return try {
            val frozen = keepCurrentPhotos() && store.cachedPhotos().isNotEmpty()
            // A missing or unreadable catalog can't be trusted to be "not modified": fetch it all again.
            // A frozen TV always reads the whole index, so revocations reach it every day.
            val etag = previous.etag.takeIf { !frozen && previous.feedUrl == feedUrl && store.readCatalogOrNull() != null }
            val failedBatches = refreshCatalog(feedUrl, etag, frozen)
            val filled = fillCache(shouldContinue, frozen)
            val result = if (failedBatches.isEmpty()) filled else filled.copy(
                complete = false,
                message = filled.message + "; couldn't read batch ${failedBatches.joinToString()}, kept its photos",
            )
            val state = store.readSyncState().copy(lastAttemptAt = attemptAt, lastMessage = result.message)
            // A sync stopped partway isn't a success, so the TV still counts as stale and tries again.
            store.writeSyncState(if (result.complete) state.copy(lastSuccessAt = clock()) else state)
            result
        } catch (error: Exception) {
            val message = "Sync failed: ${error.message ?: error.javaClass.simpleName}"
            store.writeSyncState(store.readSyncState().copy(lastAttemptAt = attemptAt, lastMessage = message))
            SyncResult(complete = false, downloaded = 0, corrupt = 0, message = message)
        }
    }

    /** Returns the batches whose manifest couldn't be used this time. */
    private fun refreshCatalog(feedUrl: String, etag: String?, frozen: Boolean): List<String> {
        val result = client.fetchIndex(feedUrl, etag)
        if (result !is IndexResult.Fetched) return emptyList()
        val failedBatches = mutableListOf<String>()
        val catalog = if (frozen) frozenCatalog(result.index) else buildCatalog(feedUrl, result.index, store.readCatalog(), failedBatches)
        synchronized(store.lock) {
            store.writeCatalog(catalog)
            pruneRotationHistory(catalog)
            // Without the ETag the next sync fetches everything again, so the failed batches get retried
            // (and a TV that was frozen rebuilds its catalog from the feed once it isn't).
            val savedEtag = if (failedBatches.isEmpty() && !frozen) result.etag else null
            store.writeSyncState(store.readSyncState().copy(feedUrl = feedUrl, etag = savedEtag))
        }
        return failedBatches
    }

    private fun buildCatalog(feedUrl: String, index: FeedIndex, previous: Catalog, failedBatches: MutableList<String>): Catalog {
        val revoked = index.revoked.toSet()
        val seenIds = mutableSetOf<String>()
        val entries = index.batches.sortedByDescending { batch -> batch.id }.flatMap { batch ->
            batchItems(feedUrl, batch, previous, failedBatches)
                .filter { item -> item.id !in revoked && seenIds.add(item.id) }
                .map { item -> CatalogEntry(batch.id, item) }
        }
        return Catalog(entries)
    }

    /** The photos already on the TV, minus any the feed has revoked. Nothing new joins. */
    private fun frozenCatalog(index: FeedIndex): Catalog {
        val revoked = index.revoked.toSet()
        val cached = store.cachedPhotos()
        return Catalog(store.readCatalog().entries.filter { entry -> entry.item.id in cached && entry.item.id !in revoked })
    }

    private fun batchItems(feedUrl: String, batch: BatchRef, previous: Catalog, failedBatches: MutableList<String>): List<FeedItem> =
        try {
            client.fetchManifest(URI(feedUrl).resolve(batch.manifest).toString(), batch.sha256).items
        } catch (error: Exception) {
            failedBatches += batch.id
            previous.entries.filter { entry -> entry.batch == batch.id }.map { entry -> entry.item }
        }

    private fun pruneRotationHistory(catalog: Catalog) {
        val catalogIds = catalog.entries.map { entry -> entry.item.id }.toSet()
        val rotation = store.readRotation()
        store.writeRotation(rotation.copy(lastShown = rotation.lastShown.filterKeys { id -> id in catalogIds }))
    }

    private fun fillCache(shouldContinue: () -> Boolean, frozen: Boolean): SyncResult {
        // Frozen, the plan only removes photos (revoked, or over a shrunken budget); it never downloads,
        // not even videos for photos that arrived without one.
        val withVideos = wantVideos() && !frozen
        val steps = planSteps(withVideos).filter { step -> !frozen || step is CacheStep.Evict }
        var downloaded = 0
        var corrupt = 0
        var failed = 0
        var failedInARow = 0
        // One file failing (a server hiccup) shouldn't hold up the rest: skip it and let the next sync
        // retry it. Several in a row means the network is down, so stop and retry the whole sync later.
        fun attempt(download: () -> DownloadOutcome?): DownloadOutcome? = try {
            download().also { failedInARow = 0 }
        } catch (error: IOException) {
            failed++
            if (++failedInARow >= MAX_FAILURES_IN_A_ROW) throw error
            null
        }
        // Videos that failed their checksum this sync; they get another try on the next one.
        val badVideoIds = mutableSetOf<String>()
        for (step in steps) {
            if (!shouldContinue()) return SyncResult(false, downloaded, corrupt, "Stopped early; $downloaded downloaded")
            when (step) {
                is CacheStep.Evict -> evict(step.id)
                is CacheStep.Download -> when (attempt { downloadImage(step.item) }) {
                    DownloadOutcome.SAVED -> {
                        downloaded++
                        step.makeRoom.forEach(::evict)
                        if (withVideos && attempt { downloadVideo(step.item) } == DownloadOutcome.CORRUPT) {
                            corrupt++
                            badVideoIds += step.item.id
                        }
                    }
                    DownloadOutcome.CORRUPT -> corrupt++
                    null -> {}
                }
            }
        }
        if (withVideos) {
            for (item in photosMissingVideos().filter { item -> item.id !in badVideoIds }) {
                if (!shouldContinue()) return SyncResult(false, downloaded, corrupt, "Stopped early; $downloaded downloaded")
                if (attempt { downloadVideo(item) } == DownloadOutcome.CORRUPT) corrupt++
            }
        }
        val (cachedCount, catalogCount) = synchronized(store.lock) {
            store.cachedPhotos().size to store.readCatalog().entries.size
        }
        if (frozen) return SyncResult(true, downloaded, corrupt, "Keeping the $cachedCount photos on the TV; new photos are off")
        val failedNote = if (failed == 0) "" else "; $failed downloads failed and will be retried"
        return SyncResult(failed == 0, downloaded, corrupt, "$cachedCount of $catalogCount photos on the TV" + corruptNote(corrupt) + failedNote)
    }

    private fun planSteps(withVideos: Boolean): List<CacheStep> = synchronized(store.lock) {
        val catalog = store.readCatalog()
        val rotation = store.readRotation()
        store.deleteStrayPartials(catalog.entries.map { entry -> entry.item.id }.toSet())
        val sizeOf = { item: FeedItem -> item.bytes + if (withVideos) item.video?.bytes ?: 0 else 0 }
        CachePlanner.plan(catalog, store.cachedPhotos(), rotation.lastShown, rotation.currentId, budgetBytes(), sizeOf)
    }

    /** Photos on the TV whose video the feed offers but the TV doesn't have yet (e.g. added later). */
    private fun photosMissingVideos(): List<FeedItem> = synchronized(store.lock) {
        val cachedIds = store.cachedPhotos()
        store.readCatalog().entries.map { entry -> entry.item }
            .filter { item -> item.id in cachedIds && item.video != null && !store.videoFile(item.id).exists() }
    }

    private fun evict(id: String) = synchronized(store.lock) {
        // The rotation may have moved onto this photo since the plan was made. Revoked and stale
        // photos go regardless; the rotation won't serve a photo that's left the catalog.
        val isOnScreen = store.readRotation().currentId == id
        val isInCatalog = store.readCatalog().entries.any { entry -> entry.item.id == id }
        if (isOnScreen && isInCatalog) return@synchronized
        store.deletePhoto(id)
    }

    private fun downloadImage(item: FeedItem): DownloadOutcome =
        client.download(item.image, store.partialFile(item.id), store.photoFile(item.id))

    private fun downloadVideo(item: FeedItem): DownloadOutcome? {
        val video = item.video ?: return null
        return client.download(video.file, store.videoPartialFile(item.id), store.videoFile(item.id))
    }

    private fun corruptNote(corrupt: Int) = if (corrupt == 0) "" else "; $corrupt failed their checksum and were skipped"

    private companion object {
        const val MAX_FAILURES_IN_A_ROW = 3
    }
}
