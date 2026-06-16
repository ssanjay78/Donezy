import Foundation

/// Single place defining the App Group used to share the SQLite database, photos,
/// and preferences between the main app and the home-screen widget extension.
///
/// If you change this in the Xcode capability editor, update it here too (and vice
/// versa) — both must match for the widget to see the app's data.
enum AppGroup {
    static let identifier = "group.com.swarnkary.donezy"

    /// Root of the shared container. Falls back to the app's own Documents dir if the
    /// App Group entitlement is missing (e.g. running a quick unsigned debug build),
    /// so the app still works — the widget just won't see the same data in that case.
    static var containerURL: URL {
        if let url = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: identifier) {
            return url
        }
        return FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
    }

    /// Shared defaults bucket so notification/streak-rescue settings written by the app
    /// are visible to the widget process.
    static var defaults: UserDefaults {
        UserDefaults(suiteName: identifier) ?? .standard
    }
}
