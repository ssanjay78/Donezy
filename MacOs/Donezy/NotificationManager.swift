import Foundation
import UserNotifications
import UIKit

/// iOS port of the Android reminder/notification machinery (`Notifications.kt`).
///
/// Android uses AlarmManager + a BroadcastReceiver that re-arms the next occurrence
/// when the alarm fires. iOS has no equivalent background broadcast, so we:
///   • schedule a single local notification per tracker at its next fire time,
///   • re-arm recurrences when the app launches/foregrounds (see `rearmAll`), and
///   • advance the recurrence immediately when the user taps Done/Snooze on the
///     notification (handled in the delegate below).
///
/// Notification action buttons (Done / Snooze / Dismiss) mirror the Android shade
/// actions. "Done" logs from the notification; "Snooze" re-fires in 1 hour (iOS
/// action buttons can't open a custom multi-option picker the way Android can, so a
/// single sensible snooze is used — the in-app reminder card still offers full
/// control).
enum NotificationManager {

    static let categoryId = "DONEZY_REMINDER"
    static let streakCategoryId = "DONEZY_STREAK_RESCUE"
    static let actionDone = "DONEZY_DONE"
    static let actionSnooze = "DONEZY_SNOOZE"
    static let actionDismiss = "DONEZY_DISMISS"
    static let hobbyIdKey = "hobby_id"

    private static func identifier(for hobbyId: Int64) -> String { "reminder_\(hobbyId)" }
    private static let streakIdentifier = "streak_rescue"

    // ── Setup ───────────────────────────────────────────────────────────────────

    static func registerCategories() {
        let center = UNUserNotificationCenter.current()
        let done = UNNotificationAction(identifier: actionDone, title: "Done", options: [])
        let snooze = UNNotificationAction(identifier: actionSnooze, title: "Snooze 1h", options: [])
        let dismiss = UNNotificationAction(identifier: actionDismiss, title: "Dismiss",
                                           options: [.destructive])
        let reminder = UNNotificationCategory(
            identifier: categoryId, actions: [done, snooze, dismiss],
            intentIdentifiers: [], options: [.customDismissAction])
        let streak = UNNotificationCategory(
            identifier: streakCategoryId, actions: [], intentIdentifiers: [], options: [])
        center.setNotificationCategories([reminder, streak])
    }

