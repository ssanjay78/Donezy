import Foundation

/// Streak / time helpers ported from the Android `HobbyViewModel` companion object.
/// Kept free-standing so both the app and the widget can use them.
enum StreakMath {

    /// Streak computed from precomputed local-midnight day timestamps (descending).
    static func computeStreakFromDays(_ dayMs: [Int64]) -> Int {
        if dayMs.isEmpty { return 0 }
        let calendar = Calendar.current
        let daySet = Set(dayMs)

        func localDayMs(_ date: Date) -> Int64 {
            Int64(calendar.startOfDay(for: date).timeIntervalSince1970 * 1000.0)
        }

        var streak = 0
        var check = Date()
        while daySet.contains(localDayMs(check)) {
            streak += 1
            check = calendar.date(byAdding: .day, value: -1, to: check)!
        }
        if streak == 0 {
            check = calendar.date(byAdding: .day, value: -1, to: Date())!
            while daySet.contains(localDayMs(check)) {
                streak += 1
                check = calendar.date(byAdding: .day, value: -1, to: check)!
            }
        }
        return streak
    }

    /// Streak computed directly from logs (used by the detail screen and rescue check).
    static func computeStreak(_ logs: [HobbyLog]) -> Int {
        if logs.isEmpty { return 0 }
        let calendar = Calendar.current
        let logDays = Set(logs.map { log -> Date in
            calendar.startOfDay(for: Date(timeIntervalSince1970: Double(log.createdAt) / 1000.0))
        })

        func contains(_ date: Date) -> Bool {
            logDays.contains(calendar.startOfDay(for: date))
        }

        var streak = 0
        var check = Date()
        while contains(check) {
            streak += 1
            check = calendar.date(byAdding: .day, value: -1, to: check)!
        }
        if streak == 0 {
            check = calendar.date(byAdding: .day, value: -1, to: Date())!
            while contains(check) {
                streak += 1
                check = calendar.date(byAdding: .day, value: -1, to: check)!
            }
        }
        return streak
    }

    /// Whole days since the most recent log, or nil if there are none.
    static func daysSinceLastLog(_ logs: [HobbyLog]) -> Int64? {
        guard let last = logs.map({ $0.createdAt }).max() else { return nil }
        return (nowMillis() - last) / DAY_MS
    }
}
