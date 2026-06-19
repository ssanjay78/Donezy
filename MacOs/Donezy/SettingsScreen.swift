import SwiftUI
import UniformTypeIdentifiers

struct SettingsScreen: View {
    @ObservedObject var viewModel: HobbyViewModel
    @Environment(\.theme) private var theme
    @State private var showThemeDialog = false
    @State private var showSoundImporter = false
    @State private var showRestoreImporter = false
    @State private var showRestoreConfirm = false
    @State private var restoreURL: URL?

    var body: some View {
        VStack(spacing: 0) {
            ScreenTopBar(title: "Settings") { viewModel.goHome() }
            ScrollView {
                VStack(alignment: .leading, spacing: 8) {
                    sectionHeader("Notifications")

                    toggleRow(icon: "speaker.wave.2.fill", title: "Sound",
                              subtitle: "Play notification sound when a reminder fires",
                              isOn: Binding(get: { viewModel.soundEnabled }, set: { viewModel.setSoundEnabled($0) }))
                    
                    if viewModel.soundEnabled {
                        // Custom Sound Selection Row
                        HStack(spacing: 12) {
                            Image(systemName: "music.note").foregroundColor(theme.onSurfaceVariant)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Notification sound").font(.system(size: 14, weight: .semibold))
                                Text(viewModel.customSoundUri ?? "Default system sound")
                                    .font(.system(size: 12)).foregroundColor(theme.onSurfaceVariant)
                            }
                            Spacer()
                            if viewModel.customSoundUri != nil {
                                Button(action: {
                                    NotificationSoundPlayer.shared.stop()
                                    viewModel.setCustomSoundUri(nil)
                                }) {
                                    Image(systemName: "xmark.circle.fill")
                                        .font(.system(size: 18))
                                        .foregroundColor(theme.onSurfaceVariant.opacity(0.6))
                                }
                                .buttonStyle(.plain)
                                .padding(.trailing, 4)
                            }
                            Button("Browse") {
                                NotificationSoundPlayer.shared.stop()
                                showSoundImporter = true
                            }
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(theme.primary)
                        }
                        .padding(14)
                        .background(theme.surfaceVariant.opacity(0.4), in: RoundedRectangle(cornerRadius: 16))
                        
                        // Playback Duration Slider Row
                        VStack(alignment: .leading, spacing: 8) {
                            HStack(spacing: 12) {
                                Image(systemName: "bell.fill").foregroundColor(theme.onSurfaceVariant)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("Playback duration").font(.system(size: 14, weight: .semibold))
                                    Text("Play notification sound for \(viewModel.playbackDurationSeconds) seconds")
                                        .font(.system(size: 12)).foregroundColor(theme.onSurfaceVariant)
                                }
                            }
                            Slider(value: Binding(get: { Double(viewModel.playbackDurationSeconds) },
                                                 set: { viewModel.setPlaybackDurationSeconds(Int($0)) }),
                                   in: 1...30, step: 1)
                                .tint(theme.primary)
                        }
                        .padding(14)
                        .background(theme.surfaceVariant.opacity(0.4), in: RoundedRectangle(cornerRadius: 16))
                    }

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
        .onDisappear {
            NotificationSoundPlayer.shared.stop()
        }
        .confirmationDialog("Theme", isPresented: $showThemeDialog, titleVisibility: .visible) {
            ForEach(ThemeMode.allCases) { mode in
                Button(mode.label) { viewModel.setThemeMode(mode) }
            }
            Button("Cancel", role: .cancel) {}
        }
        .fileImporter(isPresented: $showSoundImporter, allowedContentTypes: [.audio]) { result in
            if case .success(let url) = result {
                importSound(at: url)
            }
        }
        .fileImporter(isPresented: $showRestoreImporter, allowedContentTypes: [.json, .data]) { result in
            if case .success(let url) = result {
                restoreURL = url
                showRestoreConfirm = true
            }
        }
        .alert("Confirm Restore", isPresented: $showRestoreConfirm) {
            Button("Cancel", role: .cancel) {}
            Button("Restore", role: .destructive) {
                if let url = restoreURL {
                    viewModel.restoreFrom(url: url)
                }
            }
        } message: {
            Text("Restoring from backup will overwrite all current trackers and log entries. Are you sure you want to proceed?")
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

    private func importSound(at url: URL) {
        let gotScope = url.startAccessingSecurityScopedResource()
        defer { if gotScope { url.stopAccessingSecurityScopedResource() } }
        
        do {
            let fileManager = FileManager.default
            let libraryDir = fileManager.urls(for: .libraryDirectory, in: .userDomainMask).first!
            let soundsDir = libraryDir.appendingPathComponent("Sounds", isDirectory: true)
            try fileManager.createDirectory(at: soundsDir, withIntermediateDirectories: true)
            
            let filename = url.lastPathComponent
            let targetURL = soundsDir.appendingPathComponent(filename)
            
            if fileManager.fileExists(atPath: targetURL.path) {
                try fileManager.removeItem(at: targetURL)
            }
            
            try fileManager.copyItem(at: url, to: targetURL)
            viewModel.setCustomSoundUri(filename)
            
            NotificationSoundPlayer.shared.play(soundName: filename, durationSeconds: viewModel.playbackDurationSeconds)
        } catch {
            print("Failed to import sound: \(error.localizedDescription)")
        }
    }
}
