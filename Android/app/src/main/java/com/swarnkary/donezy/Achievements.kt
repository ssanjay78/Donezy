package com.swarnkary.donezy

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Achievements are derived from the existing log/hobby data — no extra table.
 * Each achievement has a stable id (used in undo/celebration text) and a predicate that
 * answers: "given this snapshot, has the user earned this badge?".
 *
 * To detect *newly* earned achievements after a save, the ViewModel runs the same set of
 * predicates against `before` and `after` snapshots and emits whatever transitioned to true.
 */
data class Achievement(
    val id: String,
    val emoji: String,
    val title: String,
    val tagline: String
)

object Achievements {

    val ALL: List<Pair<Achievement, AchievementSnapshot.() -> Boolean>> = listOf(
        Achievement("first_log",     "✨", "First log",       "You've started — the rest is just keeping going.")
            to { totalLogs >= 1 },
        Achievement("five_logs",     "🌱", "5 logs",          "Habit forming — momentum is real.")
            to { totalLogs >= 5 },
        Achievement("twenty_five",   "🌿", "25 logs",         "Roots are deep. This tracker is part of you now.")
            to { totalLogs >= 25 },
        Achievement("hundred",       "🌳", "100 logs",        "A century of check-ins — incredible commitment.")
            to { totalLogs >= 100 },
        Achievement("first_photo",   "📸", "First photo",     "Memory captured. Future-you will thank you.")
            to { hasPhoto },
        Achievement("five_star",     "⭐", "5-star session",  "A perfect session deserves to be remembered.")
            to { fiveStar },
        Achievement("daily_streak",  "🔥", "Streak started",  "Day 1. Show up tomorrow and it doubles.")
            to { currentStreak >= 1 },
        Achievement("three_streak",  "🔥", "3-day streak",    "Three days in a row — the habit's taking hold.")
            to { currentStreak >= 3 },
        Achievement("week_streak",   "🔥", "7-day streak",    "A whole week. The hard part is over.")
            to { currentStreak >= 7 },
        Achievement("two_weeks",     "🔥", "14-day streak",   "Two weeks running — this is who you are now.")
            to { currentStreak >= 14 },
        Achievement("month_streak",  "🏆", "30-day streak",   "A month-long streak. Take a moment to be proud.")
            to { currentStreak >= 30 },
        Achievement("hundred_streak","👑", "100-day streak",  "Triple digits. You're an inspiration.")
            to { currentStreak >= 100 },
        Achievement("weekly_goal",   "🎯", "Weekly goal hit", "You hit this week's target. Treat yourself.")
            to { weeklyGoal in 1..logsThisWeek },
        Achievement("comeback",      "💫", "Comeback",        "Welcome back — picking it up again is the bravest part.")
            to { comeback }
    )

    /** Predicates that turned `false → true` between two snapshots. */
    fun newlyEarned(before: AchievementSnapshot, after: AchievementSnapshot): List<Achievement> =
        ALL.filter { (_, pred) -> !before.pred() && after.pred() }.map { it.first }

    /** All currently-earned achievements for a tracker, used by detail screen. */
    fun earned(snapshot: AchievementSnapshot): List<Achievement> =
        ALL.filter { (_, pred) -> snapshot.pred() }.map { it.first }
}

/**
 * Pre-computed numbers fed into achievement predicates. Keeping it small keeps the
 * predicate set easy to extend.
 */
data class AchievementSnapshot(
    val totalLogs: Int,
    val currentStreak: Int,
    val hasPhoto: Boolean,
    val fiveStar: Boolean,
    val logsThisWeek: Int,
    val weeklyGoal: Int,
    /** True when the user logs today after a gap of 7+ days since the previous log. */
    val comeback: Boolean
) {
    companion object {
        fun from(hobby: Hobby, logs: List<HobbyLog>, zone: ZoneId = ZoneId.systemDefault()): AchievementSnapshot {
            val streak = HobbyViewModel.computeStreak(logs)
            val today = LocalDate.now(zone)
            val weekFields = WeekFields.of(Locale.getDefault())
            val thisWeek = today.get(weekFields.weekOfWeekBasedYear())
            val thisWeekYear = today.get(weekFields.weekBasedYear())
            val logsThisWeek = logs.count {
                val d = Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate()
                d.get(weekFields.weekOfWeekBasedYear()) == thisWeek &&
                    d.get(weekFields.weekBasedYear()) == thisWeekYear
            }

            // Comeback: the most recent two logs are >=7 days apart, and the latest is today.
            val sorted = logs.sortedByDescending { it.createdAt }
            val comeback = if (sorted.size >= 2) {
                val newest = Instant.ofEpochMilli(sorted[0].createdAt).atZone(zone).toLocalDate()
                val prev = Instant.ofEpochMilli(sorted[1].createdAt).atZone(zone).toLocalDate()
                newest == today && java.time.temporal.ChronoUnit.DAYS.between(prev, newest) >= 7
            } else false

            return AchievementSnapshot(
                totalLogs = logs.size,
                currentStreak = streak,
                hasPhoto = logs.any { it.photoUri != null },
                fiveStar = logs.any { it.rating == 5 },
                logsThisWeek = logsThisWeek,
                weeklyGoal = hobby.weeklyGoal,
                comeback = comeback
            )
        }
    }
}

/** A small bank of varied affirmations so the same toast doesn't repeat after every save. */
object Affirmations {
    private val random = java.util.Random()

    private val PHRASES = listOf(
        "Log saved ✓ · Nice work",
        "Logged · Small steps, big wins",
        "Saved · Future-you will thank present-you",
        "Logged · One more brick in the wall",
        "Saved · That's another rep for the streak",
        "Logged · You showed up today",
        "Saved · Quiet consistency is the move",
        "Logged · Progress, not perfection",
        "Saved · Keep stacking days",
        "Logged · This is how habits stick"
    )

    fun pick(): String = PHRASES[random.nextInt(PHRASES.size)]
}
