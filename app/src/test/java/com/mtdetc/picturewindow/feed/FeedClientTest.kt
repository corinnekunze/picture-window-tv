package com.mtdetc.picturewindow.feed

import com.mtdetc.picturewindow.TestData
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FeedClientTest {
    @get:Rule val folder = TemporaryFolder()
    private val server = MockWebServer()
    private val client = FeedClient(OkHttpClient())
    private val photo = ByteArray(1000) { index -> (index % 251).toByte() }

    @Before fun start() = server.start()

    @After fun stop() = server.shutdown()

    @Test
    fun `sends the etag and reports not modified`() {
        server.enqueue(MockResponse().setResponseCode(304))
        assertEquals(IndexResult.NotModified, client.fetchIndex(server.url("/index.json").toString(), "\"abc\""))
        assertEquals("\"abc\"", server.takeRequest().getHeader("If-None-Match"))
    }

    @Test
    fun `rejects a manifest whose hash does not match the index`() {
        server.enqueue(MockResponse().setBody("""{"schema":1,"batch":"b","published":"p","items":[]}"""))
        assertThrows(FeedFormatException::class.java) {
            client.fetchManifest(server.url("/m.json").toString(), "0".repeat(64))
        }
    }

    @Test
    fun `resumes a partial download with a range request`() {
        val item = TestData.item("wmc-1", content = photo, url = server.url("/wmc-1.webp").toString())
        val partial = folder.newFile("wmc-1.webp.part").apply { writeBytes(photo.copyOfRange(0, 400)) }
        val target = folder.root.resolve("wmc-1.webp")
        server.enqueue(MockResponse().setResponseCode(206).setBody(Buffer().write(photo.copyOfRange(400, 1000))))

        assertEquals(DownloadOutcome.SAVED, client.download(item, partial, target))
        assertEquals("bytes=400-", server.takeRequest().getHeader("Range"))
        assertArrayEquals(photo, target.readBytes())
        assertFalse(partial.exists())
    }

    @Test
    fun `starts over when the server ignores the range`() {
        val item = TestData.item("wmc-1", content = photo, url = server.url("/wmc-1.webp").toString())
        val partial = folder.newFile("wmc-1.webp.part").apply { writeBytes(ByteArray(400)) }
        val target = folder.root.resolve("wmc-1.webp")
        server.enqueue(MockResponse().setBody(Buffer().write(photo)))

        assertEquals(DownloadOutcome.SAVED, client.download(item, partial, target))
        assertArrayEquals(photo, target.readBytes())
    }

    @Test
    fun `throws away a download that fails its checksum`() {
        val item = TestData.item("wmc-1", content = photo, url = server.url("/wmc-1.webp").toString())
        val partial = folder.root.resolve("wmc-1.webp.part")
        val target = folder.root.resolve("wmc-1.webp")
        server.enqueue(MockResponse().setBody(Buffer().write(ByteArray(1000))))

        assertEquals(DownloadOutcome.CORRUPT, client.download(item, partial, target))
        assertFalse(partial.exists())
        assertFalse(target.exists())
    }
}
