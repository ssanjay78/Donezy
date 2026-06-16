import SwiftUI

struct ArchiveScreen: View {
    @ObservedObject var viewModel: HobbyViewModel
    @Environment(\.theme) private var theme

    var body: some View {
        VStack(spacing: 0) {
            ScreenTopBar(title: "Archived trackers") { viewModel.goHome() }
            if viewModel.archivedHobbies.isEmpty {
                Spacer()
                VStack(spacing: 8) {
                    Text("📦").font(.system(size: 48))
                    Text("No archived trackers").font(.system(size: 16, weight: .semibold))
                    Text("Swipe a tracker on the home screen to archive it.")
                        .font(.system(size: 12)).foregroundColor(theme.onSurfaceVariant)
                        .multilineTextAlignment(.center)
                }
                Spacer()
            } else {
                ScrollView {
                    LazyVStack(spacing: 10) {
                        ForEach(viewModel.archivedHobbies) { hobby in
                            row(hobby)
                        }
                    }
                    .padding(16)
                }
            }
        }
    }

    private func row(_ hobby: Hobby) -> some View {
        let cat = categoryFor(hobby.category)
        return HStack(spacing: 10) {
            CategoryAvatar(category: cat, size: 40)
            VStack(alignment: .leading, spacing: 2) {
                Text(hobby.name).font(.system(size: 14, weight: .semibold)).lineLimit(1)
                Text(cat.label).font(.system(size: 11)).foregroundColor(theme.onSurfaceVariant)
            }
            Spacer()
            Button {
                viewModel.restoreHobby(id: hobby.id, name: hobby.name)
            } label: {
                Label("Restore", systemImage: "arrow.uturn.backward").font(.system(size: 14))
            }
            .buttonStyle(.borderedProminent)
        }
        .padding(12)
        .background(theme.surface, in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(theme.onSurfaceVariant.opacity(0.25)))
    }
}
