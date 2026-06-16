import SwiftUI

/// A bottom snackbar with an optional action, matching Material3 `SnackbarHost`.
/// Auto-dismisses after ~4s; tapping the action runs the callback and dismisses.
private struct SnackbarHostModifier: ViewModifier {
    @Binding var event: SnackbarEvent?
    @Environment(\.theme) private var theme
    @State private var dismissTask: Task<Void, Never>?

    func body(content: Content) -> some View {
        ZStack(alignment: .bottom) {
            content
            if let event {
                HStack(spacing: 12) {
                    Text(event.message)
                        .font(.system(size: 14))
                        .foregroundColor(Color(hex: 0xFFFFFF))
                        .fixedSize(horizontal: false, vertical: true)
                    Spacer(minLength: 0)
                    if let label = event.actionLabel {
                        Button(label) {
                            event.onAction?()
                            self.event = nil
                        }
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(theme.primary)
                    }
                }
                .padding(.horizontal, 16).padding(.vertical, 14)
                .background(Color(hex: 0x303030), in: RoundedRectangle(cornerRadius: 8))
                .padding(.horizontal, 16)
                .padding(.bottom, 16)
                .transition(.move(edge: .bottom).combined(with: .opacity))
                .onAppear { scheduleDismiss() }
            }
        }
        .animation(.easeInOut(duration: 0.25), value: event)
        .onChange(of: event) { _, newValue in if newValue != nil { scheduleDismiss() } }
    }

    private func scheduleDismiss() {
        dismissTask?.cancel()
        dismissTask = Task {
            try? await Task.sleep(nanoseconds: 4_000_000_000)
            if !Task.isCancelled { await MainActor.run { event = nil } }
        }
    }
}

extension View {
    func snackbarHost(event: Binding<SnackbarEvent?>) -> some View {
        modifier(SnackbarHostModifier(event: event))
    }
}
