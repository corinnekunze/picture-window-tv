package com.mtdetc.picturewindow.store

import com.mtdetc.picturewindow.feed.FeedItem
import com.mtdetc.picturewindow.rotation.RotationState
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** One feed photo and the batch it came from. Batch ids are "YYYY-MM", so they sort by age. */
@Serializable
data class CatalogEntry(val batch: String, val item: FeedItem)

/** Every photo the feed currently offers, newest batch first, revoked photos already removed. */
@Serializable
data class Catalog(val entries: List<CatalogEntry> = emptyList())

@Serializable
data class SyncState(
    val feedUrl: String? = null,
    val etag: String? = null,
    val lastAttemptAt: Long = 0,
    val lastSuccessAt: Long = 0,
    val lastMessage: String = "Not synced yet",
)

/**
 * Files under the app's private directory: the photos, plus small JSON files for the catalog, the
 * rotation and the last sync. JSON writes go to a temp file that's synced to storage before it's
 * renamed into place, so a power cut leaves either the old file or the new one, never an empty one.
 */
class PhotoStore(private val root: File) {
    /** Held while reading or changing the catalog, the rotation or which photos exist. */
    val lock = Any()

    private val photosDir = File(root, "photos")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var cachedCatalog: Catalog? = null
    private var cachedCatalogStamp = -1L

    init {
        photosDir.mkdirs()
    }

    fun photoFile(id: String) = File(photosDir, "$id.webp")

    fun partialFile(id: String) = File(photosDir, "$id.webp.part")

    /** The photo's 4K video, kept next to its image. */
    fun videoFile(id: String) = File(photosDir, "$id.mp4")

    fun videoPartialFile(id: String) = File(photosDir, "$id.mp4.part")

    /** Ids of photos whose image is on disk. A video on its own doesn't count. */
    fun cachedPhotos(): Set<String> =
        photosDir.listFiles { file -> file.name.endsWith(".webp") }.orEmpty()
            .map { file -> file.name.removeSuffix(".webp") }
            .toSet()

    /** Everything this app keeps in the photos directory, partial downloads included. */
    fun bytesOnDisk(): Long = photosDir.listFiles().orEmpty().sumOf(File::length)

    fun deletePhoto(id: String) {
        listOf(photoFile(id), partialFile(id), videoFile(id), videoPartialFile(id)).forEach(File::delete)
    }

    /** Partial downloads for photos the catalog no longer wants. */
    fun deleteStrayPartials(wantedIds: Set<String>) {
        photosDir.listFiles { file -> file.name.endsWith(".part") }.orEmpty()
            .filter { file -> file.name.substringBefore('.') !in wantedIds }
            .forEach(File::delete)
    }

    fun readCatalog(): Catalog = readCatalogOrNull() ?: Catalog()

    /**
     * Null when there's no catalog or it can't be read (e.g. damaged by a power cut on older firmware).
     * The syncer then fetches the whole feed again instead of trusting "not modified".
     */
    fun readCatalogOrNull(): Catalog? {
        val file = File(root, CATALOG)
        val stamp = if (file.exists()) file.lastModified() xor file.length() else 0L
        cachedCatalog?.let { catalog -> if (stamp == cachedCatalogStamp) return catalog }
        val catalog = readJson(CATALOG, Catalog.serializer()) ?: return null
        cachedCatalog = catalog
        cachedCatalogStamp = stamp
        return catalog
    }

    fun writeCatalog(catalog: Catalog) {
        writeJson(CATALOG, Catalog.serializer(), catalog)
        cachedCatalog = null
    }

    fun readRotation(): RotationState = readJson(ROTATION, RotationState.serializer()) ?: RotationState()

    fun writeRotation(state: RotationState) = writeJson(ROTATION, RotationState.serializer(), state)

    fun readSyncState(): SyncState = readJson(SYNC, SyncState.serializer()) ?: SyncState()

    fun writeSyncState(state: SyncState) = writeJson(SYNC, SyncState.serializer(), state)

    private fun <T> readJson(name: String, serializer: KSerializer<T>): T? {
        val file = File(root, name)
        if (!file.exists()) return null
        return try {
            json.decodeFromString(serializer, file.readText())
        } catch (error: IllegalArgumentException) {
            null
        }
    }

    private fun <T> writeJson(name: String, serializer: KSerializer<T>, value: T) {
        val temp = File(root, "$name.tmp")
        FileOutputStream(temp).use { output ->
            output.write(json.encodeToString(serializer, value).encodeToByteArray())
            output.fd.sync()
        }
        if (!temp.renameTo(File(root, name))) throw IOException("Couldn't save $name")
    }

    private companion object {
        const val CATALOG = "catalog.json"
        const val ROTATION = "rotation.json"
        const val SYNC = "sync.json"
    }
}
