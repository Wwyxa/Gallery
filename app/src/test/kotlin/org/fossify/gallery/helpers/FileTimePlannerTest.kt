package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class FileTimePlannerTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun timestamp(value: String) = LocalDateTime.parse(value).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun preservesUnselectedComponents() {
        val original = timestamp("2024-02-29T23:12:34")
        val selected = LocalDateTime.parse("2026-10-09T10:20:30")
        assertEquals(timestamp("2026-10-09T23:12:34"), FileTimePlanner.uniform(original, selected, true, false, zone))
        assertEquals(timestamp("2024-02-29T10:20:30"), FileTimePlanner.uniform(original, selected, false, true, zone))
        assertThrows(IllegalArgumentException::class.java) { FileTimePlanner.uniform(original, selected, false, false, zone) }
    }

    @Test
    fun shiftsAcrossDayAndYearBoundaries() {
        val original = timestamp("2025-12-31T23:59:30")
        assertEquals(timestamp("2026-01-01T00:00:30"), FileTimePlanner.offset(original, 60))
        assertEquals(timestamp("2025-12-31T22:59:30"), FileTimePlanner.offset(original, -3600))
    }

    @Test
    fun sequenceUsesStartAndFixedStep() {
        val start = timestamp("2026-10-09T10:00:00")
        assertEquals(start, FileTimePlanner.sequence(start, 86400, 0))
        assertEquals(timestamp("2026-10-11T10:00:00"), FileTimePlanner.sequence(start, 86400, 2))
        assertEquals(start, FileTimePlanner.sequence(start, 0, 100))
    }

    @Test
    fun rejectsOverflowAndNegativeTimestamps() {
        assertThrows(ArithmeticException::class.java) { FileTimePlanner.offset(0, Long.MAX_VALUE) }
        assertThrows(ArithmeticException::class.java) { FileTimePlanner.sequence(0, Long.MAX_VALUE / 1000, 1000) }
        assertThrows(IllegalArgumentException::class.java) { FileTimePlanner.offset(0, -1) }
    }

    @Test
    fun rejectsNonexistentLocalTimeDuringDaylightSavingChange() {
        assertThrows(IllegalArgumentException::class.java) {
            FileTimePlanner.uniform(0, LocalDateTime.parse("2026-03-08T02:30:00"), true, true, ZoneId.of("America/New_York"))
        }
    }
}