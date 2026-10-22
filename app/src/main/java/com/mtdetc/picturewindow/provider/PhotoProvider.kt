package com.mtdetc.picturewindow.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.mtdetc.picturewindow.Config
import com.mtdetc.picturewindow.PictureWindowApp
import java.io.FileNotFoundException

/**
 * Read-only access to the photo on screen, for the screensaver and CleanYourTV's Home.
 *
 * - `content://com.mtdetc.picturewindow.photos/current`: one row describing the current photo, or no
 *   rows while nothing is downloaded yet. Readers should query again at `next_change_at`.
 * - `content://com.mtdetc.picturewindow.photos/photos/<id>`: the WebP file, opened read-only. Use the
 *   `image_uri` from the row so the image always matches the credit.
 */
class PhotoProvider : ContentProvider() {

    override fun onCreate() = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        if (matcher.match(uri) != MATCH_CURRENT) throw IllegalArgumentException("Unknown uri $uri")
        val cursor = MatrixCursor(COLUMNS)
        val current = library().current() ?: return cursor
        cursor.addRow(
            arrayOf(
                current.item.id,
                current.item.title,
                current.item.author,
                current.item.license,
                current.item.sourceUrl,
                photoUri(current.item.id).toString(),
                current.since,
                current.nextChangeAt,
            ),
        )
        return cursor
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("Photos are read-only")
        if (matcher.match(uri) != MATCH_PHOTO) throw FileNotFoundException(uri.toString())
        val file = library().cachedPhoto(uri.lastPathSegment!!) ?: throw FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String? = when (matcher.match(uri)) {
        MATCH_CURRENT -> "vnd.android.cursor.item/vnd.com.mtdetc.picturewindow.photo"
        MATCH_PHOTO -> "image/webp"
        else -> null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri = throw UnsupportedOperationException("Read-only")

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Read-only")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Read-only")

    private fun library() = PictureWindowApp.from(context!!).library

    companion object {
        val COLUMNS = arrayOf(
            "id", "title", "author", "license", "source_url", "image_uri", "since", "next_change_at",
        )
        val CURRENT_URI: Uri = Uri.parse("content://${Config.PROVIDER_AUTHORITY}/current")

        private const val MATCH_CURRENT = 1
        private const val MATCH_PHOTO = 2
        private val matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(Config.PROVIDER_AUTHORITY, "current", MATCH_CURRENT)
            addURI(Config.PROVIDER_AUTHORITY, "photos/*", MATCH_PHOTO)
        }

        fun photoUri(id: String): Uri = Uri.parse("content://${Config.PROVIDER_AUTHORITY}/photos/$id")
    }
}
