package org.fossify.gallery.models

import androidx.room.ColumnInfo

// lightweight projection of the media table, used to look up already parsed durations on fetch
data class MediumDuration(
    @ColumnInfo(name = "full_path") val path: String,
    @ColumnInfo(name = "video_duration") val duration: Int
)
