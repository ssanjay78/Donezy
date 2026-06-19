import SwiftUI
import UserNotifications

@main
struct DonezyApp: App {
    @StateObject private var viewModel = HobbyViewModel()
    @Environment(\.scenePhase) private var scenePhase

    private let notificationDelegate = NotificationDelegate()

    init() {
        NotificationManager.registerCategories()
        UNUserNotificationCenter.current().delegate = notificationDelegate
    }

    var body: some Scene {
        WindowGroup {
            RootView(viewModel: viewModel)
                .onAppear {
                    NotificationManager.requestAuthorization()
                    // Route notification body-taps into the matching tracker.
                    notificationDelegate.onOpenHobby = { id in
                        Task { @MainActor in viewModel.openDetail(id) }
                    }
                    // Deliver a tap that occurred before the UI was ready (cold launch).
                    notificationDelegate.flushPendingOpen()
                }
                .onChange(of: scenePhase) { _, phase in
                    if phase == .active {
                        // Equivalent of Android's BootReceiver re-arm: when we come back
                        // to the foreground, advance lapsed recurrences and refresh widget.
                        // Runs off-main — it touches the DB for every tracker and would
                        // otherwise risk a main-thread watchdog stall on large datasets.
                        let rescueEnabled = viewModel.streakRescueEnabled
                        DispatchQueue.global(qos: .utility).async {
                            NotificationManager.rearmAll()
                            if rescueEnabled { NotificationManager.scheduleStreakRescue() }
                            DispatchQueue.main.async { WidgetBridge.reload() }
                        }
                    }
                }
                .onOpenURL { url in
                    // Widget deep link: donezy://hobby/<id> opens that tracker;
                    // donezy://home returns to the dashboard.
                    guard url.scheme == "donezy" else { return }
                    if url.host == "hobby", let id = Int64(url.lastPathComponent) {
                        viewModel.openDetail(id)
                    } else {
                        viewModel.goHome()
                    }
                }
        }
    }
}

/// Top-level navigation + theming, replacing Android's `HobbyLogApp` AnimatedContent.
struct RootView: View {
    @ObservedObject var viewModel: HobbyViewModel

    var body: some View {
        ThemedRoot(themeMode: viewModel.themeMode) {
            ThemedBackground {
                content
                    .transition(.asymmetric(
                        insertion: .move(edge: .trailing).combined(with: .opacity),
                        removal: .opacity))
                    .animation(.easeInOut(duration: 0.32), value: viewModel.navState)
            }
            .snackbarHost(event: $viewModel.currentSnackbar)
        }
    }

    @ViewBuilder private var content: some View {
        if !viewModel.onboardingCompleted {
            OnboardingScreen(onComplete: {
                viewModel.completeOnboarding()
            })
        } else {
            switch viewModel.navState {
            case .home:                 HomeScreen(viewModel: viewModel)
            case .detail:               DetailScreen(viewModel: viewModel)
            case .archive:              ArchiveScreen(viewModel: viewModel)
            case .settings:             SettingsScreen(viewModel: viewModel)
            case .about:                AboutScreen(viewModel: viewModel)
            }
        }
    }
}

/// Paints the themed background behind every screen (Material3 `background` role).
struct ThemedBackground<Content: View>: View {
    @ViewBuilder var content: () -> Content
    @Environment(\.theme) private var theme
    var body: some View {
        ZStack {
            theme.background.ignoresSafeArea()
            content()
        }
    }
}
