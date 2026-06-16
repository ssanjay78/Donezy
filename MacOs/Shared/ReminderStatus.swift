import SwiftUI

/// Status descriptor for a reminder timestamp — label, detail line, and accent color.
/// Direct port of `reminderStatusInfo` + `durationText` + `durationRefreshIntervalMs`.
struct ReminderStatusInfo {
    let label: String
    let detail: String
    let color: Color
}

func reminderStatusInfo(_ nextReminderAt: Int64?, now: Int64 = nowMillis()) -> ReminderStatusInfo {
    guard let nextReminderAt else {
        return ReminderStatusInfo(label: "No reminder", detail: "Not scheduled", color: Color(hex: 0x888888))
    }
    let diff = nextReminderAt - now
    if diff <= 0 {
        return ReminderStatusInfo(label: "Overdue", detail: "Overdue by \(durationText(-diff))", color: Color(hex: 0xB3261E))
    } else if diff <= HOUR_MS {
        return ReminderStatusInfo(label: "Soon", detail: "In \(durationText(diff))", color: Color(hex: 0x9A5A00))
    } else if diff <= DAY_MS {
        return ReminderStatusInfo(label: "Today", detail: "In \(durationText(diff))", color: Color(hex: 0x9A5A00))
    } else {
        return ReminderStatusInfo(label: "Scheduled", detail: formatDateShort(nextReminderAt), color: Color(hex: 0x1A6B48))
    }
}

/// Refresh cadence (seconds) that keeps `durationText` honest without burning CPU.
func durationRefreshIntervalSeconds(_ diffMs: Int64) -> Double {
    let abs = Swift.abs(diffMs)
    if abs < 60_000 { return 1 }
    if abs < HOUR_MS { return 30 }
    if abs < DAY_MS { return 60 }
    return 5 * 60
}

func durationText(_ ms: Int64) -> String {
    let abs = Swift.abs(ms)
    let seconds = abs / 1000
    if seconds < 60 { return "\(seconds)s" }
    if seconds < 3600 { return "\(seconds / 60)m" }
    if seconds < 86_400 {
        let h = seconds / 3600
        let m = (seconds % 3600) / 60
        return m == 0 ? "\(h)h" : "\(h)h \(m)m"
    }
    if seconds < 14 * 86_400 { return "\(seconds / 86_400)d" }
    return "\(seconds / (7 * 86_400))w"
}
