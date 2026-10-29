import SwiftUI

struct TabActivityBadge: View {
    let activity: TabActivity

    @Environment(\.appTheme) private var theme

    var body: some View {
        content
            .frame(minWidth: 18, minHeight: 18)
            .accessibilityHidden(true)
    }

    @ViewBuilder
    private var content: some View {
        switch activity.state {
        case .waiting:
            Circle()
                .fill(activity.state.tint(in: theme))
                .frame(width: 9, height: 9)
                .background(Circle().fill(activity.state.tint(in: theme).opacity(0.28)).frame(width: 16, height: 16))
        case .working:
            Image(systemName: "ellipsis")
                .font(.body.weight(.bold))
                .foregroundStyle(activity.state.tint(in: theme))
                .symbolEffect(.variableColor.iterative, options: .repeating)
        case .finished:
            Circle()
                .fill(activity.state.tint(in: theme))
                .frame(width: 8, height: 8)
        case .idle:
            EmptyView()
        }
    }
}

extension TabActivity.State {
    func tint(in theme: AppTheme) -> Color {
        switch self {
        case .waiting: .orange
        case .finished: theme.green
        case .working, .idle: theme.secondaryForeground
        }
    }
}
