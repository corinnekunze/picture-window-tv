package com.mtdetc.picturewindow.sync

import com.mtdetc.picturewindow.feed.DownloadOutcome
import com.mtdetc.picturewindow.feed.FeedClient
import com.mtdetc.picturewindow.feed.FeedIndex
import com.mtdetc.picturewindow.feed.IndexResult
import com.mtdetc.picturewindow.store.CacheStep
import com.mtdetc.picturewindow.store.CachePlanner
import com.mtdetc.picturewindow.store.Catalog
import com.mtdetc.picturewindow.store.CatalogEntry
import com.mtdetc.picturewindow.store.PhotoStore
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean

/** [complete] false means "try again later". */
data class SyncResult(val complete: Boolean, val downloaded: Int, val corrupt: Int, val message: String)

/**
 * One sync: refresh the catalog from the feed (skipped on 304), drop revoked and stale photos, then
 * download within the budget. Any problem with the index or a manifest keeps the old catalog: fail
 * closed. A single bad photo is skipped, not fatal.
 */
class Syncer(
    private val store: PhotoStore,
    private val client: FeedClient,
    private val budgetBytes: () -> Long,
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
            refreshCatalog(feedUrl, previous.takeIf { state -> state.feedUrl == feedUrl }?.etag)
            val result = fillCache(shouldContinue)
            store.writeSyncState(store.readSyncState().copy(lastAttemptAt = attemptAt, lastSuccessAt = clock(), lastMessage = result.message))
            result
        } catch (error: Exception) {
            val message = "Sync failed: ${error.message ?: error.javaClass.simpleName}"
            store.writeSyncState(store.readSyncState().copy(lastAttemptAt = attemptAt, lastMessage = message))
            SyncResult(complete = false, downloaded = 0, corrupt = 0, message = message)
        }
    }

    private fun refreshCatalog(feedUrl: String, etag: String?) {
        val result = client.fetchIndex(feedUrl, etag)
        if (result !is IndexResult.Fetched) return
        val catalog = buildCatalog(feedUrl, result.index)
        synchronized(store.lock) {
            store.writeCatalog(catalog)
            pruneRotationHistory(catalog)
            store.writeSyncState(store.readSyncState().copy(feedUrl = feedUrl, etag = result.etag))
        }
    }

    private fun buildCatalog(feedUrl: String, index: FeedIndex): Catalog {
        val revoked = index.revoked.toSet()
        val seenIds = mutableSetOf<String>()
        val entries = index.batches.sortedByDescending { batch -> batch.id }.flatMap { batch ->
            val manifest = client.fetchManifest(URI(feedUrl).resolve(batch.manifest).toString(), batch.sha256)
            manifest.items
                .filter { item -> item.id !in revoked && seenIds.add(item.id) }
                .map { item -> CatalogEntry(batch.id, item) }
        }
        return Catalog(entries)
    }

    private fun pruneRotationHistory(catalog: Catalog) {
        val catalogIds = catalog.entries.map { entry -> entry.item.id }.toSet()
        val rotation = store.readRotation()
        store.writeRotation(rotation.copy(lastShown = rotation.lastShown.filterKeys { id -> id in catalogIds }))
    }

    private fun fillCache(shouldContinue: () -> Boolean): SyncResult {
        val steps = planSteps()
        var downloaded = 0
        var corrupt = 0
        for (step in steps) {
            if (!shouldContinue()) return SyncResult(false, downloaded, corrupt, "Stopped early; $downloaded downloaded")
            when (step) {
                is CacheStep.Evict -> evict(step.id)
                is CacheStep.Download -> when (download(step)) {
                    DownloadOutcome.SAVED -> downloaded++
                    DownloadOutcome.CORRUPT -> corrupt++
                }
            }
        }
        val (cachedCount, catalogCount) = synchronized(store.lock) {
            store.cachedPhotos().size to store.readCatalog().entries.size
        }
        return SyncResult(true, downloaded, corrupt, "$cachedCount of $catalogCount photos on the TV" + corruptNote(corrupt))
    }

    private fun planSteps(): List<CacheStep> = synchronized(store.lock) {
        val catalog = store.readCatalog()
        val rotation = store.readRotation()
        store.deleteStrayPartials(catalog.entries.map { entry -> entry.item.id }.toSet())
        CachePlanner.plan(catalog, store.cachedPhotos(), rotation.lastShown, rotation.currentId, budgetBytes())
    }

    private fun evict(id: String) = synchronized(store.lock) {
        // The rotation may have moved onto this photo since the plan was made. Revoked and stale
        // photos go regardless; the rotation won't serve a photo that's left the catalog.
        val isOnScreen = store.readRotation().currentId == id
        val isInCatalog = store.readCatalog().entries.any { entry -> entry.item.id == id }
        if (isOnScreen && isInCatalog) return@synchronized
        store.deletePhoto(id)
    }

    private fun download(step: CacheStep.Download): DownloadOutcome {
        val id = step.item.id
        return client.download(step.item, store.partialFile(id), store.photoFile(id))
    }

    private fun corruptNote(corrupt: Int) = if (corrupt == 0) "" else "; $corrupt failed their checksum and were skipped"
}
