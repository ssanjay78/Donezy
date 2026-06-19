import Foundation
import Combine

/// Theme preference, backed by the shared App Group defaults so the widget could
/// read it too. Mirrors Android's `ThemePreference`.
final class ThemePreference: ObservableObject {
    private let defaults = AppGroup.defaults
    private let key = "theme_mode"

    @Published var mode: ThemeMode

    init() {
        mode = ThemeMode.from(defaults.string(forKey: "theme_mode"))
    }

    func setMode(_ mode: ThemeMode) {
        defaults.set(mode.rawValue, forKey: key)
        self.mode = mode
    }
}

/// Notification / streak-rescue toggles. Mirrors Android's `SettingsPreferences`.
final class SettingsPreferences: ObservableObject {
    private let defaults = AppGroup.defaults

    @Published var soundEnabled: Bool
    @Published var vibrateEnabled: Bool
    @Published var streakRescueEnabled: Bool
    @Published var playbackDurationSeconds: Int
    @Published var customSoundUri: String?
    @Published var onboardingCompleted: Bool

    init() {
        // Default true when unset, matching Android.
        soundEnabled = defaults.object(forKey: Keys.sound) as? Bool ?? true
        vibrateEnabled = defaults.object(forKey: Keys.vibrate) as? Bool ?? true
        streakRescueEnabled = defaults.object(forKey: Keys.streakRescue) as? Bool ?? true
        playbackDurationSeconds = defaults.object(forKey: Keys.playbackDuration) as? Int ?? 3
        customSoundUri = defaults.string(forKey: Keys.customSound)
        onboardingCompleted = defaults.bool(forKey: Keys.onboardingCompleted)
    }

    func setSound(_ enabled: Bool) { soundEnabled = enabled; defaults.set(enabled, forKey: Keys.sound) }
    func setVibrate(_ enabled: Bool) { vibrateEnabled = enabled; defaults.set(enabled, forKey: Keys.vibrate) }
    func setStreakRescue(_ enabled: Bool) { streakRescueEnabled = enabled; defaults.set(enabled, forKey: Keys.streakRescue) }
    func setPlaybackDuration(_ seconds: Int) { playbackDurationSeconds = seconds; defaults.set(seconds, forKey: Keys.playbackDuration) }
    func setCustomSound(_ uri: String?) { customSoundUri = uri; defaults.set(uri, forKey: Keys.customSound) }
    func setOnboardingCompleted(_ completed: Bool) { onboardingCompleted = completed; defaults.set(completed, forKey: Keys.onboardingCompleted) }

    private enum Keys {
        static let sound = "notif_sound"
        static let vibrate = "notif_vibrate"
        static let streakRescue = "streak_rescue"
        static let playbackDuration = "playback_duration_seconds"
        static let customSound = "custom_sound_uri"
        static let onboardingCompleted = "onboarding_completed"
    }
}
