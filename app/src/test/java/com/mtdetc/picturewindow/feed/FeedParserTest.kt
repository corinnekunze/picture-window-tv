package com.mtdetc.picturewindow.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FeedParserTest {
    private val sha = "a".repeat(64)

    private fun manifest(schema: Int = 1, items: String) =
        """{"schema":$schema,"batch":"2026-10","published":"2026-10-22T00:00:00Z","items":[$items]}"""

    private fun item(id: String = "wmc-1", width: Int = 3840, extra: String = "") =
        """{"id":"$id","title":"T","author":"A","license":"CC0","licenseUrl":null,"sourceUrl":"https://commons.wikimedia.org/x",
           "url":"https://example.test/x.webp","width":$width,"height":2160,"bytes":10,"sha256":"$sha","category":"space"$extra}"""

    @Test
    fun `parses the live index shape and ignores unknown fields`() {
        val index = FeedParser.parseIndex(
            """{"schema":1,"published":"2026-10-22T16:20:55.259Z","future":true,
                "batches":[{"id":"2026-10","manifest":"batches/2026-10.json","sha256":"$sha","count":900,"theme":null}],
                "revoked":["wmc-193366557"]}""",
        )
        assertEquals("batches/2026-10.json", index.batches.single().manifest)
        assertEquals(listOf("wmc-193366557"), index.revoked)
    }

    @Test
    fun `rejects a schema it does not know`() {
        assertThrows(FeedFormatException::class.java) {
            FeedParser.parseIndex("""{"schema":2,"published":"x","batches":[],"revoked":[]}""")
        }
    }

    @Test
    fun `rejects unreadable json`() {
        assertThrows(FeedFormatException::class.java) { FeedParser.parseIndex("not json") }
    }

    @Test
    fun `keeps good items and drops ones that break the contract`() {
        val parsed = FeedParser.parseManifest(
            manifest(
                items = listOf(
                    item(id = "wmc-1", extra = ""","newField":"ok""""),
                    item(id = "wmc-2", width = 1920),
                    item(id = "../escape"),
                ).joinToString(","),
            ),
        )
        assertEquals(listOf("wmc-1"), parsed.items.map { item -> item.id })
    }

    @Test
    fun `reads a video, and drops only the video when it's malformed`() {
        val good = ""","video":{"url":"https://example.test/x.mp4","bytes":5,"sha256":"$sha"}"""
        val bad = ""","video":{"url":"https://example.test/x.mp4","bytes":5,"sha256":"nope"}"""
        val parsed = FeedParser.parseManifest(manifest(items = item(id = "wmc-1", extra = good) + "," + item(id = "wmc-2", extra = bad)))
        assertEquals(5L, parsed.items[0].video?.bytes)
        assertEquals("wmc-2", parsed.items[1].id)
        assertEquals(null, parsed.items[1].video)
    }
}
