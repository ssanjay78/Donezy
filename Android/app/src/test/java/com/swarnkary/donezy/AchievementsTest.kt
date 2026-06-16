package com.swarnkary.donezy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class AchievementsTest {

    private val zone: ZoneId = ZoneId.systemDefault()
    private fun ts(daysAgo: Int): Long =
        LocalDate.now(zone).minusDays(daysAgo.toLong())
            .atStartOfDay(zone).plusHours(9).toInstant().toEpochMilli()

    private fun hobby(weeklyGoal: Int = 0) =
        Hobby(
            id = 1, name = "Plant", category = "Plants", notes = "",
            nextReminderAt = null, createdAt = 0L,
            weeklyGoal = weeklyGoal
        )

    @Test fun `first log triggers first_log and daily_streak together`() {
        val before = AchievementSnapshot.from(hobby(), emptyList())
        val after = AchievementSnapshot.from(
            hobby(),
            listOf(HobbyLog(1, 1, "x", ts(0)))
        )
        val newly = Achievements.newlyEarned(before, after).map { it.id }
        assertTrue("first_log" in newly)
        assertTrue("daily_streak" in newly)
    }

    @Test fun `three consecutive days unlocks three_streak`() {
        val before = listOf(HobbyLog(1, 1, "x", ts(2)), HobbyLog(2, 1, "x", ts(1)))
        val after = before + HobbyLog(3, 1, "x", ts(0))
        val newly = Achievements.newlyEarned(
            AchievementSnapshot.from(hobby(), before),
            AchievementSnapshot.from(hobby(), after)
        ).map { it.id }
        assertTrue("three_streak" in newly)
    }

    @Test fun `5-log threshold fires once`() {
        val four = (1..4).map { HobbyLog(it.toLong(), 1, "x", ts(it)) }
        val five = four + HobbyLog(5, 1, "x", ts(0))
        val newly = Achievements.newlyEarned(
            AchievementSnapshot.from(hobby(), four),
            AchievementSnapshot.from(hobby(), five)
        ).map { it.id }
        assertTrue("five_logs" in newly)
        assertFalse("first_log" in newly) // already earned at log 1
    }

    @Test fun `weekly goal hit triggers weekly_goal`() {
        val h = hobby(weeklyGoal = 3)
        val twoLogs = listOf(HobbyLog(1, 1, "x", ts(0)), HobbyLog(2, 1, "x", ts(0)))
        val threeLogs = twoLogs + HobbyLog(3, 1, "x", ts(0))
        val newly = Achievements.newlyEarned(
            AchievementSnapshot.from(h, twoLogs),
            AchievementSnapshot.from(h, threeLogs)
        ).map { it.id }
        assertTrue("weekly_goal" in newly)
    }

    @Test fun `photo unlocks first_photo`() {
        val withoutPhoto = listOf(HobbyLog(1, 1, "x", ts(0)))
        val withPhoto = withoutPhoto + HobbyLog(2, 1, "x", ts(0), photoUri = "file:///fake.jpg")
        val newly = Achievements.newlyEarned(
            AchievementSnapshot.from(hobby(), withoutPhoto),
            AchievementSnapshot.from(hobby(), withPhoto)
        ).map { it.id }
        assertTrue("first_photo" in newly)
    }

    @Test fun `5-star unlocks five_star`() {
        val before = listOf(HobbyLog(1, 1, "x", ts(0), rating = 4))
        val after = before + HobbyLog(2, 1, "x", ts(0), rating = 5)
        val newly = Achievements.newlyEarned(
            AchievementSnapshot.from(hobby(), before),
            AchievementSnapshot.from(hobby(), after)
        ).map { it.id }
        assertTrue("five_star" in newly)
    }

    @Test fun `comeback fires when returning after a long gap`() {
        // last log 10 days ago, then today.
        val before = listOf(HobbyLog(1, 1, "x", ts(10)))
        val after = before + HobbyLog(2, 1, "x", ts(0))
        val newly = Achievements.newlyEarned(
            AchievementSnapshot.from(hobby(), before),
            AchievementSnapshot.from(hobby(), after)
        ).map { it.id }
        assertTrue("comeback" in newly)
    }

    @Test fun `affirmation pool is non-empty`() {
        repeat(20) { assertTrue(Affirmations.pick().isNotBlank()) }
    }
}
