package com.mtdetc.picturewindow.update

import com.mtdetc.picturewindow.feed.RemoteFile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The newest release, as `update.json` on the latest GitHub release describes it (written by
 * scripts/release.sh publish). The TV only offers it; the viewer presses Update and confirms
 * Android's prompt, and Android installs it only if it's signed with the same release key.
 */
@Serializable
data class AppUpdate(
    val versionCode: Int,
    val versionName: String,
    val url: String,
    val bytes: Long,
    val sha256: String,
) {
    val apk get() = RemoteFile(url, bytes, sha256)
}

object AppUpdateParser {
    /** Releases of this app only: anything else in update.json is ignored. */
    const val RELEASE_DOWNLOADS = "https://github.com/corinnekunze/picture-window-tv/releases/download/"
    private const val MAX_APK_BYTES = 100L * 1024 * 1024

    private val json = Json { ignoreUnknownKeys = true }
    private val sha256Pattern = Regex("^[0-9a-f]{64}$")
    private val versionNamePattern = Regex("^[0-9A-Za-z.+-]{1,32}$")

    /** The update [text] describes, or null when it's unreadable or doesn't meet the contract. */
    fun parse(text: String): AppUpdate? {
        val update = try {
            json.decodeFromString<AppUpdate>(text)
        } catch (error: IllegalArgumentException) {
            return null
        }
        return update.takeIf(::isValid)
    }

    private fun isValid(update: AppUpdate): Boolean =
        update.versionCode > 0 &&
            versionNamePattern.matches(update.versionName) &&
            update.url.startsWith(RELEASE_DOWNLOADS) &&
            !update.url.contains("..") &&
            update.url.endsWith(".apk") &&
            update.bytes in 1..MAX_APK_BYTES &&
            sha256Pattern.matches(update.sha256)
}

/** The update to offer this TV, if [latest] is newer than what's installed. */
fun updateToOffer(latest: AppUpdate?, installedVersionCode: Int): AppUpdate? =
    latest?.takeIf { update -> update.versionCode > installedVersionCode }
