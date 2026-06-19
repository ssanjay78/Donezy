import SwiftUI
import Combine
import UIKit

// ─── Navigation state ─────────────────────────────────────────────────────────

enum NavState: Equatable {
    case home
    case archive
    case settings
    case about
    case detail(hobbyId: Int64)
}

enum SortBy: String, CaseIterable, Identifiable {
    case dueSoon, recentActivity, alphabetical, custom
    var id: String { rawValue }
    var label: String {
        switch self {
        case .dueSoon: return "Due soon"
        case .recentActivity: return "Recent"
        case .alphabetical: return "A–Z"
        case .custom: return "Custom"
        }
    }
}

/// One-shot snackbar event, optionally with an Undo-style action.
struct SnackbarEvent: Identifiable, Equatable {
    let id = UUID()
    let message: String
    var actionLabel: String? = nil
    var onAction: (() -> Void)? = nil

    static func == (lhs: SnackbarEvent, rhs: SnackbarEvent) -> Bool { lhs.id == rhs.id }
}

// ─── ViewModel ──────────────────────────────────────────────────────────────────

@MainActor
final class HobbyViewModel: ObservableObject {

    private let repository: HobbyRepository
    private let themePreference: ThemePreference
    let settingsPreferences: SettingsPreferences

    // Mirror the repository's published lists.
    @Published private(set) var hobbies: [Hobby] = []
    @Published private(set) var archivedHobbies: [Hobby] = []
    @Published private(set) var logDaysByHobby: [Int64: [Int64]] = [:]

    @Published var themeMode: ThemeMode
    @Published private(set) var navState: NavState = .home
    @Published private(set) var detail: HobbyDetail?
    @Published private(set) var filteredLogs: [HobbyLog] = []

    // Settings, surfaced for the Settings screen.
    @Published var soundEnabled: Bool
    @Published var vibrateEnabled: Bool
    @Published var streakRescueEnabled: Bool
    @Published var playbackDurationSeconds: Int
    @Published var customSoundUri: String?
    @Published var onboardingCompleted: Bool

    @Published var currentSnackbar: SnackbarEvent?

    @Published var logSearchQuery: String = ""
    @Published private(set) var logSearchResults: [LogSearchHit] = []

    private var cancellables = Set<AnyCancellable>()

    init(repository: HobbyRepository = .shared,
         themePreference: ThemePreference = ThemePreference(),
         settingsPreferences: SettingsPreferences = SettingsPreferences()) {
        self.repository = repository
        self.themePreference = themePreference
        self.settingsPreferences = settingsPreferences
        self.themeMode = themePreference.mode
        self.soundEnabled = settingsPreferences.soundEnabled
        self.vibrateEnabled = settingsPreferences.vibrateEnabled
        self.streakRescueEnabled = settingsPreferences.streakRescueEnabled
        self.playbackDurationSeconds = settingsPreferences.playbackDurationSeconds
        self.customSoundUri = settingsPreferences.customSoundUri
        self.onboardingCompleted = settingsPreferences.onboardingCompleted

        // Bridge the repository's @Published lists into ours.
        repository.$hobbies.receive(on: DispatchQueue.main).assign(to: &$hobbies)
        repository.$archivedHobbies.receive(on: DispatchQueue.main).assign(to: &$archivedHobbies)
        repository.$logDaysByHobby.receive(on: DispatchQueue.main).assign(to: &$logDaysByHobby)

        repository.refresh()   // already hops onto the DB queue and publishes on main

        // Re-arm reminders off the main thread: this walks every tracker (and their
        // logs for streak rescue), which must not block launch on the main thread.
        let rescueEnabled = settingsPreferences.streakRescueEnabled
        DispatchQueue.global(qos: .utility).async {
            NotificationManager.rearmAll()
            if rescueEnabled { NotificationManager.scheduleStreakRescue() }
        }
    }

    // ── Snackbar ──────────────────────────────────────────────────────────────

