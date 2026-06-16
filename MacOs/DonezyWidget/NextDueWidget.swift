import WidgetKit
import SwiftUI
import AppIntents

/// Home-screen widget showing the next-due tracker plus a one-tap "Logged" action.
/// iOS port of the Android `NextDueWidgetProvider` (2×1 next-due widget). Reads the
/// same SQLite database from the shared App Group container that the app writes.

struct NextDueEntry: TimelineEntry {
    let date: Date
    let hobbyId: Int64?
    let title: String
    let subtitle: String
    let showLogButton: Bool
}

struct NextDueProvider: TimelineProvider {
    func placeholder(in context: Context) -> NextDueEntry {
        NextDueEntry(date: Date(), hobbyId: nil, title: "Donezy", subtitle: "Next-due tracker", showLogButton: false)
    }

    func getSnapshot(in context: Context, completion: @escaping (NextDueEntry) -> Void) {
        completion(currentEntry())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<NextDueEntry>) -> Void) {
        let entry = currentEntry()
        // Refresh roughly every 15 minutes so countdown/status text stays current.
        let next = Calendar.current.date(byAdding: .minute, value: 15, to: Date())!
        completion(Timeline(entries: [entry], policy: .after(next)))
    }

    private func currentEntry() -> NextDueEntry {
        guard let due = HobbyRepository.shared.nextDueHobbySync() else {
            return NextDueEntry(date: Date(), hobbyId: nil, title: "Donezy", subtitle: "No trackers yet", showLogButton: false)
        }
        let status = reminderStatusInfo(due.nextReminderAt)
        return NextDueEntry(date: Date(), hobbyId: due.id, title: due.name,
                            subtitle: "\(due.category) · \(status.detail)", showLogButton: true)
    }
}

/// Interactive quick-log action (iOS 17+). Logs from the widget and advances the
/// tracker's recurrence in the shared database, mirroring the Android widget's
/// ACTION_QUICK_LOG. The app re-arms the actual local notification next time it
/// foregrounds (see `NotificationManager.rearmAll`).
struct QuickLogIntent: AppIntent {
    static var title: LocalizedStringResource = "Log progress"
    @Parameter(title: "Hobby ID") var hobbyId: Int

    init() {}
    init(hobbyId: Int64) { self.hobbyId = Int(hobbyId) }

    func perform() async throws -> some IntentResult {
        let repo = HobbyRepository.shared
        let id = Int64(hobbyId)
        repo.addLogSync(hobbyId: id, entry: "Logged from widget")
        if let h = repo.hobbyByIdSync(id), let next = nextOccurrence(h.recurrence, from: nowMillis()) {
            repo.updateReminderSync(hobbyId: id, nextReminderAt: next)
        }
        WidgetCenter.shared.reloadAllTimelines()
        return .result()
    }
}

struct NextDueWidgetEntryView: View {
    var entry: NextDueEntry
    private let accent = Color(hex: 0x1A6B48)

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(entry.title)
                .font(.system(size: 15, weight: .bold))
                .lineLimit(1)
            Text(entry.subtitle)
                .font(.system(size: 11))
                .foregroundColor(.secondary)
                .lineLimit(2)
            Spacer(minLength: 0)
            if entry.showLogButton, let id = entry.hobbyId {
                Button(intent: QuickLogIntent(hobbyId: id)) {
                    Text("✓ Logged").font(.system(size: 12, weight: .semibold))
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(accent)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .widgetURL(entry.hobbyId.map { URL(string: "donezy://hobby/\($0)") } ?? URL(string: "donezy://home"))
        .containerBackground(for: .widget) { Color(.systemBackground) }
    }
}

struct NextDueWidget: Widget {
    let kind = "NextDueWidget"
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: NextDueProvider()) { entry in
            NextDueWidgetEntryView(entry: entry)
        }
        .configurationDisplayName("Next due")
        .description("Shows your next-due tracker with a one-tap log button.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

@main
struct DonezyWidgetBundle: WidgetBundle {
    var body: some Widget {
        NextDueWidget()
    }
}
