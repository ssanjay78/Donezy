import Foundation

/// Computes the next fire time for a recurrence rule, given a `from` instant.
/// Direct port of the Android `nextOccurrence` / `nextWeeklyMatch` / `nextMonthlyMatch`.
/// Shared so the widget's quick-log can advance recurring reminders too.
func nextOccurrence(_ recurrence: Recurrence, from: Int64, calendar: Calendar = .current) -> Int64? {
    switch recurrence {
    case .none:
        return nil
    case .hourly(let hours):
        return from + hours * HOUR_MS
    case .daily:
        let fromDate = Date(timeIntervalSince1970: Double(from) / 1000.0)
        let next = calendar.date(byAdding: .day, value: 1, to: fromDate)!
        return Int64(next.timeIntervalSince1970 * 1000.0)
    case .weekly(let dayMask):
        return nextWeeklyMatch(from: from, dayMask: dayMask, calendar: calendar)
    case .monthly(let dayOfMonth):
        return nextMonthlyMatch(from: from, dayOfMonth: dayOfMonth, calendar: calendar)
    }
}

/// Bit index for a Swift weekday (Calendar uses 1=Sun…7=Sat). Android uses Mon=0…Sun=6.
private func bitIndexFor(weekday: Int) -> Int {
    // Calendar.weekday: 1=Sun, 2=Mon … 7=Sat → Android index: Mon=0 … Sun=6
    // Mon(2)->0, Tue(3)->1, … Sat(7)->5, Sun(1)->6
    return weekday == 1 ? 6 : weekday - 2
}

private func nextWeeklyMatch(from: Int64, dayMask: Int, calendar: Calendar) -> Int64 {
    let fromDate = Date(timeIntervalSince1970: Double(from) / 1000.0)
    if dayMask == 0 {
        return Int64(calendar.date(byAdding: .day, value: 7, to: fromDate)!.timeIntervalSince1970 * 1000.0)
    }
    // Preserve the time-of-day from `from`.
    let timeComps = calendar.dateComponents([.hour, .minute, .second], from: fromDate)
    var probe = calendar.date(byAdding: .day, value: 1, to: fromDate)!
    for _ in 0..<7 {
        let weekday = calendar.component(.weekday, from: probe)
        if dayMask & (1 << bitIndexFor(weekday: weekday)) != 0 {
            var comps = calendar.dateComponents([.year, .month, .day], from: probe)
            comps.hour = timeComps.hour
            comps.minute = timeComps.minute
            comps.second = timeComps.second
            if let match = calendar.date(from: comps) {
                return Int64(match.timeIntervalSince1970 * 1000.0)
            }
        }
        probe = calendar.date(byAdding: .day, value: 1, to: probe)!
    }
    return Int64(calendar.date(byAdding: .day, value: 7, to: fromDate)!.timeIntervalSince1970 * 1000.0)
}

private func nextMonthlyMatch(from: Int64, dayOfMonth: Int, calendar: Calendar) -> Int64 {
    let fromDate = Date(timeIntervalSince1970: Double(from) / 1000.0)
    let nextMonth = calendar.date(byAdding: .month, value: 1, to: fromDate)!
    let range = calendar.range(of: .day, in: .month, for: nextMonth)
    let maxDay = range?.count ?? 28
    let day = min(dayOfMonth, maxDay)
    let timeComps = calendar.dateComponents([.hour, .minute], from: fromDate)
    var comps = calendar.dateComponents([.year, .month], from: nextMonth)
    comps.day = day
    comps.hour = timeComps.hour
    comps.minute = timeComps.minute
    let target = calendar.date(from: comps) ?? nextMonth
    return Int64(target.timeIntervalSince1970 * 1000.0)
}

/// Human label for a weekly day-mask. Mon..Sun, e.g. "Mon, Wed, Fri".
func weeklyMaskLabel(_ mask: Int) -> String {
    let names = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
    let out = (0..<7).filter { (mask >> $0) & 1 == 1 }.map { names[$0] }
    return out.isEmpty ? "No days picked" : out.joined(separator: ", ")
}