    private func emit(_ message: String, action: String? = nil, onAction: (() -> Void)? = nil) {
        currentSnackbar = SnackbarEvent(message: message, actionLabel: action, onAction: onAction)
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    func openDetail(_ id: Int64) {
        guard let d = repository.detail(id) else {
            emit("That tracker is no longer available")
            navState = .home
            return
        }
        detail = d
        filteredLogs = d.logs
        navState = .detail(hobbyId: id)
    }

    func goHome() {
        navState = .home
        detail = nil
        filteredLogs = []
        repository.refresh()
    }

    func openArchive() { navState = .archive }
    func openSettings() { navState = .settings }
    func openAbout() { navState = .about }

    func refreshDetail(_ id: Int64) {
        let d = repository.detail(id)
        detail = d
        filteredLogs = d?.logs ?? []
    }

    // ── Theme ─────────────────────────────────────────────────────────────────

    func setThemeMode(_ mode: ThemeMode) {
        themePreference.setMode(mode)
        themeMode = mode
    }

    // ── Settings ──────────────────────────────────────────────────────────────

    func setSoundEnabled(_ enabled: Bool) {
        settingsPreferences.setSound(enabled); soundEnabled = enabled
    }
    func setVibrateEnabled(_ enabled: Bool) {
        settingsPreferences.setVibrate(enabled); vibrateEnabled = enabled
    }
    func setStreakRescueEnabled(_ enabled: Bool) {
        settingsPreferences.setStreakRescue(enabled); streakRescueEnabled = enabled
        if enabled { NotificationManager.scheduleStreakRescue() }
        else { NotificationManager.cancelStreakRescue() }
    }
    func setPlaybackDurationSeconds(_ seconds: Int) {
        settingsPreferences.setPlaybackDuration(seconds); playbackDurationSeconds = seconds
    }
    func setCustomSoundUri(_ uri: String?) {
        settingsPreferences.setCustomSound(uri); customSoundUri = uri
    }
    func completeOnboarding() {
        settingsPreferences.setOnboardingCompleted(true); onboardingCompleted = true
    }

    // ── Tracker CRUD ──────────────────────────────────────────────────────────

    func addHobby(name: String, category: String, notes: String,
                  reminderAt: Int64?, recurrence: Recurrence, weeklyGoal: Int = 0) {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return }
        let id = repository.addHobby(
            name: trimmed, category: category.isEmpty ? "General" : category,
            notes: notes, nextReminderAt: reminderAt, recurrence: recurrence, weeklyGoal: weeklyGoal)
        if let reminderAt {
            NotificationManager.scheduleReminder(hobbyId: id, hobbyName: trimmed, triggerAt: reminderAt, sound: soundEnabled)
        }
        emit("Tracker \"\(trimmed)\" created ✓")
        WidgetBridge.reload()
    }

    func updateHobby(id: Int64, name: String, category: String, notes: String, weeklyGoal: Int = 0) {
        repository.updateHobby(id: id, name: name, category: category, notes: notes, weeklyGoal: weeklyGoal)
        emit("Tracker updated ✓")
        refreshDetail(id)
    }

    func deleteHobby(id: Int64, name: String) {
        let snapshot = repository.snapshotHobby(id: id)
        NotificationManager.cancelReminder(hobbyId: id)
        repository.deleteHobby(id: id)
        WidgetBridge.reload()
        emit("\"\(name)\" deleted",
             action: snapshot != nil ? "Undo" : nil,
             onAction: snapshot.map { snap in { [weak self] in self?.restoreDeletedHobby(snap) } })
        goHome()
    }

    private func restoreDeletedHobby(_ snapshot: HobbyDetail) {
        repository.restoreHobbySnapshot(snapshot)
        if let ts = snapshot.hobby.nextReminderAt, ts > nowMillis() {
            NotificationManager.scheduleReminder(hobbyId: snapshot.hobby.id, hobbyName: snapshot.hobby.name, triggerAt: ts, sound: soundEnabled)
        }
        WidgetBridge.reload()
        emit("\"\(snapshot.hobby.name)\" restored")
    }

