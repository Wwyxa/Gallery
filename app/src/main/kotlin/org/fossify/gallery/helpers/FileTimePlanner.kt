package org.fossify.gallery.helpers

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

object FileTimePlanner {
    fun uniform(original: Long, selected: LocalDateTime, changeDate: Boolean, changeTime: Boolean, zone: ZoneId): Long {
        require(changeDate || changeTime)
        val previous = Instant.ofEpochMilli(original).atZone(zone)
        val date = if (changeDate) selected.toLocalDate() else previous.toLocalDate()
        val time = if (changeTime) selected.toLocalTime() else previous.toLocalTime()
        val result = LocalDateTime.of(date, time)
        require(zone.rules.getValidOffsets(result).isNotEmpty())
        return result.atZone(zone).toInstant().toEpochMilli().also { require(it >= 0) }
    }

    fun offset(original: Long, seconds: Long): Long =
        Math.addExact(original, Math.multiplyExact(seconds, 1000L)).also { require(it >= 0) }

    fun sequence(start: Long, seconds: Long, index: Int): Long =
        offset(start, Math.multiplyExact(seconds, index.toLong()))
}