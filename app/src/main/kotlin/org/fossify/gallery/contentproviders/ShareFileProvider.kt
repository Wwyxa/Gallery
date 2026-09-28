package org.fossify.gallery.contentproviders

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import org.fossify.commons.extensions.getMimeType
import java.io.File

/**
 * ContentProvider for sharing files that MediaStore has no record of (e.g. files inside .nomedia
 * folders). androidx FileProvider cannot be used for them: it rejects _data queries with an
 * unknown column error, so receivers that resolve shared uris to real file paths (metadata
 * editors and similar tools) report the shared file as missing. This provider answers _data with
 * the shared file's absolute path and also serves the file itself through openFile for
 * stream-reading receivers.
 */
class ShareFileProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val file = getFileFromUri(uri)
        val columns = projection ?: DEFAULT_PROJECTION
        val row = columns.map { column ->
            when (column) {
                MediaStore.MediaColumns.DATA -> file?.absolutePath
                MediaStore.MediaColumns.DISPLAY_NAME -> file?.name
                MediaStore.MediaColumns.SIZE -> file?.length()
                else -> null
            }
        }
        return MatrixCursor(columns).apply { addRow(row) }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val file = getFileFromUri(uri) ?: throw IllegalArgumentException("Invalid uri: $uri")
        if (mode != "r") {
            throw SecurityException("Only read access is supported")
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = getFileFromUri(uri)?.getMimeType().orEmpty()

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    private fun getFileFromUri(uri: Uri): File? {
        val segments = uri.pathSegments
        if (segments.size != 2 || segments.first() != PATH_SEGMENT) {
            return null
        }
        val path = Uri.decode(segments.last())
        return if (path.isNotEmpty()) File(path) else null
    }

    companion object {
        private const val PATH_SEGMENT = "share"
        private val DEFAULT_PROJECTION = arrayOf(
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE
        )

        fun getUriForFile(context: Context, file: File): Uri {
            return Uri.Builder()
                .scheme("content")
                .authority("${context.packageName}.share")
                .appendEncodedPath(PATH_SEGMENT)
                .appendEncodedPath(Uri.encode(file.absolutePath))
                .build()
        }
    }
}
