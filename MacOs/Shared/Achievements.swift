import Foundation

/// Achievements are derived from existing log/hobby data — no extra table.
/// Each has a stable id and a predicate answering: "given this snapshot, earned?".
struct Achievement: Identifiable, Equatable {
    let id: String
    let emoji: String
    let title: String
    let tagline: String
}

enum Achievements {

    /// (achievement, predicate) pairs, in display order. Mirrors the Android `ALL` list.
    static let all: [(Achievement, (AchievementSnapshot) -> Bool)] = [
        (Achievement(id: "first_log", emoji: "✨", title: "First log", tagline: "You've started — the rest is just keeping going."),
            { $0.totalLogs >= 1 }),
        (Achievement(id: "five_logs", emoji: "🌱", title: "5 logs", tagline: "Habit forming — momentum is real."),
            { $0.totalLogs >= 5 }),
        (Achievement(id: "twenty_five", emoji: "🌿", title: "25 logs", tagline: "Roots are deep. This tracker is part of you now."),
            { $0.totalLogs >= 25 }),
        (Achievement(id: "hundred", emoji: "🌳", title: "100 logs", tagline: "A century of check-ins — incredible commitment."),
            { $0.totalLogs >= 100 }),
        (Achievement(id: "first_photo", emoji: "📸", title: "First photo", tagline: "Memory captured. Future-you will thank you."),
            { $0.hasPhoto }),
        (Achievement(id: "five_star", emoji: "⭐", title: "5-star session", tagline: "A perfect session deserves to be remembered."),
            { $0.fiveStar }),
        (Achievement(id: "daily_streak", emoji: "🔥", title: "Streak started", tagline: "Day 1. Show up tomorrow and it doubles."),
            { $0.currentStreak >= 1 }),
        (Achievement(id: "three_streak", emoji: "🔥", title: "3-day streak", tagline: "Three days in a row — the habit's taking hold."),
            { $0.currentStreak >= 3 }),
        (Achievement(id: "week_streak", emoji: "🔥", title: "7-day streak", tagline: "A whole week. The hard part is over."),
            { $0.currentStreak >= 7 }),
        (Achievement(id: "two_weeks", emoji: "🔥", title: "14-day streak", tagline: "Two weeks running — this is who you are now."),
            { $0.currentStreak >= 14 }),
        (Achievement(id: "month_streak", emoji: "🏆", title: "30-day streak", tagline: "A month-long streak. Take a moment to be proud."),
            { $0.currentStreak >= 30 }),
        (Achievement(id: "hundred_streak", emoji: "👑", title: "100-day streak", tagline: "Triple digits. You're an inspiration."),
            { $0.currentStreak >= 100 }),
        (Achievement(id: "weekly_goal", emoji: "🎯", title: "Weekly goal hit", tagline: "You hit this week's target. Treat yourself."),
            { $0.weeklyGoal >= 1 && $0.weeklyGoal <= $0.logsThisWeek }),
        (Achievement(id: "comeback", emoji: "💫", title: "Comeback", tagline: "Welcome back — picking it up again is the bravest part."),
            { $0.comeback }),
    ]

    /// Predicates that turned false → true between two snapshots.
    static func newlyEarned(before: AchievementSnapshot, after: AchievementSnapshot) -> [Achievement] {
        all.filter { !$0.1(before) && $0.1(after) }.map { $0.0 }
    }

    /// All currently-earned achievements for a tracker.
    static func earned(_ snapshot: AchievementSnapshot) -> [Achievement] {
        all.filter { $0.1(snapshot) }.map { $0.0 }
    }
}

/// Pre-computed numbers fed into achievement predicates.
struct AchievementSnapshot: Equatable {
    let totalLogs: Int
    let currentStreak: Int
    let hasPhoto: Bool
    let fiveStar: Bool
    let logsThisWeek: Int
    let weeklyGoal: Int
    /// True when the user logs today after a gap of 7+ days since the previous log.
    let comeback: Bool

    static func from(hobby: Hobby, logs: [HobbyLog]) -> AchievementSnapshot {
        let calendar = Calendar.current
        let streak = StreakMath.computeStreak(logs)
        let today = calendar.startOfDay(for: Date())

        func weekKey(_ date: Date) -> Int {
            let comps = calendar.dateComponents([.weekOfYear, .yearForWeekOfYear], from: date)
            return (comps.yearForWeekOfYear ?? 0) * 100 + (comps.weekOfYear ?? 0)
        }
        let thisWeekKey = weekKey(today)
        let logsThisWeek = logs.filter {
            weekKey(Date(timeIntervalSince1970: Double($0.createdAt) / 1000.0)) == thisWeekKey
        }.count

        // Comeback: the most recent two logs are >=7 days apart, and the latest is today.
        let sorted = logs.sorted { $0.createdAt > $1.createdAt }
        var comeback = false
        if sorted.count >= 2 {
            let newest = calendar.startOfDay(for: Date(timeIntervalSince1970: Double(sorted[0].createdAt) / 1000.0))
            let prev = calendar.startOfDay(for: Date(timeIntervalSince1970: Double(sorted[1].createdAt) / 1000.0))
            let gap = calendar.dateComponents([.day], from: prev, to: newest).day ?? 0
            comeback = newest == today && gap >= 7
        }

        return AchievementSnapshot(
            totalLogs: logs.count,
            currentStreak: streak,
            hasPhoto: logs.contains { $0.photoUri != nil },
            fiveStar: logs.contains { $0.rating == 5 },
            logsThisWeek: logsThisWeek,
            weeklyGoal: hobby.weeklyGoal,
            comeback: comeback
        )
    }
}

/// A small bank of varied affirmations so the same toast doesn't repeat every save.
enum Affirmations {
    private static let phrases = [
        "Log saved ✓ · Nice work",
        "Logged · Small steps, big wins",
        "Saved · Future-you will thank present-you",
        "Logged · One more brick in the wall",
        "Saved · That's another rep for the streak",
        "Logged · You showed up today",
        "Saved · Quiet consistency is the move",
        "Logged · Progress, not perfection",
        "Saved · Keep stacking days",
        "Logged · This is how habits stick",
    ]

    static func pick() -> String { phrases.randomElement() ?? phrases[0] }
}
