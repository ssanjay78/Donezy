import Foundation
import UserNotifications

/// Handles foreground presentation and the Done/Snooze/Dismiss action buttons on
/// reminder notifications. Mirrors the Android `ReminderReceiver` action handling:
/// "Done" logs from the notification and advances the recurrence; "Snooze" re-arms
/// in one hour; tapping the body deep-links into the matching tracker.
///
/// Threading: the completion-handler delegate APIs are used (not the `async`
/// variants) so we control exactly which thread each step runs on. Database work
/// runs on a background queue; **all UI navigation and WidgetKit reloads are
/// dispatched to the main thread**, because SwiftUI state mutation and
/// `WidgetCenter` calls assert main-thread on iOS 17+ and crash otherwise.
final class NotificationDelegate: NSObject, UNUserNotificationCenterDelegate {

    /// Set by the App so a body-tap can route into the right detail screen.
    var onOpenHobby: ((Int64) -> Void)?

    /// If a notification is tapped before the UI is ready (cold launch), the hobby
    /// id is parked here and flushed once `onOpenHobby` is wired up.
    private(set) var pendingHobbyId: Int64?

    /// Called by the App once `onOpenHobby` is set, to deliver any cold-launch tap.
    @MainActor
    func flushPendingOpen() {
        if let id = pendingHobbyId {
            pendingHobbyId = nil
            onOpenHobby?(id)
        }
    }

    // ── Foreground presentation ───────────────────────────────────────────────

    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        // Show reminders even while foregrounded, like Android's heads-up banner.
        completionHandler([.banner, .sound, .list])
    }

    // ── Action handling ───────────────────────────────────────────────────────

    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                didReceive response: UNNotificationResponse,
                                withCompletionHandler completionHandler: @escaping () -> Void) {
        let info = response.notification.request.content.userInfo
        guard let hobbyId = (info[NotificationManager.hobbyIdKey] as? NSNumber)?.int64Value else {
            completionHandler(); return
        }
        let action = response.actionIdentifier

        // Do the data mutation off the main thread; SQLite serialises internally.
        DispatchQueue.global(qos: .userInitiated).async {
            let repo = HobbyRepository.shared
            var shouldOpen = false

            switch action {
            case NotificationManager.actionDone:
                repo.addLogSync(hobbyId: hobbyId, entry: "Logged from reminder")
                self.advanceRecurrence(hobbyId: hobbyId, repo: repo)

            case NotificationManager.actionSnooze:
                let snoozeAt = nowMillis() + HOUR_MS
                repo.updateReminderSync(hobbyId: hobbyId, nextReminderAt: snoozeAt)
                let name = repo.hobbyByIdSync(hobbyId)?.name ?? "your tracker"
                NotificationManager.scheduleReminder(hobbyId: hobbyId, hobbyName: name, triggerAt: snoozeAt,
                                                     sound: SettingsPreferences().soundEnabled)

            case NotificationManager.actionDismiss, UNNotificationDismissActionIdentifier:
                break  // just clear it

            case UNNotificationDefaultActionIdentifier:
                // Body tap — advance the recurrence (the alarm "fired") and deep-link in.
                self.advanceRecurrence(hobbyId: hobbyId, repo: repo)
                shouldOpen = true

            default:
                break
            }

            // Everything UI-facing must run on the main thread.
            DispatchQueue.main.async {
                WidgetBridge.reload()
                if shouldOpen {
                    if let open = self.onOpenHobby { open(hobbyId) }
                    else { self.pendingHobbyId = hobbyId }   // cold launch — flush later
                }
                completionHandler()
            }
        }
    }

    /// Roll a tracker's reminder to its next occurrence (or clear it for one-shots),
    /// re-scheduling the local notification — the Android re-arm step. Safe off-main.
    private func advanceRecurrence(hobbyId: Int64, repo: HobbyRepository) {
        guard let hobby = repo.hobbyByIdSync(hobbyId) else { return }
        if let next = nextOccurrence(hobby.recurrence, from: nowMillis()) {
            repo.updateReminderSync(hobbyId: hobbyId, nextReminderAt: next)
            NotificationManager.scheduleReminder(hobbyId: hobbyId, hobbyName: hobby.name, triggerAt: next,
                                                 sound: SettingsPreferences().soundEnabled)
        } else {
            repo.updateReminderSync(hobbyId: hobbyId, nextReminderAt: nil)
        }
    }
}
