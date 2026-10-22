package com.mtdetc.picturewindow.rotation

import com.mtdetc.picturewindow.feed.FeedItem
import com.mtdetc.picturewindow.store.PhotoStore
import java.io.File

/** [video] is the photo's 4K video when the TV has it; the screensaver prefers it to [file]. */
data class CurrentPhoto(val item: FeedItem, val file: File, val video: File?, val since: Long, val nextChangeAt: Long)

/**
 * The single source of "the photo on screen now", shared by the screensaver and the provider that
 * CleanYourTV's Home reads. The rotation advances lazily when someone asks.
 */
class PhotoLibrary(
    private val store: PhotoStore,
    private val rotation: Rotation,
    private val intervalMs: () -> Long,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun current(): CurrentPhoto? = synchronized(store.lock) {
        val cachedIds = store.cachedPhotos()
        val available = store.readCatalog().entries.filter { entry -> entry.item.id in cachedIds }
        val previous = store.readRotation()
        val interval = intervalMs()
        val state = rotation.advanceIfDue(previous, available, clock(), interval)
        if (state != previous) store.writeRotation(state)

        val entry = available.firstOrNull { candidate -> candidate.item.id == state.currentId } ?: return null
        val video = store.videoFile(entry.item.id).takeIf(File::exists)
        CurrentPhoto(entry.item, store.photoFile(entry.item.id), video, state.currentSince, state.currentSince + interval)
    }

    /** Looks up any cached photo by id, so a reader can open the exact photo it queried. */
    fun cachedPhoto(id: String): File? = synchronized(store.lock) {
        if (!safeIdPattern.matches(id)) return null
        val file = store.photoFile(id)
        if (file.exists()) file else null
    }

    fun counts(): Pair<Int, Int> = synchronized(store.lock) {
        store.cachedPhotos().size to store.readCatalog().entries.size
    }

    private companion object {
        /** Ids arrive from other apps through the provider, so no path characters get through. */
        val safeIdPattern = Regex("^[A-Za-z0-9-]{1,100}$")
    }
}
