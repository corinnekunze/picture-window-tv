package com.mtdetc.picturewindow

import com.mtdetc.picturewindow.feed.FeedClient
import com.mtdetc.picturewindow.feed.FeedItem
import com.mtdetc.picturewindow.store.CatalogEntry

object TestData {
    fun item(id: String, bytes: Long = 100, content: ByteArray? = null, url: String = "https://example.test/$id.webp") =
        FeedItem(
            id = id,
            title = "Title $id",
            author = "Author $id",
            license = "CC BY 4.0",
            licenseUrl = "https://creativecommons.org/licenses/by/4.0",
            sourceUrl = "https://commons.wikimedia.org/wiki/File:$id.jpg",
            url = url,
            width = 3840,
            height = 2160,
            bytes = content?.size?.toLong() ?: bytes,
            sha256 = content?.let(FeedClient::sha256Hex) ?: "0".repeat(64),
            category = "nature",
        )

    fun entry(id: String, batch: String = "2026-10", bytes: Long = 100) = CatalogEntry(batch, item(id, bytes))
}
