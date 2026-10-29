import SwiftUI

struct TabKindIcon: View {
    let kind: TabTileKind
    let activity: TabActivity
    var size: CGFloat = 18
    var onTile = false

    @Environment(\.appTheme) private var theme

    var body: some View {
        glyph
            .frame(width: size, height: size)
            .accessibilityHidden(true)
    }

    @ViewBuilder
    private var glyph: some View {
        if let provider {
            Image(provider.iconName)
                .resizable()
                .scaledToFit()
                .foregroundStyle(onTile ? Color.white : theme.foreground)
        } else {
            Image(systemName: symbol)
                .font(.system(size: size * 0.85, weight: .semibold))
                .foregroundStyle(symbolColor)
        }
    }

    var provider: AgentProvider? {
        kind == .agent ? activity.agentProvider : nil
    }

    private var symbol: String {
        switch kind {
        case .agent: "sparkle"
        case .shell: "apple.terminal"
        case .git: "arrow.triangle.branch"
        case .other: "square.dashed"
        }
    }

    private var symbolColor: Color {
        if kind == .shell { return .green }
        if onTile { return .white }
        switch kind {
        case .agent: return Color(red: 0.886, green: 0.408, blue: 0.235)
        case .git: return .indigo
        case .shell, .other: return theme.secondaryForeground
        }
    }
}