    func togglePin(_ hobby: Hobby) {
        repository.togglePin(id: hobby.id, current: hobby.isPinned)
        emit(hobby.isPinned ? "Unpinned" : "Pinned to top 📌")
        if case .detail = navState { refreshDetail(hobby.id) }
    }

    func archiveHobby(id: Int64, name: String) {
        NotificationManager.cancelReminder(hobbyId: id)
        repository.archiveHobby(id: id)
        WidgetBridge.reload()
        emit("\"\(name)\" archived", action: "Undo", onAction: { [weak self] in self?.restoreHobby(id: id, name: name) })
        if case .detail = navState { goHome() }
    }

    func restoreHobby(id: Int64, name: String) {
        repository.restoreHobby(id: id)
        if let h = repository.hobbyByIdSync(id), let ts = h.nextReminderAt, ts > nowMillis() {
            NotificationManager.scheduleReminder(hobbyId: h.id, hobbyName: h.name, triggerAt: ts, sound: soundEnabled)
        }
        WidgetBridge.reload()
        emit("\"\(name)\" restored")
    }

    // ── Reminders ─────────────────────────────────────────────────────────────

    func setReminderAt(_ hobby: Hobby, reminderAt: Int64, recurrence: Recurrence) {
        repository.updateReminder(hobbyId: hobby.id, nextReminderAt: reminderAt, recurrence: recurrence)
        NotificationManager.scheduleReminder(hobbyId: hobby.id, hobbyName: hobby.name, triggerAt: reminderAt, sound: soundEnabled)
        emit("Reminder set for \(formatDate(reminderAt)) 🔔")
        refreshDetail(hobby.id)
        WidgetBridge.reload()
    }

    func clearReminder(_ hobby: Hobby) {
        NotificationManager.cancelReminder(hobbyId: hobby.id)
        repository.updateReminder(hobbyId: hobby.id, nextReminderAt: nil, recurrence: .none)
        emit("Reminder cleared")
        refreshDetail(hobby.id)
        WidgetBridge.reload()
    }

    // ── Logs ─────────────────────────────────────────────────────────────────

    func addLog(hobbyId: Int64, entry: String, rating: Int?, photoUri: String? = nil) {
        if entry.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && photoUri == nil { return }

        let beforeDetail = repository.detail(hobbyId)
        let beforeSnapshot = beforeDetail.map { AchievementSnapshot.from(hobby: $0.hobby, logs: $0.logs) }

        let logId = repository.addLog(hobbyId: hobbyId, entry: entry, rating: rating, photoUri: photoUri)
        refreshDetail(hobbyId)
        WidgetBridge.reload()

        let afterDetail = repository.detail(hobbyId)
        let newAchievements: [Achievement]
        if let beforeSnapshot, let afterDetail {
            newAchievements = Achievements.newlyEarned(
                before: beforeSnapshot,
                after: AchievementSnapshot.from(hobby: afterDetail.hobby, logs: afterDetail.logs))
        } else { newAchievements = [] }

        if let a = newAchievements.first {
            emit("\(a.emoji) \(a.title) unlocked — \(a.tagline)")
        } else {
            if logId > 0 {
                emit(Affirmations.pick(), action: "Undo", onAction: { [weak self] in
                    self?.repository.deleteLog(id: logId)
                    self?.refreshDetail(hobbyId)
                })
            } else {
                emit(Affirmations.pick())
            }
        }

        if case .detail = navState { goHome() }
    }

    func deleteLog(hobbyId: Int64, logId: Int64) {
        guard let snapshot = repository.fetchLog(id: logId) else { return }
        repository.deleteLog(id: logId)
        refreshDetail(hobbyId)
        emit("Log entry deleted", action: "Undo", onAction: { [weak self] in
            self?.repository.insertLog(snapshot)
            self?.refreshDetail(hobbyId)
        })
    }

