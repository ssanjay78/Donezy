import Foundation

/// Recurrence rule encoded as `type:data` — a direct port of the Android sealed class.
///
///  - `none`                 — no auto-recurrence (one-shot or no reminder)
///  - `hours:<n>`            — every N hours (legacy compatible: `reminder_interval_hours`)
///  - `daily`                — every day at the same wall-clock time
///  - `weekly:<dayMask>`     — selected weekdays (bit 0 = Mon … bit 6 = Sun)
///  - `monthly:<dayOfMonth>` — same day of month (1–31, clamped)
///
/// Persisted columns: `recurrence_type` + `recurrence_data`.
/// `reminder_interval_hours` is kept for v2 compatibility and mirrors `hours:<n>`.
enum Recurrence: Equatable {
    case none
    case hourly(hours: Int64)
    case daily
    case weekly(dayMask: Int)   // bit 0 = Mon, bit 6 = Sun
    case monthly(dayOfMonth: Int)

    /// Returns (type, data) strings for persistence.
    func encode() -> (type: String, data: String) {
        switch self {
        case .none:                  return ("none", "")
        case .hourly(let hours):     return ("hours", String(hours))
        case .daily:                 return ("daily", "")
        case .weekly(let mask):      return ("weekly", String(mask))
        case .monthly(let day):      return ("monthly", String(day))
        }
    }

    var isRecurring: Bool {
        if case .none = self { return false }
        return true
    }

    static func decode(type: String?, data: String?) -> Recurrence {
        switch type {
        case "hours":
            let h = Int64(data ?? "") ?? 24
            return .hourly(hours: max(1, h))
        case "daily":
            return .daily
        case "weekly":
            let mask = Int(data ?? "") ?? 0
            return .weekly(dayMask: min(max(mask, 0), 0x7F))
        case "monthly":
            let day = Int(data ?? "") ?? 1
            return .monthly(dayOfMonth: min(max(day, 1), 31))
        default:
            return .none
        }
    }
}

struct Hobby: Identifiable, Equatable {
    let id: Int64
    var name: String
    var category: String
    var notes: String
    var nextReminderAt: Int64?
    var createdAt: Int64
    var isPinned: Bool = false
    var isArchived: Bool = false
    var reminderIntervalHours: Int64 = 0   // legacy; mirrors recurrence when .hourly
    var recurrence: Recurrence = .none
    var weeklyGoal: Int = 0                 // 0 = no goal
}

struct HobbyLog: Identifiable, Equatable {
    let id: Int64
    let hobbyId: Int64
    var entry: String
    var createdAt: Int64
    var rating: Int? = nil
    var photoUri: String? = nil
}

struct HobbyDetail: Equatable {
    let hobby: Hobby
    let logs: [HobbyLog]
}

struct LogSearchHit: Identifiable, Equatable {
    var id: Int64 { log.id }
    let log: HobbyLog
    let hobbyName: String
    let hobbyCategory: String
}
