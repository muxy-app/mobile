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
                .fill(.orange)
                .frame(width: 9, height: 9)
                .background(Circle().fill(.orange.opacity(0.28)).frame(width: 16, height: 16))
        case .working:
            Image(systemName: "ellipsis")
                .font(.body.weight(.bold))
                .foregroundStyle(theme.secondaryForeground)
                .symbolEffect(.variableColor.iterative, options: .repeating)
        case .finished:
            Circle()
                .fill(theme.green)
                .frame(width: 8, height: 8)
        case .idle:
            EmptyView()
        }
    }
}