    func updateLog(hobbyId: Int64, logId: Int64, entry: String, rating: Int?, photoUri: String?) {
        repository.updateLog(id: logId, entry: entry, rating: rating, photoUri: photoUri)
        refreshDetail(hobbyId)
        emit("Log updated ✓")
    }

    // ── Photos ──────────────────────────────────────────────────────────────────

    /// Persist picked/captured image data into the shared container's photos dir and
    /// return a `file://` URI string (mirrors Android's importPhoto/createCameraTarget).
    func importPhoto(data: Data) -> String? {
        let dir = AppGroup.containerURL.appendingPathComponent("photos", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let target = dir.appendingPathComponent("log_\(nowMillis()).jpg")
        do {
            try data.write(to: target)
            return target.absoluteString
        } catch {
            return nil
        }
    }

    // ── Log search ────────────────────────────────────────────────────────────

    func setLogSearchQuery(_ query: String) {
        logSearchQuery = query
        logSearchResults = query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? [] : repository.searchLogs(query: query)
    }

    func filterLogsByDateRange(hobbyId: Int64, fromMs: Int64, toMs: Int64) {
        filteredLogs = repository.searchLogsByDateRange(hobbyId: hobbyId, fromMs: fromMs, toMs: toMs)
    }

    func clearLogDateFilter(hobbyId: Int64) {
        if let d = repository.detail(hobbyId) {
            filteredLogs = d.logs
        }
    }

    // ── Export ────────────────────────────────────────────────────────────────

    /// Build a CSV for the current detail and return a temp file URL to share.
    func exportLogsCSV() -> URL? {
        guard let d = detail else { return nil }
        var csv = ""
        csv += "Tracker: \(d.hobby.name)\n"
        csv += "Category: \(d.hobby.category)\n"
        csv += "Notes: \(d.hobby.notes)\n\n"
        csv += "Date,Entry,Rating\n"
        for log in d.logs {
            let safeEntry = log.entry.replacingOccurrences(of: "\"", with: "\"\"")
            csv += "\(formatDate(log.createdAt)),\"\(safeEntry)\",\(log.rating.map(String.init) ?? "")\n"
        }
        let safeName = d.hobby.name.replacingOccurrences(of: "[^A-Za-z0-9_-]", with: "_", options: .regularExpression)
        let name = safeName.isEmpty ? "hobby" : safeName
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent("exports", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let url = dir.appendingPathComponent("\(name)_log.csv")
        try? csv.write(to: url, atomically: true, encoding: .utf8)
        return url
    }

    // ── Backup / Restore ──────────────────────────────────────────────────────

    func backupData() -> Data? {
        BackupCodec.encode(hobbies: repository.allHobbiesSync(), logs: repository.allLogsSync())
            .data(using: .utf8)
    }

    func defaultBackupFilename() -> String { "hobbylog-backup-\(nowMillis()).json" }

    func restoreFrom(url: URL) {
        do {
            let needsScope = url.startAccessingSecurityScopedResource()
            defer { if needsScope { url.stopAccessingSecurityScopedResource() } }
            let text = try String(contentsOf: url, encoding: .utf8)
            let (hobbies, logs) = try BackupCodec.decode(text)
            repository.replaceAll(hobbies: hobbies, logs: logs)
            for h in hobbies where !h.isArchived {
                if let ts = h.nextReminderAt, ts > nowMillis() {
                    NotificationManager.scheduleReminder(hobbyId: h.id, hobbyName: h.name, triggerAt: ts, sound: soundEnabled)
                }
            }
            WidgetBridge.reload()
            emit("Restored \(hobbies.count) trackers")
        } catch {
            emit("Restore failed: \(error.localizedDescription)")
        }
    }

    func handleBackupResult(_ result: Result<URL, Error>) {
        switch result {
        case .success: emit("Backup saved ✓")
        case .failure(let e): emit("Backup failed: \(e.localizedDescription)")
        }
    }

    // ── Sort order ─────────────────────────────────────────────────────────────

    func reorderHobby(id: Int64, newOrder: Int) {
        repository.updateSortOrder(id: id, order: newOrder)
    }
}
