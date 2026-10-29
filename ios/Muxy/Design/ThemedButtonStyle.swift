import SwiftUI

struct ThemedProminentButtonStyle: ButtonStyle {
    @Environment(\.appTheme) private var theme

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.body.weight(.semibold))
            .foregroundStyle(theme.onAccent)
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
            .background(theme.accent, in: Capsule())
            .opacity(configuration.isPressed ? 0.8 : 1)
    }
}

struct ThemedBorderedButtonStyle: ButtonStyle {
    @Environment(\.appTheme) private var theme

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.body.weight(.medium))
            .foregroundStyle(theme.accent)
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
            .background(theme.secondaryGroupedBackground, in: Capsule())
            .overlay {
                Capsule()
                    .stroke(theme.separator, lineWidth: 1)
            }
            .opacity(configuration.isPressed ? 0.8 : 1)
    }
}
