import SwiftUI

struct AboutScreen: View {
    @ObservedObject var viewModel: HobbyViewModel
    @Environment(\.theme) private var theme

    private var versionName: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "—"
    }
    private var versionCode: String {
        Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "0"
    }

    var body: some View {
        VStack(spacing: 0) {
            ScreenTopBar(title: "About") { viewModel.goHome() }
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("📓").font(.system(size: 36))
                        Text("Donezy").font(.system(size: 28, weight: .bold)).foregroundColor(theme.onPrimary)
                        Text("Plan it · Do it · Done it.")
                            .font(.system(size: 14)).foregroundColor(theme.onPrimary.opacity(0.85))
                    }
                    .padding(20).frame(maxWidth: .infinity, alignment: .leading)
                    .background(
                        LinearGradient(colors: [theme.primary, theme.tertiary], startPoint: .leading, endPoint: .trailing),
                        in: RoundedRectangle(cornerRadius: 24))

                    aboutCard("What it is",
                        "A local-first tracker for plant care, mechanical keyboards, sneaker collecting, aquarium maintenance, brewing routines, study habits, medication, and similar life routines. Everything stays on your device.")
                    aboutCard("How it works",
                        "Set a reminder for any tracker. When it fires you'll get a banner notification — tap it to jump straight to that tracker and log progress. Streaks, achievements, and weekly goals reward consistency.")
                    aboutCard("Privacy",
                        "No backend. No analytics. No cloud sync. Your trackers, logs, and photos live in this app's private storage. Use Backup to export a JSON file you control.")
                    aboutCard("Privacy policy",
                        "Read the full privacy policy at swarnkary.com/donezy/privacy. Short version: nothing leaves your device unless you explicitly export a backup.")

                    VStack(spacing: 2) {
                        Text("Version \(versionName) · build \(versionCode)")
                            .font(.system(size: 12)).foregroundColor(theme.onSurfaceVariant)
                        Text("© Swarnkary · swarnkary.com")
                            .font(.system(size: 12)).foregroundColor(theme.onSurfaceVariant)
                    }
                    .frame(maxWidth: .infinity).padding(.top, 8)
                }
                .padding(20)
            }
        }
    }

    private func aboutCard(_ title: String, _ body: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title).font(.system(size: 14, weight: .semibold)).foregroundColor(theme.primary)
            Text(body).font(.system(size: 14)).foregroundColor(theme.onSurface)
        }
        .padding(16).frame(maxWidth: .infinity, alignment: .leading)
        .background(theme.surface, in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(theme.onSurfaceVariant.opacity(0.25)))
    }
}
