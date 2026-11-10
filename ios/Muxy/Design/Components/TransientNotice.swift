import SwiftUI

extension View {
    func transientNotice(_ message: String?) -> some View {
        modifier(TransientNoticeModifier(message: message))
    }
}

private struct TransientNoticeModifier: ViewModifier {
    private static let duration = Duration.seconds(4)

    let message: String?

    @Environment(\.appTheme) private var theme
    @State private var shown: String?

    func body(content: Content) -> some View {
        content
            .overlay(alignment: .bottom) {
                if let shown {
                    Text(shown)
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(theme.foreground)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .glassEffect(.regular, in: .capsule)
                        .padding(.bottom, 24)
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                        .accessibilityAddTraits(.isStaticText)
                }
            }
            .task(id: message) { await show(message) }
    }

    private func show(_ message: String?) async {
        guard let message else { return }
        withAnimation(.snappy) { shown = message }
        AccessibilityNotification.Announcement(message).post()
        try? await Task.sleep(for: Self.duration)
        withAnimation(.snappy) { shown = nil }
    }
}
