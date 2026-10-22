package com.mtdetc.picturewindow.feed

import com.mtdetc.picturewindow.Config
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

sealed interface IndexResult {
    data object NotModified : IndexResult
    data class Fetched(val index: FeedIndex, val etag: String?) : IndexResult
}

enum class DownloadOutcome { SAVED, CORRUPT }

/** Anything the feed points at with a size and a hash: a photo's image or its video. */
data class RemoteFile(val url: String, val bytes: Long, val sha256: String)

val FeedItem.image get() = RemoteFile(url, bytes, sha256)
val FeedVideo.file get() = RemoteFile(url, bytes, sha256)

class FeedClient(private val http: OkHttpClient) {

    fun fetchIndex(url: String, etag: String?): IndexResult {
        val request = baseRequest(url).apply { if (etag != null) header("If-None-Match", etag) }.build()
        http.newCall(request).execute().use { response ->
            if (response.code == 304) return IndexResult.NotModified
            requireSuccess(response, url)
            return IndexResult.Fetched(FeedParser.parseIndex(response.body!!.string()), response.header("ETag"))
        }
    }

    /** Fetches a batch manifest and checks it against the hash the index promised. */
    fun fetchManifest(url: String, expectedSha256: String): BatchManifest {
        val bytes = http.newCall(baseRequest(url).build()).execute().use { response ->
            requireSuccess(response, url)
            response.body!!.bytes()
        }
        if (sha256Hex(bytes) != expectedSha256) throw FeedFormatException("Manifest hash mismatch: $url")
        return FeedParser.parseManifest(bytes.decodeToString())
    }

    /**
     * Downloads [remote] into [partial], resuming from what's already there, then moves it to [target]
     * once its size and SHA-256 match. A mismatch deletes the partial file. Network errors throw and
     * leave the partial file for the next attempt.
     */
    fun download(remote: RemoteFile, partial: File, target: File): DownloadOutcome {
        val resumeFrom = resumableLength(remote, partial)
        val request = baseRequest(remote.url)
            .apply { if (resumeFrom > 0) header("Range", "bytes=$resumeFrom-") }
            .build()
        http.newCall(request).execute().use { response ->
            if (response.code == 416) return finish(remote, partial, target)
            requireSuccess(response, remote.url)
            val append = resumeFrom > 0 && response.code == 206
            FileOutputStream(partial, append).use { output -> response.body!!.byteStream().copyTo(output) }
        }
        return finish(remote, partial, target)
    }

    private fun resumableLength(remote: RemoteFile, partial: File): Long {
        if (!partial.exists()) return 0
        if (partial.length() <= remote.bytes) return partial.length()
        partial.delete()
        return 0
    }

    private fun finish(remote: RemoteFile, partial: File, target: File): DownloadOutcome {
        if (partial.length() != remote.bytes || sha256Hex(partial) != remote.sha256) {
            partial.delete()
            return DownloadOutcome.CORRUPT
        }
        if (!partial.renameTo(target)) throw IOException("Couldn't move ${partial.name} into place")
        return DownloadOutcome.SAVED
    }

    private fun baseRequest(url: String): Request.Builder =
        Request.Builder().url(url).header("User-Agent", Config.USER_AGENT)

    private fun requireSuccess(response: Response, url: String) {
        if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
    }

    companion object {
        fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

        fun sha256Hex(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().toHex()
        }

        private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte) }
    }
}
