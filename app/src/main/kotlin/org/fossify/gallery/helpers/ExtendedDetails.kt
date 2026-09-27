package org.fossify.gallery.helpers

import android.content.Context
import android.graphics.Point
import android.provider.MediaStore
import android.provider.MediaStore.Files
import android.provider.MediaStore.Images
import androidx.exifinterface.media.ExifInterface
import com.awxkee.jxlcoder.JxlCoder
import org.fossify.commons.extensions.*
import org.fossify.gallery.extensions.config
import org.fossify.gallery.models.Medium
import java.io.File

fun Context.getMediumExtendedDetails(medium: Medium): String {
    val file = File(medium.path)
    if (!getDoesFilePathExist(file.absolutePath)) {
        return ""
    }

    val path = "${file.parent.trimEnd('/')}/"
    val exif = try {
        ExifInterface(medium.path)
    } catch (e: Exception) {
        return ""
    }

    val details = StringBuilder()
    val detailsFlag = config.extendedDetails
    if (detailsFlag and EXT_NAME != 0) {
        medium.name.let { if (it.isNotEmpty()) details.appendLine(it) }
    }

    if (detailsFlag and EXT_PATH != 0) {
        path.let { if (it.isNotEmpty()) details.appendLine(it) }
    }

    if (detailsFlag and EXT_SIZE != 0) {
        file.length().formatSize().let { if (it.isNotEmpty()) details.appendLine(it) }
    }

    if (detailsFlag and EXT_RESOLUTION != 0) {
        getResolution(medium, file)?.let { if (it.isNotEmpty()) details.appendLine(it) }
    }

    if (detailsFlag and EXT_LAST_MODIFIED != 0) {
        getFileLastModified(file).let { if (it.isNotEmpty()) details.appendLine(it) }
    }

    if (detailsFlag and EXT_DATE_TAKEN != 0) {
        exif.getExifDateTaken(this).let { if (it.isNotEmpty()) details.appendLine(it) }
    }

    if (detailsFlag and EXT_CAMERA_MODEL != 0) {
        exif.getExifCameraModel().let { if (it.isNotEmpty()) details.appendLine(it) }
    }

    if (detailsFlag and EXT_EXIF_PROPERTIES != 0) {
        exif.getExifProperties().let { if (it.isNotEmpty()) details.appendLine(it) }
    }

    if (detailsFlag and EXT_GPS != 0) {
        getLatLonAltitude(medium.path).let { if (it.isNotEmpty()) details.appendLine(it) }
    }
    return details.toString().trim()
}

private fun Context.getResolution(medium: Medium, file: File): String? {
    if (medium.name.endsWith(".jxl", ignoreCase = true)) {
        val resolution = try {
            JxlCoder.getSize(file.readBytes())
        } catch (ignored: OutOfMemoryError) {
            null
        }
        return resolution?.let { Point(it.width, it.height).formatAsResolution() }
    } else {
        return getResolution(file.absolutePath)?.formatAsResolution()
    }
}

private fun Context.getFileLastModified(file: File): String {
    val projection = arrayOf(Images.Media.DATE_MODIFIED)
    val uri = Files.getContentUri("external")
    val selection = "${MediaStore.MediaColumns.DATA} = ?"
    val selectionArgs = arrayOf(file.absolutePath)
    val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
    cursor?.use {
        return if (cursor.moveToFirst()) {
            val dateModified = cursor.getLongValue(Images.Media.DATE_MODIFIED) * 1000L
            dateModified.formatDate(this)
        } else {
            file.lastModified().formatDate(this)
        }
    }
    return ""
}

private fun getLatLonAltitude(path: String): String {
    var result = ""
    val exif = try {
        ExifInterface(path)
    } catch (e: Exception) {
        return ""
    }

    val latLon = FloatArray(2)

    if (exif.getLatLong(latLon)) {
        result = "${latLon[0]},  ${latLon[1]}"
    }

    val altitude = exif.getAltitude(0.0)
    if (altitude != 0.0) {
        result += ",  ${altitude}m"
    }

    return result.trimStart(',').trim()
}
