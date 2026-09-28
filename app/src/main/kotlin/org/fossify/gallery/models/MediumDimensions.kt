package org.fossify.gallery.models

import androidx.room.ColumnInfo

// lightweight projection of the media table, used to look up already parsed aspect ratios on fetch
data class MediumDimensions(
    @ColumnInfo(name = "full_path") val path: String,
    @ColumnInfo(name = "width") val width: Int,
    @ColumnInfo(name = "height") val height: Int
)
