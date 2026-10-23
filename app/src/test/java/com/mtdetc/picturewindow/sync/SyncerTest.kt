package com.mtdetc.picturewindow.sync

import com.mtdetc.picturewindow.TestData
import com.mtdetc.picturewindow.feed.FeedClient
import com.mtdetc.picturewindow.feed.FeedItem
import com.mtdetc.picturewindow.feed.FeedVideo
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

class SyncerTest {
    @get:Rule val folder = TemporaryFolder()
    private val server = MockWebServer()
    private val files = mutableMapOf<String, ByteArray>()
    private lateinit var store: PhotoStore
    private lateinit var syncer: Syncer

    @Before
    fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = files[request.path] ?: return MockResponse().setResponseCode(404)
                return MockResponse().setBody(Buffer().write(body))
            }
        }
        server.start()
        store = PhotoStore(folder.root)
        syncer = Syncer(store, FeedClient(OkHttpClient()), budgetBytes = { 10_000 })
    }

    @After fun stop() = server.shutdown()

    private fun photo(id: String): FeedItem {
        val content = id.encodeToByteArray() + ByteArray(100)
        files["/$id.webp"] = content
        return TestData.item(id, content = content, url = server.url("/$id.webp").toString())
    }

    private fun withVideo(item: FeedItem): FeedItem {
        val content = "video ${item.id}".encodeToByteArray() + ByteArray(200)
        files["/${item.id}.mp4"] = content
        return item.copy(video = FeedVideo(server.url("/${item.id}.mp4").toString(), content.size.toLong(), FeedClient.sha256Hex(content)))
    }

    private fun videoSyncer() = Syncer(store, FeedClient(OkHttpClient()), budgetBytes = { 10_000 }, wantVideos = { true })

    private fun publish(items: List<FeedItem>, revoked: List<String> = emptyList()) {
        val manifest = """{"schema":1,"batch":"2026-10","published":"p","items":${Json.encodeToString(items)}}"""
        files["/batches/2026-10.json"] = manifest.encodeToByteArray()
        val manifestSha = FeedClient.sha256Hex(manifest.encodeToByteArray())
        files["/index.json"] = """{"schema":1,"published":"p","revoked":${Json.encodeToString(revoked)},
            "batches":[{"id":"2026-10","manifest":"batches/2026-10.json","sha256":"$manifestSha","count":${items.size}}]}"""
            .encodeToByteArray()
    }

    private fun indexUrl() = server.url("/index.json").toString()

    @Test
    fun `downloads every photo that fits`() {
        publish(listOf(photo("wmc-1"), photo("wmc-2")))
        val result = syncer.sync(indexUrl())
        assertTrue(result.complete)
        assertEquals(setOf("wmc-1", "wmc-2"), store.cachedPhotos())
    }

    @Test
    fun `deletes revoked photos`() {
        publish(listOf(photo("wmc-1"), photo("wmc-2")))
        syncer.sync(indexUrl())
        publish(listOf(photo("wmc-1"), photo("wmc-2")), revoked = listOf("wmc-2"))
        syncer.sync(indexUrl())
        assertEquals(setOf("wmc-1"), store.cachedPhotos())
        assertEquals(listOf("wmc-1"), store.readCatalog().entries.map { entry -> entry.item.id })
    }

    @Test
    fun `keeps the old catalog when a manifest is broken`() {
        publish(listOf(photo("wmc-1")))
        syncer.sync(indexUrl())
        files["/batches/2026-10.json"] = "tampered".encodeToByteArray()
        files["/index.json"] = files.getValue("/index.json") + " ".encodeToByteArray()

        val result = syncer.sync(indexUrl())
        assertFalse(result.complete)
        assertEquals(listOf("wmc-1"), store.readCatalog().entries.map { entry -> entry.item.id })
        assertEquals(setOf("wmc-1"), store.cachedPhotos())
    }

    @Test
    fun `skips a photo that fails its checksum and keeps the rest`() {
        val good = photo("wmc-1")
        val bad = photo("wmc-2")
        files["/wmc-2.webp"] = ByteArray(bad.bytes.toInt())
        publish(listOf(good, bad))
        val result = syncer.sync(indexUrl())
        assertEquals(1, result.corrupt)
        assertEquals(setOf("wmc-1"), store.cachedPhotos())
    }

    @Test
    fun `downloads each photo's video when the TV can play it`() {
        publish(listOf(withVideo(photo("wmc-1")), photo("wmc-2")))
        assertTrue(videoSyncer().sync(indexUrl()).complete)
        assertTrue(store.videoFile("wmc-1").exists())
        assertFalse(store.videoFile("wmc-2").exists())
    }

    @Test
    fun `leaves videos alone on a TV that can't play them`() {
        publish(listOf(withVideo(photo("wmc-1"))))
        syncer.sync(indexUrl())
        assertEquals(setOf("wmc-1"), store.cachedPhotos())
        assertFalse(store.videoFile("wmc-1").exists())
    }

    @Test
    fun `adds videos to photos already on the TV`() {
        val plain = photo("wmc-1")
        publish(listOf(plain))
        videoSyncer().sync(indexUrl())
        publish(listOf(withVideo(plain)))
        files["/index.json"] = files.getValue("/index.json") + " ".encodeToByteArray()
        videoSyncer().sync(indexUrl())
        assertTrue(store.videoFile("wmc-1").exists())
    }

    @Test
    fun `keeps the photo when its video fails its checksum`() {
        val item = withVideo(photo("wmc-1"))
        files["/wmc-1.mp4"] = ByteArray(item.video!!.bytes.toInt())
        publish(listOf(item))
        val result = videoSyncer().sync(indexUrl())
        assertEquals(1, result.corrupt)
        assertEquals(setOf("wmc-1"), store.cachedPhotos())
        assertFalse(store.videoFile("wmc-1").exists())
    }

    @Test
    fun `a sync stopped partway is not recorded as a success`() {
        publish(listOf(photo("a"), photo("b")))
        val result = syncer.sync(indexUrl(), shouldContinue = { false })
        assertFalse(result.complete)
        assertEquals(0L, store.readSyncState().lastSuccessAt)
    }
}
