import SwiftUI

/// Color from a 0xRRGGBB hex literal — convenience matching Android's Color(0xFF……).
extension Color {
    init(hex: UInt32, alpha: Double = 1.0) {
        let r = Double((hex >> 16) & 0xFF) / 255.0
        let g = Double((hex >> 8) & 0xFF) / 255.0
        let b = Double(hex & 0xFF) / 255.0
        self.init(.sRGB, red: r, green: g, blue: b, opacity: alpha)
    }
}

// ─── Theme mode ─────────────────────────────────────────────────────────────────

enum ThemeMode: String, CaseIterable, Identifiable {
    case system, light, dark
    var id: String { rawValue }
    var label: String {
        switch self {
        case .system: return "System"
        case .light:  return "Light"
        case .dark:   return "Dark"
        }
    }
    var colorScheme: ColorScheme? {
        switch self {
        case .system: return nil
        case .light:  return .light
        case .dark:   return .dark
        }
    }
    static func from(_ key: String?) -> ThemeMode { ThemeMode(rawValue: key ?? "") ?? .system }
}

// ─── Palette ──────────────────────────────────────────────────────────────────
//
// Mirrors the Material3 color roles used across the Android app. SwiftUI doesn't
// have a built-in MaterialTheme, so `AppTheme` is an EnvironmentObject-style struct
// resolved for the active color scheme and injected via `.appTheme(scheme:)`.

struct AppTheme {
    // Core roles
    let primary: Color
    let onPrimary: Color
    let primaryContainer: Color
    let onPrimaryContainer: Color
    let secondary: Color
    let onSecondary: Color
    let secondaryContainer: Color
    let onSecondaryContainer: Color
    let tertiary: Color
    let onTertiary: Color
    let tertiaryContainer: Color
    let onTertiaryContainer: Color
    let background: Color
    let onBackground: Color
    let surface: Color
    let onSurface: Color
    let surfaceVariant: Color
    let onSurfaceVariant: Color
    let error: Color
    let onError: Color
    let errorContainer: Color
    let onErrorContainer: Color

    static let light = AppTheme(
        primary: Color(hex: 0x1A6B48),
        onPrimary: Color(hex: 0xFFFFFF),
        primaryContainer: Color(hex: 0xB4F1D0),
        onPrimaryContainer: Color(hex: 0x002113),
        secondary: Color(hex: 0x7B4B2A),
        onSecondary: Color(hex: 0xFFFFFF),
        secondaryContainer: Color(hex: 0xFFDBCB),
        onSecondaryContainer: Color(hex: 0x2E1200),
        tertiary: Color(hex: 0x1B61A4),
        onTertiary: Color(hex: 0xFFFFFF),
        tertiaryContainer: Color(hex: 0xD7E3FF),
        onTertiaryContainer: Color(hex: 0x001C3B),
        background: Color(hex: 0xF5F7F2),
        onBackground: Color(hex: 0x191C19),
        surface: Color(hex: 0xFFFFFF),
        onSurface: Color(hex: 0x191C19),
        surfaceVariant: Color(hex: 0xDCE5DB),
        onSurfaceVariant: Color(hex: 0x404943),
        error: Color(hex: 0xB3261E),
        onError: Color(hex: 0xFFFFFF),
        errorContainer: Color(hex: 0xF9DEDC),
        onErrorContainer: Color(hex: 0x410E0B)
    )

    static let dark = AppTheme(
        primary: Color(hex: 0x6ECFA3),
        onPrimary: Color(hex: 0x003823),
        primaryContainer: Color(hex: 0x005235),
        onPrimaryContainer: Color(hex: 0xB4F1D0),
        secondary: Color(hex: 0xFFB68E),
        onSecondary: Color(hex: 0x4A2000),
        secondaryContainer: Color(hex: 0x5F3210),
        onSecondaryContainer: Color(hex: 0xFFDBCB),
        tertiary: Color(hex: 0xB0C8FF),
        onTertiary: Color(hex: 0x002E60),
        tertiaryContainer: Color(hex: 0x00448A),
        onTertiaryContainer: Color(hex: 0xD7E3FF),
        background: Color(hex: 0x0E1512),
        onBackground: Color(hex: 0xDFE4DC),
        surface: Color(hex: 0x161D1A),
        onSurface: Color(hex: 0xDFE4DC),
        surfaceVariant: Color(hex: 0x3A413D),
        onSurfaceVariant: Color(hex: 0xBFC9C2),
        error: Color(hex: 0xF2B8B5),
        onError: Color(hex: 0x601410),
        errorContainer: Color(hex: 0x8C1D18),
        onErrorContainer: Color(hex: 0xF9DEDC)
    )

    static func resolve(_ scheme: ColorScheme) -> AppTheme {
        scheme == .dark ? .dark : .light
    }
}

// Inject the resolved theme into the environment so views read it like Android's
// `MaterialTheme.colorScheme`.
private struct AppThemeKey: EnvironmentKey {
    static let defaultValue: AppTheme = .light
}

extension EnvironmentValues {
    var theme: AppTheme {
        get { self[AppThemeKey.self] }
        set { self[AppThemeKey.self] = newValue }
    }
}

/// Wrap a view tree with the palette resolved for the current color scheme.
struct ThemedRoot<Content: View>: View {
    let themeMode: ThemeMode
    @ViewBuilder var content: () -> Content
    @Environment(\.colorScheme) private var systemScheme

    var body: some View {
        // Decide the effective scheme from the user's theme preference.
        let effective: ColorScheme = themeMode.colorScheme ?? systemScheme
        content()
            .environment(\.theme, AppTheme.resolve(effective))
            .preferredColorScheme(themeMode.colorScheme)
            .tint(AppTheme.resolve(effective).primary)
    }
}
