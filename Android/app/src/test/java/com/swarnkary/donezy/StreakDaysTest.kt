package com.swarnkary.donezy

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StreakDaysTest {

    private val zone: ZoneId = ZoneId.systemDefault()
    private fun day(offset: Int): Long =
        LocalDate.now(zone).minusDays(offset.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()

    @Test fun `empty list is zero`() {
        assertEquals(0, HobbyViewModel.computeStreakFromDays(emptyList()))
    }

    @Test fun `today only is one`() {
        assertEquals(1, HobbyViewModel.computeStreakFromDays(listOf(day(0))))
    }

    @Test fun `consecutive days from today`() {
        assertEquals(4, HobbyViewModel.computeStreakFromDays(listOf(day(0), day(1), day(2), day(3))))
    }

    @Test fun `gap breaks streak`() {
        assertEquals(1, HobbyViewModel.computeStreakFromDays(listOf(day(0), day(2), day(3))))
    }

    @Test fun `yesterday but not today still counts`() {
        assertEquals(2, HobbyViewModel.computeStreakFromDays(listOf(day(1), day(2))))
    }

    @Test fun `missed two days is zero`() {
        assertEquals(0, HobbyViewModel.computeStreakFromDays(listOf(day(2), day(3))))
    }

    @Test fun `matches HobbyLog-based computeStreak`() {
        // Build a list of HobbyLogs and a parallel list of midnight day-millis,
        // and ensure both routes agree on a non-trivial pattern.
        val offsets = listOf(0, 1, 2, 4, 5)
        val logs = offsets.map {
            HobbyLog(id = 0, hobbyId = 1, entry = "x",
                createdAt = LocalDate.now(zone).minusDays(it.toLong())
                    .atStartOfDay(zone).plusHours(9).toInstant().toEpochMilli())
        }
        val days = offsets.map { day(it) }
        assertEquals(HobbyViewModel.computeStreak(logs), HobbyViewModel.computeStreakFromDays(days))
    }
}
