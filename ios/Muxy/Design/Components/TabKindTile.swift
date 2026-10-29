import SwiftUI

struct TabKindTile: View {
    let kind: TabTileKind
    let activity: TabActivity
    var size: CGFloat = 36

    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.appTheme) private var theme

    var body: some View {
        RoundedRectangle(cornerRadius: size * 0.25, style: .continuous)
            .fill(fill)
            .frame(width: size, height: size)
            .overlay { icon }
            .overlay(alignment: .topTrailing) {
                if let dot = dotColor {
                    Circle()
                        .fill(dot)
                        .frame(width: size * 0.3, height: size * 0.3)
                        .overlay { Circle().stroke(theme.secondaryGroupedBackground, lineWidth: 2) }
                        .offset(x: size * 0.1, y: -size * 0.1)
                }
            }
            .accessibilityHidden(true)
    }

    @ViewBuilder
    private var icon: some View {
        if let provider {
            Image(provider.iconName)
                .resizable()
                .scaledToFit()
                .frame(width: size * 0.54, height: size * 0.54)
                .foregroundStyle(.white)
        } else {
            Image(systemName: symbol)
                .font(.system(size: size * 0.46, weight: .semibold))
                .foregroundStyle(glyph)
        }
    }

    private var provider: AgentProvider? {
        kind == .agent ? activity.agentProvider : nil
    }

    private var neutralFill: Color {
        colorScheme == .dark ? Color(white: 0.2) : Color(white: 0.12)
    }

    private var symbol: String {
        switch kind {
        case .agent: "sparkle"
        case .shell: "apple.terminal"
        case .git: "arrow.triangle.branch"
        case .other: "square.dashed"
        }
    }

    private var fill: Color {
        switch kind {
        case .agent: provider == nil ? Color(red: 0.886, green: 0.408, blue: 0.235) : neutralFill
        case .shell: neutralFill
        case .git: .indigo
        case .other: .gray
        }
    }

    private var glyph: Color {
        kind == .shell ? .green : .white
    }

    private var dotColor: Color? {
        switch activity.state {
        case .waiting: .orange
        case .finished: .blue
        case .idle, .working: nil
        }
    }
}
