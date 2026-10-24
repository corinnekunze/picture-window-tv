package com.mtdetc.picturewindow.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppUpdateTest {
    private val sha = "a".repeat(64)
    private val url = "${AppUpdateParser.RELEASE_DOWNLOADS}v0.2.0/picture-window-0.2.0.apk"

    private fun json(versionCode: Int = 2, url: String = this.url, bytes: Long = 1_300_000, sha256: String = sha) =
        """{"versionCode":$versionCode,"versionName":"0.2.0","url":"$url","bytes":$bytes,"sha256":"$sha256","extra":1}"""

    @Test
    fun `reads a release's update file`() {
        val update = AppUpdateParser.parse(json())!!
        assertEquals(2, update.versionCode)
        assertEquals(url, update.apk.url)
    }

    @Test
    fun `offers only a newer version`() {
        val update = AppUpdateParser.parse(json(versionCode = 2))
        assertEquals(update, updateToOffer(update, installedVersionCode = 1))
        assertNull(updateToOffer(update, installedVersionCode = 2))
        assertNull(updateToOffer(null, installedVersionCode = 1))
    }

    @Test
    fun `ignores downloads from anywhere but this app's releases`() {
        assertNull(AppUpdateParser.parse(json(url = "https://example.com/picture-window.apk")))
        assertNull(AppUpdateParser.parse(json(url = "https://github.com/someone-else/picture-window-tv/releases/download/v1/a.apk")))
        assertNull(AppUpdateParser.parse(json(url = "${AppUpdateParser.RELEASE_DOWNLOADS}../../other/a.apk")))
        assertNull(AppUpdateParser.parse(json(url = "${AppUpdateParser.RELEASE_DOWNLOADS}v1/notes.txt")))
    }

    @Test
    fun `ignores a malformed or implausible update file`() {
        assertNull(AppUpdateParser.parse("not json"))
        assertNull(AppUpdateParser.parse(json(sha256 = "xyz")))
        assertNull(AppUpdateParser.parse(json(bytes = 0)))
        assertNull(AppUpdateParser.parse(json(bytes = 500L * 1024 * 1024)))
        assertNull(AppUpdateParser.parse(json(versionCode = 0)))
    }
}
