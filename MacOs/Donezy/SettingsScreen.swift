import SwiftUI

struct SettingsScreen: View {
    @ObservedObject var viewModel: HobbyViewModel
    @Environment(\.theme) private var theme
    @State private var showThemeDialog = false

    var body: some View {
        VStack(spacing: 0) {
            ScreenTopBar(title: "Settings") { viewModel.goHome() }
            ScrollView {
                VStack(alignment: .leading, spacing: 8) {
                    sectionHeader("Notifications")

                    toggleRow(icon: "speaker.wave.2.fill", title: "Sound",
                              subtitle: "Play the system notification sound when a reminder fires",
                              isOn: Binding(get: { viewModel.soundEnabled }, set: { viewModel.setSoundEnabled($0) }))
                    toggleRow(icon: "iphone.radiowaves.left.and.right", title: "Vibrate",
                              subtitle: "Vibrate the phone when a reminder fires",
                              isOn: Binding(get: { viewModel.vibrateEnabled }, set: { viewModel.setVibrateEnabled($0) }))
                    toggleRow(icon: "flame.fill", title: "Streak rescue",
                              subtitle: "Get a 23:30 nudge if you're at risk of breaking a streak",
                              isOn: Binding(get: { viewModel.streakRescueEnabled }, set: { viewModel.setStreakRescueEnabled($0) }))
                    clickRow(icon: "arrow.up.forward.square", title: "System notification settings",
                             subtitle: "Manage banners, lock-screen visibility, and Focus") {
                        if let url = URL(string: UIApplication.openSettingsURLString) {
                            UIApplication.shared.open(url)
                        }
                    }

                    Divider().padding(.vertical, 8)
                    sectionHeader("Appearance")
                    clickRow(icon: "moon.fill", title: "Theme", subtitle: viewModel.themeMode.label) {
                        showThemeDialog = true
                    }

                    Divider().padding(.vertical, 8)
                    sectionHeader("Tip")
                    Text("Reminders show as a banner over other apps. Tapping the banner opens the matching tracker so you can log it instantly.")
                        .font(.system(size: 12)).foregroundColor(theme.onSurfaceVariant)
                        .padding(.horizontal, 8)
                }
                .padding(.horizontal, 16).padding(.vertical, 8)
            }
        }
        .confirmationDialog("Theme", isPresented: $showThemeDialog, titleVisibility: .visible) {
            ForEach(ThemeMode.allCases) { mode in
                Button(mode.label) { viewModel.setThemeMode(mode) }
            }
            Button("Cancel", role: .cancel) {}
        }
    }

    private func sectionHeader(_ text: String) -> some View {
        Text(text).font(.system(size: 14, weight: .semibold)).foregroundColor(theme.primary)
            .padding(.leading, 8).padding(.top, 8).padding(.bottom, 4)
    }

    private func toggleRow(icon: String, title: String, subtitle: String, isOn: Binding<Bool>) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon).foregroundColor(theme.onSurfaceVariant)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.system(size: 14, weight: .semibold))
                Text(subtitle).font(.system(size: 12)).foregroundColor(theme.onSurfaceVariant)
            }
            Spacer()
            Toggle("", isOn: isOn).labelsHidden().tint(theme.primary)
        }
        .padding(14)
        .background(theme.surfaceVariant.opacity(0.4), in: RoundedRectangle(cornerRadius: 16))
    }

    private func clickRow(icon: String, title: String, subtitle: String, onTap: @escaping () -> Void) -> some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                Image(systemName: icon).foregroundColor(theme.onSurfaceVariant)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.system(size: 14, weight: .semibold)).foregroundColor(theme.onSurface)
                    Text(subtitle).font(.system(size: 12)).foregroundColor(theme.onSurfaceVariant)
                }
                Spacer()
            }
            .padding(14)
            .background(theme.surfaceVariant.opacity(0.4), in: RoundedRectangle(cornerRadius: 16))
        }
        .buttonStyle(.plain)
    }
}

/// Shared back-button top bar for the secondary screens.
struct ScreenTopBar: View {
    let title: String
    let onBack: () -> Void
    @Environment(\.theme) private var theme
    var body: some View {
        HStack(spacing: 8) {
            Button(action: onBack) { Image(systemName: "chevron.left").font(.system(size: 18)) }
            Text(title).font(.system(size: 22, weight: .semibold))
            Spacer()
        }
        .foregroundColor(theme.onSurface)
        .padding(.horizontal, 12).padding(.vertical, 10)
    }
}
