package com.mtdetc.picturewindow.feed

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Mirrors src/schema.ts in corinnekunze/picture-window (schema 1). Unknown fields are ignored so the
// feed can add optional fields without breaking TVs in the field.

@Serializable
data class FeedIndex(
    val schema: Int,
    val published: String,
    val batches: List<BatchRef>,
    val revoked: List<String>,
)

@Serializable
data class BatchRef(
    val id: String,
    val manifest: String,
    val sha256: String,
    val count: Int,
    val theme: String? = null,
)

@Serializable
data class BatchManifest(
    val schema: Int,
    val batch: String,
    val published: String,
    val items: List<FeedItem>,
)

/** The photo as a one-frame 3840×2160 HEVC video; TVs draw apps at 1080p but play video at 4K. */
@Serializable
data class FeedVideo(
    val url: String,
    val bytes: Long,
    val sha256: String,
)

@Serializable
data class FeedItem(
    val id: String,
    val title: String,
    val author: String,
    val license: String,
    val licenseUrl: String? = null,
    val sourceUrl: String,
    val url: String,
    val width: Int,
    val height: Int,
    val bytes: Long,
    val sha256: String,
    val category: String,
    val video: FeedVideo? = null,
)

class FeedFormatException(message: String) : Exception(message)

object FeedParser {
    const val SUPPORTED_SCHEMA = 1

    private val json = Json { ignoreUnknownKeys = true }
    private val sha256Pattern = Regex("^[0-9a-f]{64}$")
    // Ids become file names, so only plain characters are allowed.
    private val idPattern = Regex("^[A-Za-z0-9-]{1,100}$")

    fun parseIndex(text: String): FeedIndex {
        val index = decode<FeedIndex>(text)
        requireSchema(index.schema)
        return index
    }

    /**
     * Parses a batch manifest, dropping any item that doesn't meet the contract. A malformed video
     * only loses the video: the photo itself is still fine.
     */
    fun parseManifest(text: String): BatchManifest {
        val manifest = decode<BatchManifest>(text)
        requireSchema(manifest.schema)
        val items = manifest.items.filter(::isPublishable).map { item ->
            if (item.video == null || isValidVideo(item.video)) item else item.copy(video = null)
        }
        return manifest.copy(items = items)
    }

    private fun isValidVideo(video: FeedVideo): Boolean =
        video.bytes > 0 && sha256Pattern.matches(video.sha256) && video.url.isNotBlank()

    fun isPublishable(item: FeedItem): Boolean =
        idPattern.matches(item.id) &&
            item.width == 3840 &&
            item.height == 2160 &&
            item.bytes > 0 &&
            sha256Pattern.matches(item.sha256) &&
            item.author.isNotBlank()

    private inline fun <reified T> decode(text: String): T =
        try {
            json.decodeFromString<T>(text)
        } catch (error: IllegalArgumentException) {
            throw FeedFormatException("Unreadable feed file: ${error.message}")
        }

    private fun requireSchema(schema: Int) {
        if (schema != SUPPORTED_SCHEMA) throw FeedFormatException("Unsupported feed schema $schema")
    }
}
