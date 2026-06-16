import Foundation

let HOUR_MS: Int64 = 60 * 60 * 1000
let DAY_MS: Int64 = 24 * HOUR_MS

/// "MMM d, yyyy h:mm a" in the device locale — e.g. "Jun 16, 2026 9:30 AM".
func formatDate(_ timestamp: Int64) -> String {
    let formatter = DateFormatter()
    formatter.locale = Locale.current
    formatter.dateFormat = "MMM d, yyyy h:mm a"
    return formatter.string(from: Date(timeIntervalSince1970: Double(timestamp) / 1000.0))
}

/// "MMM d, yyyy" — e.g. "Jun 16, 2026".
func formatDateShort(_ timestamp: Int64) -> String {
    let formatter = DateFormatter()
    formatter.locale = Locale.current
    formatter.dateFormat = "MMM d, yyyy"
    return formatter.string(from: Date(timeIntervalSince1970: Double(timestamp) / 1000.0))
}

/// Current wall-clock time in epoch milliseconds (matches Android's System.currentTimeMillis()).
func nowMillis() -> Int64 {
    Int64(Date().timeIntervalSince1970 * 1000.0)
}
