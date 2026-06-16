package com.swarnkary.donezy

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StreakTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    private fun logsOnDays(vararg daysAgo: Int): List<HobbyLog> {
        val today = LocalDate.now(zone)
        return daysAgo.map { offset ->
            val ts = today.minusDays(offset.toLong())
                .atStartOfDay(zone).plusHours(12).toInstant().toEpochMilli()
            HobbyLog(id = 0, hobbyId = 1, entry = "x", createdAt = ts)
        }
    }

    @Test fun `empty logs is zero`() {
        assertEquals(0, HobbyViewModel.computeStreak(emptyList()))
    }

    @Test fun `single log today is one`() {
        assertEquals(1, HobbyViewModel.computeStreak(logsOnDays(0)))
    }

    @Test fun `streak counts consecutive days starting today`() {
        assertEquals(3, HobbyViewModel.computeStreak(logsOnDays(0, 1, 2)))
    }

    @Test fun `gap breaks streak`() {
        // logs today, then 2 days ago — only today counts
        assertEquals(1, HobbyViewModel.computeStreak(logsOnDays(0, 2)))
    }

    @Test fun `if no log today but yesterday, streak still counts back from yesterday`() {
        assertEquals(2, HobbyViewModel.computeStreak(logsOnDays(1, 2)))
    }

    @Test fun `if last log was 3+ days ago, streak is zero`() {
        assertEquals(0, HobbyViewModel.computeStreak(logsOnDays(3, 4)))
    }
}
