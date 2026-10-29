import SwiftUI

struct TabsHomeNotificationsSheet: View {
    let items: [TabsHomeAttention]
    let onOpen: (TabsHomeAttention) -> Void

    @Environment(\.appTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            content
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(theme.groupedBackground.ignoresSafeArea())
                .screenTitle("Notifications")
                .toolbar {
                    DoneToolbarItem { dismiss() }
                }
        }
        .presentationDetents([.medium, .large])
    }

    @ViewBuilder
    private var content: some View {
        if items.isEmpty {
            ThemedEmptyState(
                title: "No notifications",
                systemImage: "bell.slash",
                message: "Agents waiting for you or finished show up here."
            )
        } else {
            CardList {
                CardSection {
                    ForEach(items) { item in
                        TabsHomeNotificationRow(item: item) { onOpen(item) }
                    }
                }
            }
        }
    }
}

private struct TabsHomeNotificationRow: View {
    let item: TabsHomeAttention
    let onOpen: () -> Void

    @Environment(\.appTheme) private var theme

    var body: some View {
        Button(action: onOpen) {
            HStack(spacing: CardMetrics.iconSpacing) {
                TabKindIcon(kind: item.tab.kind, activity: item.tab.activity, size: 22)
                    .frame(width: CardMetrics.iconWidth)
                VStack(alignment: .leading, spacing: 2) {
                    Text(item.tab.title)
                        .foregroundStyle(theme.foreground)
                        .lineLimit(1)
                    Text(item.tab.activity.statusLabel ?? "")
                        .font(.subheadline)
                        .foregroundStyle(item.tab.activity.state.tint(in: theme))
                        .lineLimit(1)
                    Text(context)
                        .font(.caption)
                        .foregroundStyle(theme.secondaryForeground)
                        .lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                CardRowChevron()
            }
            .padding(.horizontal, CardMetrics.rowPadding)
            .padding(.vertical, 10)
            .contentShape(Rectangle())
        }
        .buttonStyle(.cardRow)
        .accessibilityElement(children: .combine)
        .accessibilityHint("Opens this tab")
    }

    private var context: String {
        let parts = [item.projectName, item.deviceName]
        guard let since = item.tab.activity.since else { return parts.joined(separator: " · ") }
        return (parts + [since.formatted(.relative(presentation: .numeric, unitsStyle: .narrow))]).joined(separator: " · ")
    }
}