    static func requestAuthorization() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in }
    }

    // ── Scheduling ────────────────────────────────────────────────────────────────

    /// Schedule (or replace) the local notification for a tracker's next fire time.
    static func scheduleReminder(hobbyId: Int64, hobbyName: String, triggerAt: Int64,
                                 sound: Bool) {
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: [identifier(for: hobbyId)])

        let now = nowMillis()
        let diff = triggerAt - now
        
        let trigger: UNNotificationTrigger?
        if diff <= 1000 {
            trigger = nil
        } else if diff <= 65_000 {
            let delaySeconds = Double(diff) / 1000.0
            trigger = UNTimeIntervalNotificationTrigger(timeInterval: delaySeconds, repeats: false)
        } else {
            let fireDate = Date(timeIntervalSince1970: Double(triggerAt) / 1000.0)
            let comps = Calendar.current.dateComponents(
                [.year, .month, .day, .hour, .minute, .second], from: fireDate)
            trigger = UNCalendarNotificationTrigger(dateMatching: comps, repeats: false)
        }

        let content = UNMutableNotificationContent()
        content.title = hobbyName.isEmpty ? "your tracker" : hobbyName
        content.body = "Tap to log progress."
        content.categoryIdentifier = categoryId
        content.userInfo = [hobbyIdKey: hobbyId]
        
        let settings = SettingsPreferences()
        if sound {
            if let customSound = settings.customSoundUri, !customSound.isEmpty {
                // Play custom sound file located in Library/Sounds (needs filename like custom_sound.wav/mp3)
                content.sound = UNNotificationSound(named: UNNotificationSoundName(rawValue: customSound))
            } else {
                content.sound = .default
            }
        } else {
            content.sound = nil
        }
        
        if let attachment = brandAttachment() { content.attachments = [attachment] }

        let request = UNNotificationRequest(identifier: identifier(for: hobbyId), content: content, trigger: trigger)
        center.add(request)
    }

    /// The small monochrome glyph beside every iOS notification is *always* the app
    /// icon (unlike Android, it can't be overridden). To also carry the full-colour
    /// Donezy artwork in the expanded shade, we attach the icon as an image. The PNG
    /// is copied into a temp file once because `UNNotificationAttachment` needs a file
    /// URL the system can move into its own store.
    private static func brandAttachment() -> UNNotificationAttachment? {
        guard let image = UIImage(named: "NotificationIcon"),
              let data = image.pngData() else { return nil }
        let dir = FileManager.default.temporaryDirectory
        let url = dir.appendingPathComponent("donezy-notif-icon.png")
        if !FileManager.default.fileExists(atPath: url.path) {
            try? data.write(to: url)
        }
        return try? UNNotificationAttachment(identifier: "donezy-icon", url: url, options: nil)
    }

    static func cancelReminder(hobbyId: Int64) {
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: [identifier(for: hobbyId)])
        center.removeDeliveredNotifications(withIdentifiers: [identifier(for: hobbyId)])
    }

    /// Re-arm every active reminder, advancing any that already lapsed — the iOS
    /// equivalent of Android's BootReceiver, run on each app launch/foreground.
    static func rearmAll() {
        let repo = HobbyRepository.shared
        let settings = SettingsPreferences()
        let now = nowMillis()
        for h in repo.activeHobbiesWithReminderSync() {
            guard var triggerAt = h.nextReminderAt else { continue }
            // If the scheduled time passed while the app was closed, roll the
            // recurrence forward (or nudge a one-shot just past now).
            if triggerAt < now {
                if let next = nextOccurrence(h.recurrence, from: now) {
                    triggerAt = next
                    repo.updateReminderSync(hobbyId: h.id, nextReminderAt: next)
                } else {
                    triggerAt = now + 60_000
                    repo.updateReminderSync(hobbyId: h.id, nextReminderAt: triggerAt)
                }
            }
            scheduleReminder(hobbyId: h.id, hobbyName: h.name, triggerAt: triggerAt, sound: settings.soundEnabled)
        }
    }

    // ── Streak rescue ───────────────────────────────────────────────────────────

    /// Schedule the 23:30 streak-rescue check as a repeating daily local notification.
    /// Unlike Android we can't run arbitrary code at 23:30, so we evaluate streak
    /// candidates at schedule time and only post if any streak is at risk *now*. We
    /// also re-evaluate and reschedule on each launch so the message stays current.
    static func scheduleStreakRescue() {
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: [streakIdentifier])

        guard let (sample, streak, total) = streakRescueCandidate() else {
            // Nothing at risk — still arm a daily check so tomorrow can re-evaluate.
            armEmptyDailyCheck()
            return
        }

        var dateComps = DateComponents()
        dateComps.hour = 23
        dateComps.minute = 30
        let trigger = UNCalendarNotificationTrigger(dateMatching: dateComps, repeats: true)

        let content = UNMutableNotificationContent()
        content.title = "🔥 Don't lose your \(streak)-day streak"
        content.body = total > 1
            ? "\(sample.name) and \(total - 1) other tracker\(total - 1 == 1 ? "" : "s") need a log today."
            : "Open Donezy and add a quick log for \(sample.name)."
        content.categoryIdentifier = streakCategoryId
        content.userInfo = [hobbyIdKey: sample.id]
        content.sound = .default
        if let attachment = brandAttachment() { content.attachments = [attachment] }

        center.add(UNNotificationRequest(identifier: streakIdentifier, content: content, trigger: trigger))
    }

    static func cancelStreakRescue() {
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: [streakIdentifier])
    }

    private static func armEmptyDailyCheck() {
        // A silent placeholder isn't useful; simply leave nothing pending. The next
        // app launch will reschedule with a live message if a streak becomes at risk.
    }

    /// Pick the highest at-risk streak (>=2, not logged today), matching Android's logic.
    private static func streakRescueCandidate() -> (hobby: Hobby, streak: Int, total: Int)? {
        let repo = HobbyRepository.shared
        let candidates = repo.streakCandidatesSync(minStreak: 2)
        guard let best = candidates.max(by: { $0.1 < $1.1 }) else { return nil }
        return (best.0, best.1, candidates.count)
    }
}
