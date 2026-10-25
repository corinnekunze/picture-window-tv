package com.mtdetc.picturewindow.sync

import com.mtdetc.picturewindow.TestData
import com.mtdetc.picturewindow.feed.FeedClient
import com.mtdetc.picturewindow.feed.FeedItem
import com.mtdetc.picturewindow.rotation.PhotoLibrary
import com.mtdetc.picturewindow.rotation.Rotation
import com.mtdetc.picturewindow.store.PhotoStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.random.Random

/**
 * A TV over months: the feed publishes batches, the screensaver shows photos, and the daily sync
 * swaps shown photos for unseen ones within the budget. Uses the real syncer, cache planner and
 * rotation against a fake feed, with a clock the test moves forward.
 */
class LifecycleTest {
    @get:Rule val folder = TemporaryFolder()
    private val server = MockWebServer()
    private val files = mutableMapOf<String, ByteArray>()
    private var now = 1_000_000_000_000L
    private lateinit var store: PhotoStore
    private lateinit var syncer: Syncer
    private lateinit var library: PhotoLibrary

    /** Room for 10 photos of PHOTO_BYTES each. */
    private val budget = 10L * PHOTO_BYTES

    @Before
    fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = files[request.path] ?: return MockResponse().setResponseCode(404)
                // Like GitHub Pages: an ETag on every file, and 304 when the TV already has this version.
                val etag = "\"${FeedClient.sha256Hex(body)}\""
                if (request.getHeader("If-None-Match") == etag) return MockResponse().setResponseCode(304)
                return MockResponse().setHeader("ETag", etag).setBody(Buffer().write(body))
            }
        }
        server.start()
        store = PhotoStore(folder.root)
        syncer = Syncer(store, FeedClient(OkHttpClient()), budgetBytes = { budget }, clock = { now })
        library = PhotoLibrary(store, Rotation(Random(7)), intervalMs = { INTERVAL_MS }, clock = { now })
    }

    @After fun stop() = server.shutdown()

    private fun photos(batch: String, count: Int): List<FeedItem> = (1..count).map { number ->
        val id = "wmc-$batch-$number"
        val content = id.encodeToByteArray().copyOf(PHOTO_BYTES.toInt())
        files["/$id.webp"] = content
        TestData.item(id, content = content, url = server.url("/$id.webp").toString())
    }

    /** Publishes [batches] (id to photos) as the feed's index; [broken] batches get a manifest that fails its hash. */
    private fun publish(batches: Map<String, List<FeedItem>>, revoked: List<String> = emptyList(), broken: Set<String> = emptySet()) {
        val refs = batches.map { (batch, items) ->
            val manifest = """{"schema":1,"batch":"$batch","published":"p","items":${Json.encodeToString(items)}}""".encodeToByteArray()
            files["/batches/$batch.json"] = if (batch in broken) "tampered".encodeToByteArray() else manifest
            """{"id":"$batch","manifest":"batches/$batch.json","sha256":"${FeedClient.sha256Hex(manifest)}","count":${items.size}}"""
        }
        // A changing "published" stamp, like the real index, so the ETag-less fetch always sees news.
        files["/index.json"] = """{"schema":1,"published":"$now","revoked":${Json.encodeToString(revoked)},"batches":[${refs.joinToString(",")}]}"""
            .encodeToByteArray()
    }

    private fun sync() = syncer.sync(server.url("/index.json").toString())

    /** Leaves the screensaver on for [photoCount] changes; returns the ids it showed, in order. */
    private fun watch(photoCount: Int): List<String> = (1..photoCount).map {
        val shown = library.current()!!.item.id
        now += INTERVAL_MS
        shown
    }

    private fun shownEver() = store.readRotation().lastShown.keys

    @Test
    fun `works through a batch bigger than the TV, unseen photos first, then repeats what it has`() {
        val october = photos("2026-10", 25)
        publish(mapOf("2026-10" to october))
        sync()
        assertEquals(10, store.cachedPhotos().size)

        // Each day: watch the unseen photos on the TV, then the daily sync swaps the shown ones for
        // more unseen ones. (The photo on screen during a sync always stays, so a day can hold 9.)
        var days = 0
        while (shownEver().size < october.size) {
            val unseenOnTv = store.cachedPhotos() - shownEver()
            assertTrue("each sync brings unseen photos while the feed has them", unseenOnTv.isNotEmpty())
            assertEquals("every unseen photo is shown before anything repeats", unseenOnTv, watch(unseenOnTv.size).toSet())
            sync()
            assertTrue(++days < 10)
        }
        assertEquals("all 25 were shown", october.map { photo -> photo.id }.toSet(), shownEver())
        assertTrue(store.cachedPhotos().size <= 10)

        // Everything seen: the sync downloads nothing, and the screensaver keeps cycling what's there.
        val cachedBefore = store.cachedPhotos()
        assertEquals(0, sync().downloaded)
        assertEquals(cachedBefore, store.cachedPhotos())
        val cycled = watch(40)
        assertTrue(cachedBefore.containsAll(cycled))
        cycled.zipWithNext().forEach { (first, second) -> assertTrue("never the same photo twice in a row", first != second) }
    }

    @Test
    fun `a new month comes first, on the TV and on screen`() {
        publish(mapOf("2026-10" to photos("2026-10", 10)))
        sync()
        watch(10)

        val november = photos("2026-11", 4)
        publish(mapOf("2026-10" to photos("2026-10", 10), "2026-11" to november))
        assertEquals(4, sync().downloaded)
        val novemberIds = november.map { photo -> photo.id }.toSet()
        assertTrue(store.cachedPhotos().containsAll(novemberIds))
        assertTrue(store.cachedPhotos().size <= 10)
        assertEquals("the new month is shown before any repeat", novemberIds, watch(4).toSet())
    }

    @Test
    fun `a removed photo leaves the TV even while it's on screen`() {
        val october = photos("2026-10", 5)
        publish(mapOf("2026-10" to october))
        sync()
        val onScreen = library.current()!!.item.id

        publish(mapOf("2026-10" to october), revoked = listOf(onScreen))
        sync()
        assertFalse(onScreen in store.cachedPhotos())
        assertTrue("the screensaver moves on at once", library.current()!!.item.id != onScreen)
    }

    @Test
    fun `a broken batch keeps its photos, still lets the new month and removals through, and is retried`() {
        val october = photos("2026-10", 4)
        publish(mapOf("2026-10" to october))
        sync()

        val november = photos("2026-11", 3)
        val removed = october.first().id
        publish(mapOf("2026-10" to october, "2026-11" to november), revoked = listOf(removed), broken = setOf("2026-10"))
        val result = sync()
        assertFalse("counts as unfinished, so it's retried", result.complete)
        val catalogIds = store.readCatalog().entries.map { entry -> entry.item.id }.toSet()
        assertEquals(october.drop(1).map { photo -> photo.id }.toSet() + november.map { photo -> photo.id }, catalogIds)
        assertFalse(removed in store.cachedPhotos())

        // Fixed the next day: the retry picks it up without waiting for the feed to change again.
        publish(mapOf("2026-10" to october, "2026-11" to november), revoked = listOf(removed))
        assertTrue(sync().complete)
    }

    @Test
    fun `a new batch that's broken is left out until it's fixed`() {
        publish(mapOf("2026-10" to photos("2026-10", 3)))
        sync()
        val november = photos("2026-11", 3)
        publish(mapOf("2026-10" to photos("2026-10", 3), "2026-11" to november), broken = setOf("2026-11"))
        sync()
        assertTrue(store.cachedPhotos().none { id -> id.startsWith("wmc-2026-11") })
        publish(mapOf("2026-10" to photos("2026-10", 3), "2026-11" to november))
        sync()
        assertTrue(store.cachedPhotos().containsAll(november.map { photo -> photo.id }))
    }

    @Test
    fun `a batch leaving the feed's window leaves the TV`() {
        publish(mapOf("2026-04" to photos("2026-04", 3), "2026-10" to photos("2026-10", 3)))
        sync()
        publish(mapOf("2026-10" to photos("2026-10", 3)))
        sync()
        assertTrue(store.cachedPhotos().none { id -> id.startsWith("wmc-2026-04") })
        assertEquals(3, store.cachedPhotos().size)
    }

    @Test
    fun `a damaged catalog after a power cut is rebuilt, not trusted, and no photo is lost`() {
        val october = photos("2026-10", 5)
        publish(mapOf("2026-10" to october))
        sync()
        val cached = store.cachedPhotos()
        assertTrue("the TV remembers the feed's ETag", store.readSyncState().etag != null)

        File(folder.root, "catalog.json").writeText("")
        // The feed hasn't changed, so trusting the ETag would get "not modified" and no catalog.
        assertTrue(sync().complete)
        assertEquals(cached, store.cachedPhotos())
        assertEquals(5, store.readCatalog().entries.size)
    }

    @Test
    fun `an offline TV keeps showing what it has`() {
        publish(mapOf("2026-10" to photos("2026-10", 5)))
        sync()
        server.shutdown()
        assertFalse(sync().complete)
        assertEquals(5, store.cachedPhotos().size)
        assertEquals(5, watch(5).toSet().size)
    }

    @Test
    fun `a new month whose photos won't download costs the TV nothing`() {
        val october = photos("2026-10", 10)
        publish(mapOf("2026-10" to october))
        sync()
        watch(10)

        val november = photos("2026-11", 6)
        november.forEach { photo -> files.remove("/${photo.id}.webp") }
        publish(mapOf("2026-10" to october, "2026-11" to november))
        repeat(5) { sync() }
        assertEquals("still has every October photo", 10, store.cachedPhotos().size)
    }

    @Test
    fun `a new month whose photos fail their checksum costs the TV nothing`() {
        val october = photos("2026-10", 10)
        publish(mapOf("2026-10" to october))
        sync()
        watch(10)

        val november = photos("2026-11", 6)
        november.forEach { photo -> files["/${photo.id}.webp"] = ByteArray(PHOTO_BYTES.toInt()) }
        publish(mapOf("2026-10" to october, "2026-11" to november))
        repeat(5) { sync() }
        assertEquals("still has every October photo", 10, store.cachedPhotos().size)
    }

    private companion object {
        const val PHOTO_BYTES = 200L
        const val INTERVAL_MS = 3 * 60 * 1000L
    }
}
