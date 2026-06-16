package com.swarnkary.donezy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

class RecurrenceTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private fun zdt(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        ZonedDateTime.of(LocalDateTime.of(y, mo, d, h, mi), zone).toInstant().toEpochMilli()

    @Test fun `none returns null`() {
        assertNull(nextOccurrence(Recurrence.None, zdt(2026, 6, 1, 10, 0), zone))
    }

    @Test fun `hourly adds hours`() {
        val from = zdt(2026, 6, 1, 10, 0)
        val next = nextOccurrence(Recurrence.Hourly(3), from, zone)!!
        assertEquals(from + 3 * HOUR_MS, next)
    }

    @Test fun `daily adds 24h preserving wall-clock`() {
        val from = zdt(2026, 6, 1, 9, 30)
        val next = nextOccurrence(Recurrence.Daily, from, zone)!!
        val nextZ = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(next), zone)
        assertEquals(2, nextZ.dayOfMonth)
        assertEquals(9, nextZ.hour)
        assertEquals(30, nextZ.minute)
    }

    @Test fun `weekly with empty mask falls back to plus 7 days`() {
        val from = zdt(2026, 6, 1, 10, 0) // Monday
        val next = nextOccurrence(Recurrence.Weekly(0), from, zone)!!
        assertEquals(7, ChronoUnit.DAYS.between(
            ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(from), zone),
            ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(next), zone)
        ))
    }

    @Test fun `weekly picks next set day`() {
        val from = zdt(2026, 6, 1, 10, 0) // Monday → bit 0
        // bit 2 = Wednesday
        val next = nextOccurrence(Recurrence.Weekly(1 shl 2), from, zone)!!
        val nextZ = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(next), zone)
        assertEquals(java.time.DayOfWeek.WEDNESDAY, nextZ.dayOfWeek)
        assertTrue(nextZ.isAfter(ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(from), zone)))
    }

    @Test fun `monthly clamps to month length`() {
        val from = zdt(2026, 1, 31, 9, 0) // Jan 31 → next is end of Feb
        val next = nextOccurrence(Recurrence.Monthly(31), from, zone)!!
        val nextZ = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(next), zone)
        assertEquals(2, nextZ.monthValue)
        assertEquals(28, nextZ.dayOfMonth)
    }

    @Test fun `encode then decode is identity`() {
        val rules = listOf(
            Recurrence.None,
            Recurrence.Hourly(48),
            Recurrence.Daily,
            Recurrence.Weekly(0b0101010),
            Recurrence.Monthly(15)
        )
        for (r in rules) {
            val (type, data) = r.encode()
            assertEquals(r, Recurrence.decode(type, data))
        }
    }
}
