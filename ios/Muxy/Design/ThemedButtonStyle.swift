import SwiftUI

struct ThemedProminentButtonStyle: ButtonStyle {
    @Environment(\.appTheme) private var theme
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.body.weight(.semibold))
            .foregroundStyle(theme.onAccent)
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
            .background(configuration.role == .destructive ? theme.red : theme.accent, in: Capsule())
            .opacity(ThemedButtonOpacity.value(isEnabled: isEnabled, isPressed: configuration.isPressed))
    }
}

struct ThemedBorderedButtonStyle: ButtonStyle {
    @Environment(\.appTheme) private var theme
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.body.weight(.medium))
            .foregroundStyle(configuration.role == .destructive ? theme.red : theme.accent)
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
            .background(theme.secondaryGroupedBackground, in: Capsule())
            .overlay {
                Capsule()
                    .stroke(theme.separator, lineWidth: 1)
            }
            .opacity(ThemedButtonOpacity.value(isEnabled: isEnabled, isPressed: configuration.isPressed))
    }
}

private nonisolated enum ThemedButtonOpacity {
    static func value(isEnabled: Bool, isPressed: Bool) -> Double {
        guard isEnabled else { return 0.4 }
        return isPressed ? 0.8 : 1
    }
}
